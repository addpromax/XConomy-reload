/*
 *  This file (DataMigration.java) is a part of project XConomy
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
import me.yic.xconomy.adapter.comp.CConfig;
import me.yic.xconomy.data.sql.SQL;

import java.io.File;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class DataMigration {

    private static final int BATCH_SIZE = 200;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    public static boolean isRunning() {
        return RUNNING.get();
    }

    private enum DatabaseType {
        SQLITE("SQLite"),
        MYSQL("MySQL"),
        MARIADB("MariaDB"),
        POSTGRESQL("PostgreSQL");

        private final String displayName;

        DatabaseType(String displayName) {
            this.displayName = displayName;
        }
    }

    private static final class TargetConfig {
        private final DatabaseType type;
        private final String host;
        private final int port;
        private final String user;
        private final String pass;
        private final String database;
        private final String sqlitePath;
        private final String tableSuffix;
        private final boolean useSsl;
        private final String encoding;
        private final String timezone;
        private final boolean allowPublicKeyRetrieval;

        private TargetConfig(DatabaseType type, String host, int port, String user, String pass,
                             String database, String sqlitePath, String tableSuffix, boolean useSsl,
                             String encoding, String timezone, boolean allowPublicKeyRetrieval) {
            this.type = type;
            this.host = host;
            this.port = port;
            this.user = user;
            this.pass = pass;
            this.database = database;
            this.sqlitePath = sqlitePath;
            this.tableSuffix = tableSuffix;
            this.useSsl = useSsl;
            this.encoding = encoding;
            this.timezone = timezone;
            this.allowPublicKeyRetrieval = allowPublicKeyRetrieval;
        }
    }

    private static final class TableSpec {
        private final String label;
        private final String baseName;
        private final String sourceName;
        private final String keyColumn;
        private final List<String> requiredColumns;
        private final List<String> optionalColumns;

        private TableSpec(String label, String baseName, String sourceName, String keyColumn,
                          List<String> requiredColumns, List<String> optionalColumns) {
            this.label = label;
            this.baseName = baseName;
            this.sourceName = sourceName;
            this.keyColumn = keyColumn;
            this.requiredColumns = requiredColumns;
            this.optionalColumns = optionalColumns;
        }
    }

    private static final class TablePlan {
        private final TableSpec spec;
        private final String targetName;
        private final boolean sourceExists;
        private final List<String> columns;
        private final boolean synthesizeHidden;
        private final int rowCount;

        private TablePlan(TableSpec spec, String targetName, boolean sourceExists, List<String> columns,
                          boolean synthesizeHidden, int rowCount) {
            this.spec = spec;
            this.targetName = targetName;
            this.sourceExists = sourceExists;
            this.columns = columns;
            this.synthesizeHidden = synthesizeHidden;
            this.rowCount = rowCount;
        }
    }

    /**
     * 从当前数据库迁移到指定数据库类型。
     *
     * @param targetType SQLite、MySQL、MariaDB、PostgreSQL 或 Postgres
     * @param callback 迁移进度回调
     * @return 迁移是否成功
     */
    public static boolean migrate(String targetType, MigrationCallback callback) {
        int currentTypeNumber = XConomyLoad.DConfig.getStorageType();
        DatabaseType sourceType = fromStorageType(currentTypeNumber);
        DatabaseType normalizedTarget = parseTargetType(targetType);
        callback.onStart(sourceType == null ? "Unknown" : sourceType.displayName,
                normalizedTarget == null ? String.valueOf(targetType) : normalizedTarget.displayName);

        if (sourceType == null) {
            callback.onError("无法识别当前数据库类型");
            return false;
        }
        if (normalizedTarget == null) {
            callback.onError("不支持的目标数据库类型: " + targetType);
            return false;
        }
        if (isSameType(sourceType, normalizedTarget)) {
            callback.onError("源数据库和目标数据库类型相同，无法进行迁移");
            return false;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            callback.onError("已有迁移任务正在运行");
            return false;
        }

        Connection sourceConnection = null;
        boolean sourceAutoCommit = true;
        try {
            TargetConfig targetConfig = readTargetConfig(normalizedTarget);
            callback.onProgress("已读取目标 " + normalizedTarget.displayName + " 配置");

            sourceConnection = SQL.database.getConnectionAndCheck();
            if (sourceConnection == null) {
                throw new SQLException("无法连接到源数据库");
            }
            sourceAutoCommit = sourceConnection.getAutoCommit();
            if (sourceType != DatabaseType.SQLITE) {
                sourceConnection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            }
            sourceConnection.setAutoCommit(false);

            int totalCount;
            int successCount = 0;
            try (Connection targetConnection = connectTargetDatabase(targetConfig)) {
                targetConnection.setAutoCommit(false);
                callback.onProgress("已连接到目标数据库");

                try {
                    List<TablePlan> plans = buildMigrationPlans(sourceConnection, targetConfig);
                    totalCount = 0;
                    for (TablePlan plan : plans) {
                        totalCount += plan.rowCount;
                    }
                    callback.onProgress("源数据库共发现 " + totalCount + " 条可迁移数据");

                    createTargetTables(targetConnection, targetConfig, plans);
                    ensureTargetTablesEmpty(targetConnection, plans);
                    callback.onProgress("已创建适用于 " + normalizedTarget.displayName + " 的目标数据表");

                    for (TablePlan plan : plans) {
                        if (!plan.sourceExists) {
                            callback.onProgress("源表 " + plan.spec.sourceName + " 不存在，已跳过");
                            continue;
                        }
                        int migrated = migrateTable(sourceConnection, targetConnection, targetConfig.type,
                                plan, successCount, totalCount, callback);
                        successCount += migrated;
                        callback.onProgress(plan.spec.label + "迁移完成: " + migrated + "/" + plan.rowCount);
                    }

                    if (successCount != totalCount) {
                        throw new SQLException("迁移数据数量不一致: " + successCount + "/" + totalCount);
                    }
                    if (targetConfig.type == DatabaseType.POSTGRESQL) {
                        synchronizePostgreSqlRecordSequence(targetConnection, plans);
                    }
                    targetConnection.commit();
                } catch (Exception e) {
                    rollbackQuietly(targetConnection);
                    throw e;
                }
            }

            if (successCount == totalCount) {
                callback.onComplete(successCount, totalCount);
                return true;
            }
            callback.onError("迁移数据数量不一致，已回滚目标数据库: " + successCount + "/" + totalCount);
            return false;
        } catch (Exception e) {
            callback.onError("迁移失败，目标事务已回滚: " + errorMessage(e));
            return false;
        } finally {
            if (sourceConnection != null) {
                try {
                    sourceConnection.rollback();
                    sourceConnection.setAutoCommit(sourceAutoCommit);
                } catch (SQLException ignored) {
                    // 源连接只用于读取，关闭时继续释放资源。
                }
                SQL.database.closeHikariConnection(sourceConnection);
            }
            RUNNING.set(false);
        }
    }

    private static DatabaseType fromStorageType(int type) {
        switch (type) {
            case 1:
                return DatabaseType.SQLITE;
            case 2:
                return DatabaseType.MYSQL;
            case 3:
                return DatabaseType.MARIADB;
            case 4:
                return DatabaseType.POSTGRESQL;
            default:
                return null;
        }
    }

    private static DatabaseType parseTargetType(String targetType) {
        if (targetType == null) {
            return null;
        }
        if (targetType.equalsIgnoreCase("SQLite")) {
            return DatabaseType.SQLITE;
        }
        if (targetType.equalsIgnoreCase("MySQL")) {
            return DatabaseType.MYSQL;
        }
        if (targetType.equalsIgnoreCase("MariaDB")) {
            return DatabaseType.MARIADB;
        }
        if (targetType.equalsIgnoreCase("PostgreSQL") || targetType.equalsIgnoreCase("Postgres")) {
            return DatabaseType.POSTGRESQL;
        }
        return null;
    }

    private static boolean isSameType(DatabaseType sourceType, DatabaseType targetType) {
        if ((sourceType == DatabaseType.MYSQL || sourceType == DatabaseType.MARIADB)
                && (targetType == DatabaseType.MYSQL || targetType == DatabaseType.MARIADB)) {
            return true;
        }
        return sourceType == targetType;
    }

    private static TargetConfig readTargetConfig(DatabaseType type) throws SQLException {
        File file = new File(XConomy.getInstance().getDataFolder(), "database.yml");
        if (!file.isFile()) {
            throw new SQLException("找不到 database.yml");
        }

        // 使用独立的配置对象读取快照，绝不修改 DataBaseConfig.config。
        CConfig config = new CConfig(file);
        if (type == DatabaseType.SQLITE) {
            return new TargetConfig(type, null, 0, null, null, null,
                    valueOrDefault(config.getString("SQLite.path"), "Default"), "", false,
                    null, null, false);
        }

        String section = type == DatabaseType.POSTGRESQL ? "PostgreSQL" : "MySQL";
        int defaultPort = type == DatabaseType.POSTGRESQL ? 5432 : 3306;
        int configuredPort = config.getInt(section + ".port");
        String host = valueOrDefault(config.getString(section + ".host"), "localhost");
        String user = valueOrDefault(config.getString(section + ".user"), "");
        String pass = valueOrDefault(config.getString(section + ".pass"), "");
        String database = valueOrDefault(config.getString(section + ".database"), "");
        if (database.isEmpty()) {
            throw new SQLException(section + ".database 未配置");
        }

        return new TargetConfig(type, host, configuredPort > 0 ? configuredPort : defaultPort,
                user, pass, database, null,
                valueOrDefault(config.getString(section + ".table-suffix"), ""),
                config.getBoolean(section + ".property.usessl"),
                valueOrDefault(config.getString(section + ".property.encoding"), "UTF-8"),
                valueOrDefault(config.getString(section + ".property.timezone"), ""),
                type != DatabaseType.POSTGRESQL
                        && config.getBoolean("MySQL.property.allowPublicKeyRetrieval"));
    }

    private static Connection connectTargetDatabase(TargetConfig config) throws Exception {
        String url;
        switch (config.type) {
            case SQLITE:
                Class.forName("org.sqlite.JDBC");
                File sqliteFile = resolveMigrationSqliteFile(config.sqlitePath);
                File parent = sqliteFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new SQLException("无法创建 SQLite 目录: " + parent);
                }
                XConomy.getInstance().logger(null, 0,
                        "Creating SQLite migration database at: " + sqliteFile.getAbsolutePath());
                return DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
            case MYSQL:
                loadFirstAvailableDriver("me.yic.libs.mysql.cj.jdbc.Driver", "com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver");
                url = mysqlUrl(config, "mysql");
                return DriverManager.getConnection(url, config.user, config.pass);
            case MARIADB:
                loadFirstAvailableDriver("me.yic.libs.mariadb.jdbc.Driver", "org.mariadb.jdbc.Driver");
                url = mysqlUrl(config, "mariadb");
                return DriverManager.getConnection(url, config.user, config.pass);
            case POSTGRESQL:
                Class.forName("org.postgresql.Driver");
                url = "jdbc:postgresql://" + config.host + ":" + config.port + "/" + config.database
                        + "?ssl=" + config.useSsl;
                if (!config.timezone.isEmpty()) {
                    url += "&options=" + URLEncoder.encode("-c TimeZone=" + config.timezone, "UTF-8");
                }
                return DriverManager.getConnection(url, config.user, config.pass);
            default:
                throw new SQLException("不支持的目标数据库类型");
        }
    }

    private static void loadFirstAvailableDriver(String... driverClasses) throws ClassNotFoundException {
        ClassNotFoundException lastError = null;
        for (String driverClass : driverClasses) {
            try {
                Class.forName(driverClass);
                return;
            } catch (ClassNotFoundException e) {
                lastError = e;
            }
        }
        throw lastError == null ? new ClassNotFoundException("找不到数据库驱动") : lastError;
    }

    private static String mysqlUrl(TargetConfig config, String scheme) throws Exception {
        StringBuilder url = new StringBuilder("jdbc:").append(scheme).append("://")
                .append(config.host).append(':').append(config.port).append('/').append(config.database)
                .append("?useSSL=").append(config.useSsl)
                .append("&characterEncoding=").append(URLEncoder.encode(config.encoding, "UTF-8"));
        if (!config.timezone.isEmpty()) {
            url.append("&serverTimezone=").append(URLEncoder.encode(config.timezone, "UTF-8"));
        }
        if (config.allowPublicKeyRetrieval) {
            url.append("&allowPublicKeyRetrieval=true");
        }
        return url.toString();
    }

    private static File resolveMigrationSqliteFile(String configuredPath) {
        if (configuredPath == null || configuredPath.trim().isEmpty()
                || configuredPath.equalsIgnoreCase("Default")) {
            return new File(XConomy.getInstance().getPDataFolder(), "playerdata_migrated.db");
        }

        File configured = new File(configuredPath);
        String lowerName = configured.getName().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".db") || lowerName.endsWith(".sqlite") || lowerName.endsWith(".sqlite3")) {
            String name = configured.getName();
            int extension = name.lastIndexOf('.');
            return new File(configured.getParentFile(), name.substring(0, extension) + "_migrated" + name.substring(extension));
        }
        return new File(configured, "playerdata_migrated.db");
    }

    private static List<TablePlan> buildMigrationPlans(Connection source, TargetConfig targetConfig)
            throws SQLException {
        List<TableSpec> specs = Arrays.asList(
                new TableSpec("玩家数据", "xconomy", SQL.tableName, "UID",
                        Arrays.asList("UID", "player", "balance"), Arrays.asList("hidden")),
                new TableSpec("非玩家账户", "xconomynon", SQL.tableNonPlayerName, "account",
                        Arrays.asList("account", "balance"), new ArrayList<String>()),
                new TableSpec("UUID 映射", "xconomyuuid", SQL.tableUUIDName, "UUID",
                        Arrays.asList("UUID", "DUUID"), new ArrayList<String>()),
                new TableSpec("交易记录", "xconomyrecord", SQL.tableRecordName, "id",
                        Arrays.asList("id", "type", "uid", "player", "balance", "amount", "operation",
                                "command", "comment", "datetime"),
                        Arrays.asList("from_uid", "to_uid", "transaction_type", "server_id", "trace_id",
                                "parent_transaction_id")),
                new TableSpec("登录记录", "xconomylogin", SQL.tableLoginName, "UUID",
                        Arrays.asList("UUID", "last_time"), new ArrayList<String>())
        );

        List<TablePlan> plans = new ArrayList<TablePlan>();
        for (TableSpec spec : specs) {
            String targetName = targetTableName(spec.baseName, targetConfig);
            boolean exists = tableExists(source, spec.sourceName);
            if (!exists) {
                if (spec.baseName.equals("xconomy")) {
                    throw new SQLException("源玩家表不存在: " + spec.sourceName);
                }
                plans.add(new TablePlan(spec, targetName, false, new ArrayList<String>(), false, 0));
                continue;
            }

            Set<String> sourceColumns = getColumns(source, spec.sourceName);
            List<String> columns = new ArrayList<String>();
            for (String required : spec.requiredColumns) {
                if (!sourceColumns.contains(required.toLowerCase(Locale.ROOT))) {
                    throw new SQLException("源表 " + spec.sourceName + " 缺少必要字段 " + required);
                }
                columns.add(required);
            }
            boolean synthesizeHidden = spec.baseName.equals("xconomy")
                    && !sourceColumns.contains("hidden");
            for (String optional : spec.optionalColumns) {
                if (sourceColumns.contains(optional.toLowerCase(Locale.ROOT))) {
                    columns.add(optional);
                } else if (optional.equalsIgnoreCase("hidden")) {
                    columns.add(optional);
                }
            }
            plans.add(new TablePlan(spec, targetName, true, columns, synthesizeHidden,
                    countRows(source, spec.sourceName)));
        }
        return plans;
    }

    private static String targetTableName(String baseName, TargetConfig config) throws SQLException {
        if (config.type == DatabaseType.SQLITE || config.tableSuffix.isEmpty()) {
            return baseName;
        }
        String sign = XConomyLoad.Config.SYNCDATA_SIGN == null ? "" : XConomyLoad.Config.SYNCDATA_SIGN;
        String suffix = config.tableSuffix.replace("%sign%", sign);
        String tableName = baseName + "_" + suffix;
        if (!tableName.matches("[A-Za-z0-9_]+")) {
            throw new SQLException("目标 table-suffix 生成了非法表名: " + tableName);
        }
        return tableName;
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String[] candidates = {tableName, tableName.toLowerCase(Locale.ROOT), tableName.toUpperCase(Locale.ROOT)};
        for (String candidate : candidates) {
            try (ResultSet tables = metaData.getTables(connection.getCatalog(), null, candidate,
                    new String[]{"TABLE"})) {
                while (tables.next()) {
                    if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
            }
        }
        try (ResultSet tables = metaData.getTables(connection.getCatalog(), null, "%",
                new String[]{"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> getColumns(Connection connection, String tableName) throws SQLException {
        Set<String> columns = new HashSet<String>();
        DatabaseMetaData metaData = connection.getMetaData();
        String[] candidates = {tableName, tableName.toLowerCase(Locale.ROOT), tableName.toUpperCase(Locale.ROOT)};
        for (String candidate : candidates) {
            try (ResultSet result = metaData.getColumns(connection.getCatalog(), null, candidate, "%")) {
                while (result.next()) {
                    columns.add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
            if (!columns.isEmpty()) {
                break;
            }
        }
        return columns;
    }

    private static int countRows(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            result.next();
            long count = result.getLong(1);
            if (count > Integer.MAX_VALUE) {
                throw new SQLException("表 " + tableName + " 数据量超过迁移计数上限");
            }
            return (int) count;
        }
    }

    private static void createTargetTables(Connection connection, TargetConfig config, List<TablePlan> plans)
            throws SQLException {
        for (TablePlan plan : plans) {
            String table = plan.targetName;
            String sql;
            if (plan.spec.baseName.equals("xconomy")) {
                sql = "CREATE TABLE IF NOT EXISTS " + table + " (UID VARCHAR(50) NOT NULL PRIMARY KEY, "
                        + "player VARCHAR(50) NOT NULL, balance " + balanceType(config.type)
                        + " NOT NULL, hidden INTEGER NOT NULL DEFAULT 0)";
            } else if (plan.spec.baseName.equals("xconomynon")) {
                sql = "CREATE TABLE IF NOT EXISTS " + table + " (account VARCHAR(50) NOT NULL PRIMARY KEY, "
                        + "balance " + balanceType(config.type) + " NOT NULL)";
            } else if (plan.spec.baseName.equals("xconomyuuid")) {
                sql = "CREATE TABLE IF NOT EXISTS " + table + " (UUID VARCHAR(50) NOT NULL PRIMARY KEY, "
                        + "DUUID VARCHAR(50) NOT NULL)";
            } else if (plan.spec.baseName.equals("xconomyrecord")) {
                sql = createRecordTableSql(table, config.type);
            } else {
                sql = "CREATE TABLE IF NOT EXISTS " + table + " (UUID VARCHAR(50) NOT NULL PRIMARY KEY, "
                        + "last_time " + dateTimeType(config.type) + " NOT NULL)";
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }
    }

    private static void ensureTargetTablesEmpty(Connection connection, List<TablePlan> plans) throws SQLException {
        for (TablePlan plan : plans) {
            if (countRows(connection, plan.targetName) > 0) {
                throw new SQLException("目标表 " + plan.targetName + " 已有数据，请使用空数据库或清空目标表后重试");
            }
        }
    }

    private static String createRecordTableSql(String table, DatabaseType type) {
        String id;
        if (type == DatabaseType.SQLITE) {
            id = "INTEGER PRIMARY KEY AUTOINCREMENT";
        } else if (type == DatabaseType.POSTGRESQL) {
            id = "BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
        } else {
            id = "BIGINT AUTO_INCREMENT PRIMARY KEY";
        }
        String number = balanceType(type);
        return "CREATE TABLE IF NOT EXISTS " + table + " (id " + id
                + ", type VARCHAR(50) NOT NULL, uid VARCHAR(50) NOT NULL, player VARCHAR(50) NOT NULL"
                + ", balance " + number + ", amount " + number + " NOT NULL, operation VARCHAR(50) NOT NULL"
                + ", command VARCHAR(255) NOT NULL, comment VARCHAR(255) NOT NULL, datetime "
                + dateTimeType(type) + " NOT NULL, from_uid VARCHAR(50), to_uid VARCHAR(50)"
                + ", transaction_type VARCHAR(20), server_id VARCHAR(50), trace_id VARCHAR(50)"
                + ", parent_transaction_id BIGINT)";
    }

    private static String balanceType(DatabaseType type) {
        if (type == DatabaseType.POSTGRESQL) {
            return "DOUBLE PRECISION";
        }
        if (type == DatabaseType.SQLITE) {
            return "REAL";
        }
        return "DOUBLE(20,2)";
    }

    private static String dateTimeType(DatabaseType type) {
        return type == DatabaseType.POSTGRESQL ? "TIMESTAMP" : "DATETIME";
    }

    private static int migrateTable(Connection source, Connection target, DatabaseType targetType,
                                    TablePlan plan, int alreadyMigrated, int totalCount,
                                    MigrationCallback callback) throws SQLException {
        String selectSql = buildSelectSql(plan);
        String insertOrUpdateSql = buildUpsertSql(plan.targetName, plan.columns, plan.spec.keyColumn, targetType);
        int migrated = 0;
        int pending = 0;

        try (Statement sourceStatement = source.createStatement();
             ResultSet rows = sourceStatement.executeQuery(selectSql);
             PreparedStatement insert = target.prepareStatement(insertOrUpdateSql)) {
            while (rows.next()) {
                for (int index = 0; index < plan.columns.size(); index++) {
                    String column = plan.columns.get(index);
                    if (column.equalsIgnoreCase("datetime") || column.equalsIgnoreCase("last_time")) {
                        insert.setTimestamp(index + 1, rows.getTimestamp(index + 1));
                    } else {
                        insert.setObject(index + 1, rows.getObject(index + 1));
                    }
                }
                insert.addBatch();
                pending++;
                if (pending >= BATCH_SIZE) {
                    insert.executeBatch();
                    migrated += pending;
                    pending = 0;
                    callback.onProgress("已迁移 " + (alreadyMigrated + migrated) + "/" + totalCount + " 条数据");
                }
            }
            if (pending > 0) {
                insert.executeBatch();
                migrated += pending;
            }
        }
        return migrated;
    }

    private static String buildSelectSql(TablePlan plan) {
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < plan.columns.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            String column = plan.columns.get(i);
            if (plan.synthesizeHidden && column.equalsIgnoreCase("hidden")) {
                sql.append("0 AS hidden");
            } else {
                sql.append(column);
            }
        }
        return sql.append(" FROM ").append(plan.spec.sourceName).toString();
    }

    private static String buildUpsertSql(String tableName, List<String> columns, String keyColumn,
                                         DatabaseType type) {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
        appendJoined(sql, columns, null, null);
        sql.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(')');

        List<String> updateColumns = new ArrayList<String>();
        for (String column : columns) {
            if (!column.equalsIgnoreCase(keyColumn)) {
                updateColumns.add(column);
            }
        }
        if (type == DatabaseType.MYSQL || type == DatabaseType.MARIADB) {
            sql.append(" ON DUPLICATE KEY UPDATE ");
            appendJoined(sql, updateColumns, "=VALUES(", ")");
        } else {
            sql.append(" ON CONFLICT (").append(keyColumn).append(") ");
            if (updateColumns.isEmpty()) {
                sql.append("DO NOTHING");
            } else {
                sql.append("DO UPDATE SET ");
                appendJoined(sql, updateColumns, "=excluded.", "");
            }
        }
        return sql.toString();
    }

    private static void appendJoined(StringBuilder sql, List<String> columns, String valuePrefix,
                                     String valueSuffix) {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            String column = columns.get(i);
            sql.append(column);
            if (valuePrefix != null) {
                sql.append(valuePrefix).append(column).append(valueSuffix);
            }
        }
    }

    private static void synchronizePostgreSqlRecordSequence(Connection connection, List<TablePlan> plans)
            throws SQLException {
        for (TablePlan plan : plans) {
            if (plan.sourceExists && plan.spec.baseName.equals("xconomyrecord") && plan.rowCount > 0) {
                String sql = "SELECT setval(pg_get_serial_sequence('" + plan.targetName
                        + "', 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM " + plan.targetName;
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
                return;
            }
        }
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName() : message;
    }

    private static void rollbackQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // 保留原始迁移异常。
        }
    }

    public interface MigrationCallback {
        void onStart(String sourceType, String targetType);

        void onProgress(String message);

        void onComplete(int successCount, int totalCount);

        void onError(String error);
    }
}
