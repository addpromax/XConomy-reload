/*
 *  This file (DataBaseConfig.java) is a part of project XConomy
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

import com.zaxxer.hikari.HikariDataSource;
import me.yic.xconomy.AdapterManager;
import me.yic.xconomy.XConomy;
import me.yic.xconomy.XConomyLoad;
import me.yic.xconomy.adapter.comp.CConfig;
import me.yic.xconomy.lang.MessagesManager;

public class DataBaseConfig {

    public static CConfig config;

    public void Initialization() {
        canasync = !XConomyLoad.Config.DISABLE_CACHE && isRemoteDatabase();
        setHikariConnectionPooling();
    }

    public boolean EnableConnectionPool = false;
    public boolean canasync = false;

    public final String ENCODING = config.getString(
            isPostgreSQL() ? "PostgreSQL.property.encoding" : "MySQL.property.encoding");


    public int getStorageType() {
        String storageType = config.getString("Settings.storage-type");
        if (storageType != null && storageType.equalsIgnoreCase("MySQL")) {
            return 2;
        } else if (storageType != null && storageType.equalsIgnoreCase("MariaDB")) {
            return 3;
        } else if (storageType != null && (storageType.equalsIgnoreCase("PostgreSQL")
                || storageType.equalsIgnoreCase("Postgres"))) {
            return 4;
        }
        return 1;
    }

    public void setHikariConnectionPooling() {
        EnableConnectionPool = false;
        if (config.getBoolean("Settings.usepool")) {
            try {
                Class.forName("org.slf4j.Logger");
                if (getStorageType() == 0 || getStorageType() == 1) {
                    EnableConnectionPool = false;
                }else {
                    try {
                        new HikariDataSource();
                        EnableConnectionPool = !AdapterManager.foundvaultpe;
                    } catch (UnsupportedClassVersionError e) {
                        EnableConnectionPool = false;
                        XConomy.getInstance().logger(null, 1, "Connection pool not supported (Java version too old)");
                    }
                }
            } catch (ClassNotFoundException e) {
                XConomy.getInstance().logger("未找到 'org.slf4j.Logger'", 1, null);
                EnableConnectionPool = false;
            }

            if (!EnableConnectionPool){
                XConomy.getInstance().logger("连接池未启用", 0, null);
            }
        }
    }

    public boolean isMySQL() {
        return getStorageType() == 2 || getStorageType() == 3;
    }

    public boolean isPostgreSQL() {
        return getStorageType() == 4;
    }

    public boolean isRemoteDatabase() {
        return getStorageType() >= 2;
    }

    private String remoteConfigPath(String key) {
        return isPostgreSQL() ? "PostgreSQL." + key : "MySQL." + key;
    }

    public String gethost() {
        if (getStorageType() == 1) {
            return config.getString("SQLite.path");
        } else if (isRemoteDatabase()) {
            return config.getString(remoteConfigPath("host"));
        }
        return "";
    }

    public String getuser() {
        return isRemoteDatabase() ? config.getString(remoteConfigPath("user")) : "";
    }

    public String getpass() {
        return isRemoteDatabase() ? config.getString(remoteConfigPath("pass")) : "";
    }

    public String getdatabase() {
        return isRemoteDatabase() ? config.getString(remoteConfigPath("database")) : "";
    }

    public int getport() {
        return isRemoteDatabase() ? config.getInt(remoteConfigPath("port")) : 0;
    }

    public String gettablesuffix() {
        return isRemoteDatabase() ? config.getString(remoteConfigPath("table-suffix")) : "";
    }

    public String geturl() {
        if (!isRemoteDatabase()) {
            return "";
        }
        String scheme;
        if (getStorageType() == 2) {
            scheme = "jdbc:mysql://";
        } else if (getStorageType() == 3) {
            scheme = "jdbc:mariadb://";
        } else {
            scheme = "jdbc:postgresql://";
        }
        String url = scheme + gethost() + ":" + getport() + "/" + getdatabase();
        String timezone = config.getString(remoteConfigPath("property.timezone"));
        Boolean ssl = config.getBoolean(remoteConfigPath("property.usessl"));
        String encoding = config.getString(remoteConfigPath("property.encoding"));
        if (isPostgreSQL()) {
            url += "?ssl=" + ssl;
            if (timezone != null && !timezone.isEmpty()) {
                url += "&options=-c%20TimeZone%3D" + timezone;
            }
        } else {
            url += "?characterEncoding=" + encoding + "&useSSL=" + ssl;
            if (timezone != null && !timezone.isEmpty()) {
                url += "&serverTimezone=" + timezone;
            }
            if (config.getBoolean("MySQL.property.allowPublicKeyRetrieval")) {
                url += "&allowPublicKeyRetrieval=true";
            }
        }
        return url;
    }

    public void loggersysmess(String tag) {
        String mess = MessagesManager.systemMessage(tag);
        switch (getStorageType()) {
            case 1:
                XConomy.getInstance().logger(null, 0, mess.replace("%type%", "SQLite"));
                break;
            case 2:
                XConomy.getInstance().logger(null, 0, mess.replace("%type%", "MySQL"));
                break;
            case 3:
                XConomy.getInstance().logger(null, 0, mess.replace("%type%", "MariaDB"));
                break;
            case 4:
                XConomy.getInstance().logger(null, 0, mess.replace("%type%", "PostgreSQL"));
                break;
        }
    }

}