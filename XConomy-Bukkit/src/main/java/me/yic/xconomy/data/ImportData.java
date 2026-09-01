/*
 *  This file (ImportData.java) is a part of project XConomy
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
package me.yic.xconomy.data;

import me.yic.xconomy.XConomy;
import me.yic.xconomy.XConomyLoad;
import me.yic.xconomy.adapter.comp.CSender;
import me.yic.xconomy.command.core.CommandCore;
import me.yic.xconomy.depend.LoadEconomy;
import me.yic.xconomy.depend.economy.VaultCM;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ImportData {

    /**
     * 导入文件中记录玩家数据的根节点。
     * 使用独立节点存放，避免玩家名或 UUID 与元数据键冲突。
     */
    private static final String ACCOUNTS_SECTION = "accounts";

    private static final String LEGACY_BALANCE_SUFFIX = ".balance";

    public static File importdataf;
    public static FileConfiguration importdata;

    public static boolean hasImportFile = false;

    /** 防止管理员重复执行导入命令导致并发写同一文件。 */
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private final XConomy plugin;

    public ImportData(XConomy main) {
        plugin = main;
    }

    public void onEnable() {
        if (!CreateFile()) {
            plugin.logger("XConomy已成功卸载", 0, null);
            return;
        }

        if (!LoadEconomy.loadcm()) {
            plugin.logger(null, 1, "Conversion mode enable error");
            plugin.logger(null, 1, "Vault and a supported economy plugin are required");
            plugin.logger("XConomy已成功卸载", 0, null);
            return;
        }

        if (!registerImportCommand()) {
            plugin.logger(null, 1, "Conversion mode enable error");
            plugin.logger(null, 1, "Unable to register the '/xconomy' command");
            plugin.logger("XConomy已成功卸载", 0, null);
            return;
        }

        plugin.logger(null, 1, "Convertion mode enable");
        plugin.logger(null, 1, "Run '/xconomy' as an operator to start the import");
        plugin.logger(null, 0, "===== YiC =====");
    }

    public void onDisable() {
        plugin.logger("XConomy已成功卸载", 0, null);
    }

    /**
     * 注册导入模式下使用的 '/xconomy' 命令。
     *
     * <p>Paper 发行包的 plugin.yml 不声明 commands 段，命令统一通过 CommandMap 注册，
     * 因此这里不能依赖 Bukkit#getPluginCommand，否则在 Paper 上会得到 null。
     */
    private boolean registerImportCommand() {
        ImportCommand command = new ImportCommand();
        try {
            Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
            if (commandMap != null) {
                commandMap.register(command.getName(), "xconomy", command);
                return true;
            }
        } catch (Exception ignored) {
            // 回退到 plugin.yml 声明的命令（Bukkit / Spigot 发行包）。
        }

        try {
            org.bukkit.command.PluginCommand pluginCommand = Bukkit.getPluginCommand("xconomy");
            if (pluginCommand != null) {
                pluginCommand.setExecutor((sender, cmd, label, args) -> {
                    handleCommand(sender);
                    return true;
                });
                return true;
            }
        } catch (Exception ignored) {
            // 两条注册路径都不可用时按失败处理。
        }
        return false;
    }

    private void handleCommand(CommandSender sender) {
        if (!sender.isOp() || !XConomyLoad.Config.IMPORTMODE) {
            CommandCore.showVersion(new CSender(sender));
            return;
        }

        if (!RUNNING.compareAndSet(false, true)) {
            sender.sendMessage("An import task is already running");
            return;
        }

        try {
            sender.sendMessage("Data import start");
            ImportResult result = ImportBalance();
            if (result.failed > 0) {
                sender.sendMessage("Data import completed with errors: "
                        + result.imported + " imported, " + result.skipped + " already present, "
                        + result.failed + " failed");
            } else {
                sender.sendMessage("Data import completed: "
                        + result.imported + " imported, " + result.skipped + " already present");
            }
            sender.sendMessage("Saved to XConomy/importdata/data.yml");
            sender.sendMessage("Please disable 'Importdata-mode' and restart the server");
        } finally {
            RUNNING.set(false);
        }
    }

    public boolean CreateFile() {
        if (!XConomyLoad.Config.IMPORTMODE) {
            return true;
        }

        File dataFolder = new File(XConomy.getInstance().getDataFolder(), "importdata");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            XConomy.getInstance().logger("文件夹创建异常", 1, null);
            return false;
        }
        importdataf = new File(dataFolder, "data.yml");
        importdata = YamlConfiguration.loadConfiguration(importdataf);
        if (!importdataf.exists()) {
            try {
                importdata.save(importdataf);
            } catch (IOException e) {
                e.printStackTrace();
                XConomy.getInstance().logger("缓存文件创建异常", 1, null);
                return false;
            }
        }
        return true;
    }

    /**
     * 从当前经济插件读取所有离线玩家余额并写入导入文件。
     * 以 UUID 作为主键，同时保留玩家名用于日志和旧版本兼容查询。
     */
    public ImportResult ImportBalance() {
        ImportResult result = new ImportResult();
        ConfigurationSection accounts = importdata.getConfigurationSection(ACCOUNTS_SECTION);
        if (accounts == null) {
            accounts = importdata.createSection(ACCOUNTS_SECTION);
        }

        for (OfflinePlayer op : Bukkit.getServer().getOfflinePlayers()) {
            UUID uuid = op.getUniqueId();
            if (uuid == null) {
                result.failed++;
                continue;
            }

            String key = uuid.toString();
            if (accounts.contains(key)) {
                result.skipped++;
                continue;
            }

            try {
                BigDecimal balance = VaultCM.getBalance(op);
                if (balance == null) {
                    result.failed++;
                    continue;
                }
                ConfigurationSection account = accounts.createSection(key);
                account.set("balance", balance.toString());
                // 玩家名可能为 null（从未登录的档案），仅在可用时记录。
                if (op.getName() != null) {
                    account.set("player", op.getName());
                }
                result.imported++;
            } catch (Exception e) {
                XConomy.getInstance().logger(null, 1,
                        "Failed to read balance for " + key + ": " + e.getMessage());
                result.failed++;
            }
        }

        save();
        XConomy.getInstance().logger(null, 0, "Import result: " + result.imported + " imported, "
                + result.skipped + " already present, " + result.failed + " failed");
        return result;
    }

    public void save() {
        try {
            importdata.save(importdataf);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public static void isExitsFile() {
        File idataFolder = new File(XConomy.getInstance().getDataFolder(), "importdata");
        if (idataFolder.exists()) {
            importdataf = new File(idataFolder, "data.yml");
            if (importdataf.exists()) {
                hasImportFile = true;
                importdata = YamlConfiguration.loadConfiguration(importdataf);
            }
        }
    }

    /**
     * 查询待导入余额。优先按 UUID 匹配，其次回退到玩家名，
     * 以便旧版本生成的、以玩家名为键的 data.yml 仍然可用。
     */
    @SuppressWarnings("unused")
    public static BigDecimal getBalance(UUID uuid, String player, double inb) {
        if (!hasImportFile || importdata == null) {
            return DataFormat.formatdouble(inb);
        }

        if (uuid != null) {
            String value = importdata.getString(ACCOUNTS_SECTION + "." + uuid + ".balance");
            if (value != null) {
                return parse(value, inb);
            }
        }

        if (player != null) {
            // 旧格式：玩家名直接作为顶层键。玩家名含 '.' 时不能走路径查询。
            ConfigurationSection legacy = importdata.getConfigurationSection(player);
            if (legacy != null) {
                String value = legacy.getString("balance");
                if (value != null) {
                    return parse(value, inb);
                }
            }
            if (!player.contains(".")) {
                String value = importdata.getString(player + LEGACY_BALANCE_SUFFIX);
                if (value != null) {
                    return parse(value, inb);
                }
            }
            // 新格式下按玩家名反查，覆盖调用方拿不到 UUID 的情况。
            ConfigurationSection accounts = importdata.getConfigurationSection(ACCOUNTS_SECTION);
            if (accounts != null) {
                for (String key : accounts.getKeys(false)) {
                    if (player.equalsIgnoreCase(accounts.getString(key + ".player"))) {
                        String value = accounts.getString(key + ".balance");
                        if (value != null) {
                            return parse(value, inb);
                        }
                    }
                }
            }
        }

        return DataFormat.formatdouble(inb);
    }

    @SuppressWarnings("unused")
    public static BigDecimal getBalance(String player, double inb) {
        return getBalance(null, player, inb);
    }

    private static BigDecimal parse(String value, double fallback) {
        try {
            return DataFormat.formatString(value);
        } catch (NumberFormatException e) {
            XConomy.getInstance().logger(null, 1, "Invalid imported balance: " + value);
            return DataFormat.formatdouble(fallback);
        }
    }

    /** 导入结果统计。 */
    public static class ImportResult {
        public int imported;
        public int skipped;
        public int failed;
    }

    /** 导入模式下注册到 CommandMap 的 '/xconomy' 命令。 */
    private class ImportCommand extends Command {
        private ImportCommand() {
            super("xconomy");
            this.description = "XConomy data import";
            this.usageMessage = "/xconomy";
            this.setAliases(Collections.unmodifiableList(Arrays.asList("xc")));
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            handleCommand(sender);
            return true;
        }

        @Override
        public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias,
                                                  @NotNull String[] args) {
            return Collections.emptyList();
        }
    }
}
