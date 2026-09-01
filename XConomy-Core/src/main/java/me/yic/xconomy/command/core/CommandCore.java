/*
 *  This file (CommandCore.java) is a part of project XConomy
 *  Copyright (C) YiC and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package me.yic.xconomy.command.core;

import me.yic.xconomy.AdapterManager;
import me.yic.xconomy.XConomy;
import me.yic.xconomy.XConomyLoad;
import me.yic.xconomy.adapter.comp.CPlayer;
import me.yic.xconomy.adapter.comp.CSender;
import me.yic.xconomy.command.CommandRegistry;
import me.yic.xconomy.data.DataCon;
import me.yic.xconomy.data.DataFormat;
import me.yic.xconomy.data.DataMigration;
import me.yic.xconomy.data.caches.Cache;
import me.yic.xconomy.data.syncdata.PlayerData;
import me.yic.xconomy.data.syncdata.SyncMessage;
import me.yic.xconomy.data.syncdata.SyncPermission;
import me.yic.xconomy.info.MessageConfig;
import me.yic.xconomy.info.SyncType;
import me.yic.xconomy.lang.MessagesManager;
import me.yic.xconomy.utils.UUIDMode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CommandCore {

    protected static String PREFIX = translateColorCodes("prefix");

    public static boolean onCommand(CSender sender, String commandName, String[] args) {
        String resolvedCommand = CommandRegistry.resolve(commandName);
        if (resolvedCommand == null) {
            sendHelpMessage(sender, 1);
            return true;
        }
        switch (resolvedCommand) {
            case "xconomy": {
                if (sender.isOp()) {
                    if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                        // 通过适配器重新加载配置文件和所有配置
                        // 这会调用 LoadConfig()，里面已经包含了 loadlangmess() 和 DataFormat.load()
                        AdapterManager.PLUGIN.reloadPluginConfigs();
                        
                        // 重新加载 PREFIX
                        PREFIX = translateColorCodes("prefix");
                        
                        sendMessages(sender, PREFIX + "<green>配置文件和message.yml重载成功");
                        return true;
                    }
                    if (args.length == 2 && args[0].equalsIgnoreCase("deldata")) {

                        if (check()) {
                            sendMessages(sender, PREFIX + MessagesManager.systemMessage("<red>BC模式开启的情况下,无法在无人的服务器中使用OP命令"));
                            return true;
                        }

                        PlayerData pd = DataCon.getPlayerData(args[1]);
                        if (pd == null) {
                            sendMessages(sender, PREFIX + translateColorCodes(MessageConfig.NO_ACCOUNT));
                            return true;
                        }

                        DataCon.deletePlayerData(pd);

                        sendMessages(sender, PREFIX + translateColorCodes(MessageConfig.DELETE_DATA_ADMIN).replace("%player%", pd.getName()));

                        return true;
                    }
                    if (args.length == 2 && args[0].equalsIgnoreCase("migrate")) {
                        String targetType = args[1];
                        
                        if (!targetType.equalsIgnoreCase("SQLite")
                                && !targetType.equalsIgnoreCase("MySQL")
                                && !targetType.equalsIgnoreCase("MariaDB")
                                && !targetType.equalsIgnoreCase("PostgreSQL")
                                && !targetType.equalsIgnoreCase("Postgres")) {
                            sendUsage(sender, "usage_xconomy_migrate");
                            return true;
                        }
                        if (targetType.equalsIgnoreCase("Postgres")) {
                            targetType = "PostgreSQL";
                        }
                        if (DataMigration.isRunning()) {
                            sendMessages(sender, PREFIX + "<red>已有迁移任务正在运行");
                            return true;
                        }
                        final String migrationTarget = targetType;
                        
                        sendMessages(sender, PREFIX + "<yellow>开始数据迁移，请勿关闭服务器...");
                        
                        // 异步执行迁移
                        AdapterManager.runTaskAsynchronously(() -> {
                            DataMigration.migrate(migrationTarget, new DataMigration.MigrationCallback() {
                                @Override
                                public void onStart(String sourceType, String targetType) {
                                    sendMessages(sender, PREFIX + "<yellow>正在从 " + sourceType + " 迁移到 " + targetType);
                                }
                                
                                @Override
                                public void onProgress(String message) {
                                    sendMessages(sender, PREFIX + "<yellow>" + message);
                                }
                                
                                @Override
                                public void onComplete(int successCount, int totalCount) {
                                    sendMessages(sender, PREFIX + "<green>迁移完成！成功迁移 " + successCount + "/" + totalCount + " 条数据");
                                    sendMessages(sender, PREFIX + "<green>请修改 database.yml 配置文件切换数据库类型，然后重启服务器");
                                }
                                
                                @Override
                                public void onError(String error) {
                                    sendMessages(sender, PREFIX + "<red>迁移失败: " + error);
                                }
                            });
                        });
                        
                        return true;
                    }
                }
                if (args.length >= 1 && args[0].equalsIgnoreCase("track")) {
                    return CommandTrack.onCommand(sender, args);
                }
                if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
                    sendHelpMessage(sender, 1);
                    return true;
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
                    Integer page = parsePositiveInteger(args[1]);
                    if (page == null) {
                        sendUsage(sender, "usage_xconomy_help");
                    } else {
                        sendHelpMessage(sender, page);
                    }
                    return true;
                }
                if (args.length == 0) {
                    showVersion(sender);
                    return true;
                }
                if (args[0].equalsIgnoreCase("help")) {
                    sendUsage(sender, "usage_xconomy_help");
                } else if (sender.isOp() && args[0].equalsIgnoreCase("reload")) {
                    sendUsage(sender, "usage_xconomy_reload");
                } else if (sender.isOp() && args[0].equalsIgnoreCase("deldata")) {
                    sendUsage(sender, "usage_xconomy_deldata");
                } else if (sender.isOp() && args[0].equalsIgnoreCase("migrate")) {
                    sendUsage(sender, "usage_xconomy_migrate");
                } else {
                    sendUsage(sender, "usage_xconomy_help");
                }
                return true;
            }

            case "paypermission": {
                return CommandPayPermission.onCommand(sender, args);
            }

            case "paytoggle": {
                return CommandPayToggle.onCommand(sender, args);
            }

            case "balancetop": {
                return CommandBalancetop.onCommand(sender, commandName, args);
            }

            case "ebalancetop": {
                return CommandBalancetop.onCommand(sender, commandName, args);
            }

            case "pay": {
                return CommandPay.onCommand(sender, commandName, args);
            }

            case "money":
            case "balance":
            case "economy":
            case "eco": {
                return CommandBalance.onCommand(sender, commandName, args);
            }

            default: {
                sendHelpMessage(sender, 1);
                break;
            }

        }

        return true;
    }

    protected static boolean isDouble(String s) {
        return parseAmount(s) != null;
    }

    protected static BigDecimal parseAmount(String input) {
        if (input == null) {
            return null;
        }

        String number = input.trim();
        if (number.isEmpty()) {
            return null;
        }

        BigDecimal multiplier = BigDecimal.ONE;
        char suffix = Character.toLowerCase(number.charAt(number.length() - 1));
        if (Character.isLetter(suffix)) {
            if (!XConomyLoad.Config.AMOUNT_ABBREVIATIONS) {
                return null;
            }
            switch (suffix) {
                case 'k':
                    multiplier = new BigDecimal("1000");
                    break;
                case 'm':
                    multiplier = new BigDecimal("1000000");
                    break;
                case 'b':
                    multiplier = new BigDecimal("1000000000");
                    break;
                default:
                    return null;
            }
            number = number.substring(0, number.length() - 1);
        }

        if (number.isEmpty() || number.length() > 20 || number.matches(".*[a-zA-Z].*")) {
            return null;
        }

        try {
            BigDecimal amount = DataFormat.formatBigDecimal(new BigDecimal(number).multiply(multiplier));
            if (amount.compareTo(BigDecimal.ZERO) > 0 && DataFormat.isMAX(amount)) {
                return null;
            }
            return amount;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean check() {
        return AdapterManager.BanModiftyBalance();
    }

    public static boolean checkMessage(String message) {
        return !MessagesManager.messageFile.getString(message).equals("");
    }

    protected static void sendMessages(CPlayer sender, String message) {
        if (!message.replace(PREFIX, "").equalsIgnoreCase("")) {
            if (message.contains("\\n")) {
                String[] messs = message.split("\\\\n");
                sender.sendMessage(messs);
            } else {
                sender.sendMessage(message);
            }
        }
    }

    protected static void sendMessages(CSender sender, String message) {
        if (!message.replace(PREFIX, "").equalsIgnoreCase("")) {
            if (message.contains("\\n")) {
                String[] messs = message.split("\\\\n");
                sender.sendMessage(messs);
            } else {
                sender.sendMessage(message);
            }
        }
    }

    protected static String translateColorCodes(MessageConfig message) {
        return AdapterManager.translateColorCodes(message);
    }

    protected static String translateColorCodes(String message) {
        return AdapterManager.translateColorCodes(message);
    }

    protected static void sendUsage(CSender sender, String usageKey, String... replacements) {
        String usage = translateColorCodes(usageKey);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            usage = usage.replace(replacements[index], replacements[index + 1]);
        }
        sendMessages(sender, PREFIX + translateColorCodes("invalid_usage")
                .replace("%usage%", usage));
    }

    protected static Integer parsePositiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static void showVersion(CSender sender) {
        sender.sendMessage(PREFIX + "<gold>XConomy <white>(Version: "
                + XConomy.PVersion + ") <gold>|<gray> Author: <white>" + MessagesManager.getAuthor());
        String trs = MessagesManager.getTranslatorS();
        if (trs != null) {
            sender.sendMessage(PREFIX + "<gray>Translator (system): <white>" + trs);
        }
    }

    protected static void sendHelpMessage(CSender sender, Integer num) {
        List<String> helplist = new ArrayList<>();
        helplist.add(translateColorCodes("help1"));
        helplist.add(translateColorCodes("help2"));
        helplist.add(translateColorCodes("help3"));
        helplist.add(translateColorCodes("help4"));
        if (sender.isOp() | sender.hasPermission("xconomy.admin.give")) {
            helplist.add(translateColorCodes("help5"));
            helplist.add(translateColorCodes("help8"));
        }
        if (sender.isOp() | sender.hasPermission("xconomy.admin.take")) {
            helplist.add(translateColorCodes("help6"));
            helplist.add(translateColorCodes("help9"));
        }
        if (sender.isOp() | sender.hasPermission("xconomy.admin.set")) {
            helplist.add(translateColorCodes("help7"));
            helplist.add(translateColorCodes("help21"));
        }
        if (sender.isOp() | sender.hasPermission("xconomy.admin.balancetop")) {
            helplist.add(translateColorCodes("help10"));
        }
        helplist.add(translateColorCodes("help11"));
        if (sender.isOp() | sender.hasPermission("xconomy.admin.paytoggle")) {
            helplist.add(translateColorCodes("help12"));
        }
        if (sender.isOp() | sender.hasPermission("xconomy.admin.permission")) {
            helplist.add(translateColorCodes("help13"));
            helplist.add(translateColorCodes("help14"));
        }
        if (XConomyLoad.isTransactionTrackingEnabled()) {
            helplist.add(translateColorCodes("help15"));
            if (sender.isOp() | sender.hasPermission("xconomy.admin.track.other")) {
                helplist.add(translateColorCodes("help16"));
            }
            if (sender.isOp() | sender.hasPermission("xconomy.admin.track.cleanup")) {
                helplist.add(translateColorCodes("help17"));
            }
        }
        if (sender.isOp()) {
            helplist.add(translateColorCodes("help18"));
            helplist.add(translateColorCodes("help19"));
            helplist.add(translateColorCodes("help20"));
        }
        Integer maxipages;
        if (helplist.size() % XConomyLoad.Config.LINES_PER_PAGE == 0) {
            maxipages = helplist.size() / XConomyLoad.Config.LINES_PER_PAGE;
        } else {
            maxipages = helplist.size() / XConomyLoad.Config.LINES_PER_PAGE + 1;
        }
        if (num < 1) {
            num = 1;
        } else if (num > maxipages) {
            num = maxipages;
        }
        sendMessages(sender, translateColorCodes("help_title_full").replace("%page%", num + "/" + maxipages));
        int indexpage = 0;
        while (indexpage < XConomyLoad.Config.LINES_PER_PAGE) {
            if (helplist.size() > indexpage + (num - 1) * XConomyLoad.Config.LINES_PER_PAGE) {
                sender.sendMessage(helplist.get(indexpage + (num - 1) * XConomyLoad.Config.LINES_PER_PAGE));
            }
            indexpage += 1;
        }
        int previousPage = Math.max(1, num - 1);
        int nextPage = Math.min(maxipages, num + 1);
        sendMessages(sender, translateColorCodes("help_footer")
                .replace("%previous_page%", String.valueOf(previousPage))
                .replace("%next_page%", String.valueOf(nextPage)));
    }

    protected static void sendRankingMessage(CSender sender, String commandName, Integer num) {
        Integer maxipages;
        int listsize = Cache.baltop_papi.size();
        if (listsize % XConomyLoad.Config.LINES_PER_PAGE == 0) {
            maxipages = listsize / XConomyLoad.Config.LINES_PER_PAGE;
        } else {
            maxipages = listsize / XConomyLoad.Config.LINES_PER_PAGE + 1;
        }
        if (num > maxipages) {
            num = maxipages;
        }
        int endindex = num * XConomyLoad.Config.LINES_PER_PAGE;
        if (endindex >= listsize) {
            endindex = listsize;
        }
        List<String> topNames = Cache.baltop_papi.subList(num * XConomyLoad.Config.LINES_PER_PAGE - XConomyLoad.Config.LINES_PER_PAGE, endindex);

        sendMessages(sender, translateColorCodes("top_title").replace("%page%", num + "/" + maxipages));
        sendMessages(sender, translateColorCodes("sum_text")
                .replace("%balance%", DataFormat.shown((Cache.sumbalance))));
        int placement = 0;
        for (String topName : topNames) {
            placement++;
            sendMessages(sender, translateColorCodes("top_text")
                    .replace("%index%", String.valueOf(num * XConomyLoad.Config.LINES_PER_PAGE - XConomyLoad.Config.LINES_PER_PAGE + placement))
                    .replace("%player%", topName)
                    .replace("%balance%", DataFormat.shown((Cache.baltop.get(topName)))));
        }

        int previousPage = Math.max(1, num - 1);
        int nextPage = Math.min(maxipages, num + 1);
        sendMessages(sender, translateColorCodes("top_subtitle")
                .replace("%command%", commandName)
                .replace("%previous_page%", String.valueOf(previousPage))
                .replace("%next_page%", String.valueOf(nextPage)));

    }


    protected static void broadcastSendMessage(boolean ispublic, PlayerData pd, String message) {
        if (!XConomyLoad.getSyncData_Enable()) {
            return;
        }
        if (check() && AdapterManager.PLUGIN.getOnlinePlayersisEmpty()) {
            return;
        }

        SyncMessage sm;
        if (!ispublic) {
            if (XConomyLoad.Config.UUIDMODE.equals(UUIDMode.SEMIONLINE)) {
                sm = new SyncMessage(SyncType.MESSAGE_SEMI, pd.getName(), message);
            } else {
                sm = new SyncMessage(SyncType.MESSAGE, pd.getUniqueId(), message);
            }
        } else {
            sm = new SyncMessage(SyncType.BROADCAST, "", message);
        }

       DataCon.SendMessTask(sm);
    }

    protected static void syncpr(int type, UUID u, Boolean value) {
        if (!XConomyLoad.getSyncData_Enable()) {
            return;
        }

        if (check() && AdapterManager.PLUGIN.getOnlinePlayersisEmpty()) {
            return;
        }

        SyncPermission output = new SyncPermission(u, type, value);

        DataCon.SendMessTask(output);
    }

}
