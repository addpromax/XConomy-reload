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

import java.io.IOException;

public class UpdateConfig {

    public static void update(CConfig config) {
        boolean update = false;
        if (!config.contains("SyncData")) {
            XConomy.getInstance().logger(null, 1, "==================================================");
            XConomy.getInstance().logger(null, 1, "The configuration file is an older version");
            XConomy.getInstance().logger(null, 1, "The plugin may occur configuration problems");
            XConomy.getInstance().logger(null, 1, "It is recommended to regenerate configuration file");
            XConomy.getInstance().logger(null, 1, "==================================================");
        }
        if (!config.contains("Currency.rounding-mode")) {
            config.createSection("Currency.rounding-mode");
            config.set("Currency.rounding-mode", 0);
            update = true;
        }
        if (!config.contains("Thread.future-timeout")) {
            config.createSection("Thread.future-timeout");
            config.set("Thread.future-timeout", 3);
            update = true;
        }
        if (!config.contains("Settings.bstats")) {
            config.createSection("Settings.bstats");
            config.set("Settings.bstats", true);
            update = true;
        }
        if (!config.contains("Transaction-Tracking.enable")) {
            config.createSection("Transaction-Tracking.enable");
            config.set("Transaction-Tracking.enable", true);
            update = true;
        }
        if (!config.contains("Transaction-Tracking.retention-days")) {
            config.createSection("Transaction-Tracking.retention-days");
            config.set("Transaction-Tracking.retention-days", 90);
            update = true;
        }
        if (!config.contains("Transaction-Tracking.auto-cleanup")) {
            config.createSection("Transaction-Tracking.auto-cleanup");
            config.set("Transaction-Tracking.auto-cleanup", true);
            update = true;
        }
        if (!config.contains("Transaction-Tracking.cleanup-time")) {
            config.createSection("Transaction-Tracking.cleanup-time");
            config.set("Transaction-Tracking.cleanup-time", "03:00");
            update = true;
        }
        if (!config.contains("Transaction-Tracking.records-per-page")) {
            config.createSection("Transaction-Tracking.records-per-page");
            config.set("Transaction-Tracking.records-per-page", 10);
            update = true;
        }
        if (update){
            try {
                config.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
