/*
 *  This file (TabList.java) is a part of project XConomy
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
package me.yic.xconomy.listeners;

import me.yic.xconomy.XConomyLoad;
import me.yic.xconomy.command.CommandRegistry;
import me.yic.xconomy.data.DataCon;
import me.yic.xconomy.data.syncdata.PlayerData;
import me.yic.xconomy.data.tracking.TrackPageCache;
import me.yic.xconomy.lang.MessagesManager;
import me.yic.xconomy.utils.TabListCon;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TabList implements TabCompleter {

    /**
     * 获取在线玩家名称列表
     */
    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    /**
     * 获取玩家列表用于Tab补全
     * 优先使用缓存列表，如果为空则使用在线玩家列表
     */
    private List<String> getPlayerListForTab() {
        List<String> cached = TabListCon.get_Tab_PlayerList();
        if (cached == null || cached.isEmpty()) {
            return getOnlinePlayerNames();
        }
        return cached;
    }


    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String commandName, @NotNull String[] args) {

        final List<String> completions = new ArrayList<>();
        String resolvedCommand = CommandRegistry.resolve(commandName);
        if (resolvedCommand == null) {
            return completions;
        }
        switch (resolvedCommand) {
            case "xconomy":
            {
                if (args.length == 1) {
                    List<String> COMMANDS_xc = new ArrayList<>();
                    COMMANDS_xc.add("help");
                    if (commandSender.isOp()) {
                        COMMANDS_xc.add("reload");
                        COMMANDS_xc.add("deldata");
                        COMMANDS_xc.add("migrate");
                    }
                    if (XConomyLoad.isTransactionTrackingEnabled()) {
                        COMMANDS_xc.add("track");
                    }
                    StringUtil.copyPartialMatches(args[0], COMMANDS_xc, completions);

                } else if (args.length == 2 && !args[0].equalsIgnoreCase("track")) {
                    if (args[0].equalsIgnoreCase("migrate") && commandSender.isOp()) {
                        // /xconomy migrate <SQLite|MySQL|MariaDB|PostgreSQL>
                        List<String> DB_TYPES = new ArrayList<>();
                        DB_TYPES.add("SQLite");
                        DB_TYPES.add("MySQL");
                        DB_TYPES.add("MariaDB");
                        DB_TYPES.add("PostgreSQL");
                        StringUtil.copyPartialMatches(args[1], DB_TYPES, completions);
                    } else if (args[0].equalsIgnoreCase("deldata") && commandSender.isOp()) {
                        // /xconomy deldata <玩家>
                        StringUtil.copyPartialMatches(args[1], getPlayerListForTab(), completions);
                    }
                } else if (args.length >= 2 && args[0].equalsIgnoreCase("track")
                        && XConomyLoad.isTransactionTrackingEnabled()) {

                    if (args.length == 2) {
                        // /xconomy track <?>
                        List<String> TRACK_SUB = new ArrayList<>();
                        TRACK_SUB.add("income");
                        TRACK_SUB.add("expense");
                        if (commandSender.isOp() || commandSender.hasPermission("xconomy.admin.track.other")) {
                            TRACK_SUB.addAll(getPlayerListForTab());
                        }
                        if (commandSender.isOp() || commandSender.hasPermission("xconomy.admin.track.cleanup")) {
                            TRACK_SUB.add("cleanup");
                        }
                        if (commandSender instanceof Player || commandSender.isOp()
                                || commandSender.hasPermission("xconomy.admin.track.other")) {
                            TRACK_SUB.add("flow");
                        }
                        StringUtil.copyPartialMatches(args[1], TRACK_SUB, completions);

                    } else if (args.length == 3) {
                        String sub = args[1].toLowerCase();
                        if (sub.equals("income") || sub.equals("expense")) {
                            // /xconomy track income/expense <页码>
                            if (commandSender instanceof Player) {
                                UUID selfUUID = ((Player) commandSender).getUniqueId();
                                int maxPage = TrackPageCache.getMaxPage(selfUUID, sub);
                                List<String> pages = buildPageList(maxPage);
                                StringUtil.copyPartialMatches(args[2], pages, completions);
                            }
                        } else if (sub.equals("cleanup")) {
                            // /xconomy track cleanup <天数>，提示常用值
                            StringUtil.copyPartialMatches(args[2],
                                    java.util.Arrays.asList("7", "30", "60", "90", "180", "365"), completions);
                        } else if (sub.equals("flow")) {
                            String hint = MessagesManager.messageFile.getString("tab_transaction_id");
                            if (args[2].isEmpty()) {
                                completions.add(hint != null ? hint : "<transaction_id>");
                            }
                        } else {
                            // /xconomy track <玩家名> <income|expense>
                            if (commandSender.isOp() || commandSender.hasPermission("xconomy.admin.track.other")) {
                                List<String> TRACK_TYPES = new ArrayList<>();
                                TRACK_TYPES.add("income");
                                TRACK_TYPES.add("expense");
                                StringUtil.copyPartialMatches(args[2], TRACK_TYPES, completions);
                            }
                        }

                    } else if (args.length == 4) {
                        // /xconomy track <玩家名> income/expense <页码>
                        String trackType = args[2].toLowerCase();
                        if ((trackType.equals("income") || trackType.equals("expense"))
                                && (commandSender.isOp() || commandSender.hasPermission("xconomy.admin.track.other"))) {
                            PlayerData pd = DataCon.getPlayerData(args[1]);
                            if (pd != null) {
                                int maxPage = TrackPageCache.getMaxPage(pd.getUniqueId(), trackType);
                                List<String> pages = buildPageList(maxPage);
                                StringUtil.copyPartialMatches(args[3], pages, completions);
                            }
                        }
                    }
                }
                Collections.sort(completions);
                break;
            }
            case "paypermission":
            case "payperm": {
                if (commandSender.isOp() || commandSender.hasPermission("xconomy.admin.permission")) {
                    List<String> COMMANDS_payperm = new ArrayList<>();
                    if (args.length == 1) {
                        COMMANDS_payperm.add("set");
                        COMMANDS_payperm.add("remove");
                        StringUtil.copyPartialMatches(args[0], COMMANDS_payperm, completions);
                    } else if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove")) {
                        if (args.length == 2) {
                            if (args[0].equalsIgnoreCase("set")) {
                                completions.add("*");
                            }
                            StringUtil.copyPartialMatches(args[1], getPlayerListForTab(), completions);
                        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                            COMMANDS_payperm.add("true");
                            COMMANDS_payperm.add("false");
                            StringUtil.copyPartialMatches(args[2], COMMANDS_payperm, completions);
                        }
                    }
                }
                Collections.sort(completions);
                break;
            }
            case "paytoggle":{
                if (commandSender.isOp() || commandSender.hasPermission("xconomy.admin.paytoggle")) {
                    if (args.length == 1) {
                        StringUtil.copyPartialMatches(args[0], getPlayerListForTab(), completions);
                    }
                }
                Collections.sort(completions);
                break;
            }
            case "baltop":
            case "balancetop": {
                if (args.length == 1) {
                    List<String> COMMANDS_baltop = new ArrayList<>();
                    if (commandSender.isOp() || commandSender.hasPermission("xconomy.admin.balancetop")) {
                        COMMANDS_baltop.add("hide");
                        COMMANDS_baltop.add("display");
                    }
                    StringUtil.copyPartialMatches(args[0], COMMANDS_baltop, completions);
                } else if (args.length == 2) {
                    StringUtil.copyPartialMatches(args[1], getPlayerListForTab(), completions);
                }
                Collections.sort(completions);
                break;
            }
            case "ebalancetop": {
                if (args.length == 1) {
                    List<String> commands = new ArrayList<>();
                    if (commandSender.isOp() || commandSender.hasPermission("xconomy.admin.balancetop")) {
                        commands.add("hide");
                        commands.add("display");
                    }
                    StringUtil.copyPartialMatches(args[0], commands, completions);
                } else if (args.length == 2 && !args[0].equalsIgnoreCase("track")) {
                    StringUtil.copyPartialMatches(args[1], getPlayerListForTab(), completions);
                }
                Collections.sort(completions);
                break;
            }
            case "pay": {
                if (args.length == 1) {
                    if (commandSender.isOp() || commandSender.hasPermission("xconomy.user.pay")) {
                        StringUtil.copyPartialMatches(args[0], getPlayerListForTab(), completions);
                    }
                }
                Collections.sort(completions);
                break;
            }
            case "money":
            case "bal":
            case "balance":
            case "economy":
            case "eco": {
                boolean canGive = commandSender.isOp() || commandSender.hasPermission("xconomy.admin.give");
                boolean canTake = commandSender.isOp() || commandSender.hasPermission("xconomy.admin.take");
                boolean canSet  = commandSender.isOp() || commandSender.hasPermission("xconomy.admin.set");
                boolean canAdmin = canGive || canTake || canSet;
                boolean canViewOther = commandSender.isOp() || commandSender.hasPermission("xconomy.user.balance.other");

                if (args.length == 1) {
                    // /money <?>
                    List<String> candidates = new ArrayList<>();
                    if (canViewOther) candidates.add("look");
                    if (canGive) candidates.add("give");
                    if (canTake) candidates.add("take");
                    if (canSet)  candidates.add("set");
                    StringUtil.copyPartialMatches(args[0], candidates, completions);

                } else if (args.length == 2) {
                    // /money <sub> <?>
                    String sub = args[0].toLowerCase();
                    if (sub.equals("look") && canViewOther) {
                        StringUtil.copyPartialMatches(args[1], getPlayerListForTab(), completions);
                    } else if (sub.equals("give") || sub.equals("take") || sub.equals("set")) {
                        if (canAdmin) {
                            // 玩家名 + 通配符 *
                            List<String> players = new ArrayList<>(getPlayerListForTab());
                            players.add("*");
                            StringUtil.copyPartialMatches(args[1], players, completions);
                        }
                    }

                } else if (args.length == 3) {
                    // /money <sub> <玩家/*> <?>
                    String sub = args[0].toLowerCase();
                    if (sub.equals("give") || sub.equals("take") || sub.equals("set")) {
                        if (canAdmin) {
                            if (args[1].equals("*")) {
                                // /money give * <all/online>
                                StringUtil.copyPartialMatches(args[2],
                                        java.util.Arrays.asList("all", "online"), completions);
                            } else {
                                // /money give <玩家> <金额>，提示占位
                                if (args[2].isEmpty()) {
                                    String hint = MessagesManager.messageFile.getString("tab_amount");
                                    completions.add(hint != null ? hint : "<amount>");
                                }
                            }
                        }
                    }

                } else if (args.length == 4) {
                    // /money <sub> <玩家> <金额> <?>  或  /money <sub> * <all/online> <金额>
                    String sub = args[0].toLowerCase();
                    if (sub.equals("give") || sub.equals("take") || sub.equals("set")) {
                        if (canAdmin) {
                            if (args[1].equals("*")) {
                                // /money give * all/online <金额>
                                if (args[3].isEmpty()) {
                                    String hint = MessagesManager.messageFile.getString("tab_amount");
                                    completions.add(hint != null ? hint : "<amount>");
                                }
                            } else {
                                // /money give <玩家> <金额> <flag|-r>
                                // 开始 flag 补全（首个 flag 位置）
                                List<String> FLAGS = new ArrayList<>();
                                FLAGS.add("-s");
                                FLAGS.add("-q");
                                FLAGS.add("-r");
                                StringUtil.copyPartialMatches(args[3], FLAGS, completions);
                            }
                        }
                    }

                } else if (args.length == 5) {
                    // /money give * all/online <金额> <原因>
                    // 或 /money give <玩家> <金额> <flag> <?>
                    String sub = args[0].toLowerCase();
                    if ((sub.equals("give") || sub.equals("take") || sub.equals("set")) && canAdmin) {
                        if (args[1].equals("*")) {
                            // /money give * all/online <金额> <原因> — 原因自由输入，不补全
                        } else {
                            // flag 区继续补全
                            completeFlagsAt(args, 3, completions);
                        }
                    }

                } else if (args.length >= 6) {
                    String sub = args[0].toLowerCase();
                    if ((sub.equals("give") || sub.equals("take") || sub.equals("set"))
                            && canAdmin && !args[1].equals("*")) {
                        completeFlagsAt(args, 3, completions);
                    }
                }

                Collections.sort(completions);
                break;
            }
        }
        return completions;
    }

    /** 生成 1..maxPage 的页码字符串列表 */
    private static List<String> buildPageList(int maxPage) {
        List<String> pages = new ArrayList<>();
        for (int i = 1; i <= maxPage; i++) {
            pages.add(String.valueOf(i));
        }
        return pages;
    }

    /**
     * 在 flag 区（从 flagStart 到末尾）进行 flag 补全。
     * 识别 -r 的值区间（遇到已知 flag 才终止），避免把原因词当 flag 处理。
     */
    private static void completeFlagsAt(String[] args, int flagStart, List<String> completions) {
        java.util.Set<String> KNOWN_FLAGS = new java.util.HashSet<>(
                java.util.Arrays.asList("-s", "-q", "-r"));
        java.util.Set<String> usedFlags = new java.util.HashSet<>();
        boolean inReason = false;

        // 扫描已确定的 flag 区（不含正在输入的最后一个词）
        for (int fi = flagStart; fi < args.length - 1; fi++) {
            String tok = args[fi].toLowerCase();
            if (inReason) {
                if (KNOWN_FLAGS.contains(tok)) {
                    inReason = false;
                    usedFlags.add(tok);
                    if (tok.equals("-r")) inReason = true;
                }
            } else if (KNOWN_FLAGS.contains(tok)) {
                usedFlags.add(tok);
                if (tok.equals("-r")) inReason = true;
            }
        }

        if (!inReason) {
            String current = args[args.length - 1];
            List<String> FLAGS = new ArrayList<>();
            if (!usedFlags.contains("-s") && !usedFlags.contains("-q")) FLAGS.add("-s");
            if (!usedFlags.contains("-q") && !usedFlags.contains("-s")) FLAGS.add("-q");
            if (!usedFlags.contains("-r")) FLAGS.add("-r");
            StringUtil.copyPartialMatches(current, FLAGS, completions);
        }
        // inReason == true：正在输入 -r 的原因内容，不提示 flag
    }
}
