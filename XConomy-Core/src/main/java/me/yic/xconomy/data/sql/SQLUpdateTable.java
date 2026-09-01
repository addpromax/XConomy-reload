/*
 *  This file (SQLUpdateTable.java) is a part of project XConomy
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
package me.yic.xconomy.data.sql;

import me.yic.xconomy.XConomy;
import me.yic.xconomy.XConomyLoad;

import java.sql.*;

public class SQLUpdateTable extends SQL {

    public static void updataTable() {
        Connection connection = database.getConnectionAndCheck();
        try {

            PreparedStatement statementa = connection.prepareStatement("select * from " + tableName + " where hidden = '1'");

            statementa.executeQuery();
            statementa.close();
            database.closeHikariConnection(connection);

        } catch (SQLException e) {
            try {
                XConomy.getInstance().logger("升级数据库表格。。。", 0, tableName);

                String integerType = XConomyLoad.DConfig.isPostgreSQL() ? "INTEGER" : "int(5)";
                PreparedStatement statementb = connection.prepareStatement(
                        "alter table " + tableName + " add column hidden " + integerType + " not null default 0");

                statementb.executeUpdate();
                statementb.close();
                database.closeHikariConnection(connection);

            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }


    public static void updataTable_record() {
        if (!XConomyLoad.DConfig.isRemoteDatabase() || !XConomyLoad.Config.TRANSACTION_RECORD) {
            return;
        }
        Connection connection = database.getConnectionAndCheck();
        if (connection == null) {
            return;
        }
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            boolean hasLegacyDate = hasColumn(metaData, tableRecordName, "date");
            boolean hasDateTime = hasColumn(metaData, tableRecordName, "datetime");
            if (hasLegacyDate && !hasDateTime) {
                XConomy.getInstance().logger("升级数据库表格。。。", 0, tableRecordName);
                String sql = XConomyLoad.DConfig.isPostgreSQL()
                        ? "alter table " + tableRecordName + " rename column date to datetime"
                        : "alter table " + tableRecordName + " change column date datetime DATETIME NOT NULL";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            database.closeHikariConnection(connection);
        }
    }

    private static boolean hasColumn(DatabaseMetaData metaData, String table, String column) throws SQLException {
        String[] tableNames = {table, table.toLowerCase(), table.toUpperCase()};
        String[] columnNames = {column, column.toLowerCase(), column.toUpperCase()};
        for (String tableNameCandidate : tableNames) {
            for (String columnNameCandidate : columnNames) {
                try (ResultSet resultSet = metaData.getColumns(null, null, tableNameCandidate, columnNameCandidate)) {
                    if (resultSet.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}