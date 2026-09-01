/*
 *  This file (UpdateConfig.java) is a part of project XConomy
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
package me.yic.xconomy.info;

import me.yic.xconomy.XConomy;
import me.yic.xconomy.adapter.comp.CConfig;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用配置迁移器。
 *
 * <p>普通的新增配置项只需要修改插件包内的模板文件（config.yml / database.yml），
 * 旧配置会在加载时自动补齐缺失字段，无需再为每个字段编写迁移代码。
 *
 * <p>只有语义变化（字段改名、类型变更、值换算、字段拆分）才需要在
 * {@link #applySemanticMigrations} 中集中登记。
 */
public class UpdateConfig {

    /**
     * 当前配置结构版本。
     * 仅在出现语义迁移时才需要递增；单纯新增字段不需要改动。
     */
    private static final int CURRENT_CONFIG_VERSION = 1;

    private static final String VERSION_PATH = "config-version";

    /**
     * 按内置模板补齐用户配置中缺失的字段。
     *
     * @param config       用户配置
     * @param templateName 插件包内的模板资源名，例如 "config.yml"
     */
    public static void update(CConfig config, String templateName) {
        if (config == null || config.getConfig() == null) {
            return;
        }

        CConfig template = loadTemplate(templateName);
        if (template == null || template.getConfig() == null) {
            XConomy.getInstance().logger(null, 1,
                    "Unable to read bundled template " + templateName + ", configuration was left unchanged");
            return;
        }

        Map<String, Object> defaults = template.getLeafValues();
        if (defaults.isEmpty()) {
            XConomy.getInstance().logger(null, 1,
                    "Bundled template " + templateName + " has no readable entries, configuration was left unchanged");
            return;
        }

        boolean changed = applySemanticMigrations(config, templateName);

        List<String> added = new ArrayList<>();
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String path = entry.getKey();
            if (VERSION_PATH.equals(path) || config.contains(path)) {
                // 已存在的值一律保留，无论是否与默认值不同。
                continue;
            }
            config.createSection(path);
            config.set(path, entry.getValue());
            added.add(path);
        }

        if (!added.isEmpty()) {
            changed = true;
        }

        if (isVersionTracked(templateName)
                && config.getInt(VERSION_PATH) != CURRENT_CONFIG_VERSION) {
            config.createSection(VERSION_PATH);
            config.set(VERSION_PATH, CURRENT_CONFIG_VERSION);
            changed = true;
        }

        if (!changed) {
            return;
        }

        try {
            config.save();
        } catch (Exception e) {
            XConomy.getInstance().logger(null, 1, "Unable to save " + templateName + ": " + e.getMessage());
            return;
        }

        if (!added.isEmpty()) {
            XConomy.getInstance().logger(null, 0,
                    "Added " + added.size() + " missing entries to " + templateName);
        }
    }

    /**
     * 集中登记无法通过默认值推断的语义迁移。
     * 返回 true 表示配置内容已被修改。
     */
    private static boolean applySemanticMigrations(CConfig config, String templateName) {
        boolean changed = false;

        if ("config.yml".equals(templateName)) {
            // 金额缩写开关由支付专用改为全局，沿用管理员原有的开关值。
            if (config.contains("Settings.pay-amount-abbreviations")
                    && !config.contains("Settings.amount-abbreviations")) {
                config.createSection("Settings.amount-abbreviations");
                config.set("Settings.amount-abbreviations",
                        config.getBoolean("Settings.pay-amount-abbreviations"));
                changed = true;
            }

            if (!config.contains("SyncData")) {
                XConomy.getInstance().logger(null, 1, "==================================================");
                XConomy.getInstance().logger(null, 1, "The configuration file is an older version");
                XConomy.getInstance().logger(null, 1, "The plugin may occur configuration problems");
                XConomy.getInstance().logger(null, 1, "It is recommended to regenerate configuration file");
                XConomy.getInstance().logger(null, 1, "==================================================");
            }
        }

        return changed;
    }

    private static boolean isVersionTracked(String templateName) {
        return "config.yml".equals(templateName);
    }

    private static CConfig loadTemplate(String templateName) {
        try {
            URL resource = UpdateConfig.class.getClassLoader().getResource(templateName);
            if (resource == null) {
                return null;
            }
            return new CConfig(resource);
        } catch (Exception e) {
            return null;
        }
    }
}
