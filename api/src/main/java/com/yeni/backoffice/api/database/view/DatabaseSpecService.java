package com.yeni.backoffice.api.database.view;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DatabaseSpecService {

    private static final String DEFAULT_SCHEMA = "PUBLIC";

    private final DataSource dataSource;
    private final DatabaseSpecDescriptionCatalog descriptionCatalog;

    // JDBC 메타데이터 스캔(테이블·컬럼별 라운드트립)은 원격 DB에서 느리다. 스키마는 배포 사이에 바뀌지 않으므로
    // 첫 호출 결과를 캐시한다.
    private volatile List<TableSpec> cachedSpecs;

    public DatabaseSpecService(DataSource dataSource, DatabaseSpecDescriptionCatalog descriptionCatalog) {
        this.dataSource = dataSource;
        this.descriptionCatalog = descriptionCatalog;
    }

    public List<TableSpec> getTableSpecs() {
        List<TableSpec> specs = cachedSpecs;
        if (specs != null) {
            return specs;
        }
        synchronized (this) {
            if (cachedSpecs == null) {
                try (Connection connection = dataSource.getConnection()) {
                    cachedSpecs = List.copyOf(getTables(connection));
                } catch (SQLException e) {
                    throw new IllegalStateException("DB 명세 자동 조회 중 오류가 발생했습니다.", e);
                }
            }
            return cachedSpecs;
        }
    }

    List<TableSpec> getTables(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String schema = resolveSchema(connection);
        List<TableSpec> tables = new ArrayList<>();

        try (ResultSet rs = metaData.getTables(connection.getCatalog(), schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (isSystemTable(tableName)) {
                    continue;
                }
                String remarks = firstText(rs.getString("REMARKS"), getTableRemarksFallback(connection, schema, tableName));
                tables.add(new TableSpec(
                        normalizeName(tableName),
                        descriptionCatalog.tableDescription(tableName).orElseGet(() -> defaultTableDescription(remarks)),
                        getColumns(connection, tableName)
                ));
            }
        }

        tables.sort(Comparator.comparing(TableSpec::tableName));
        return tables;
    }

    List<ColumnSpec> getColumns(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String schema = resolveSchema(connection);
        Set<String> primaryKeys = getPrimaryKeys(metaData, schema, tableName);
        List<ColumnSpec> columns = new ArrayList<>();

        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), schema, tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                boolean primaryKey = primaryKeys.contains(normalizeName(columnName));
                boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                String defaultValue = rs.getString("COLUMN_DEF");
                String remarks = firstText(
                        rs.getString("REMARKS"),
                        getColumnRemarksFallback(connection, schema, tableName, columnName)
                );

                columns.add(new ColumnSpec(
                        normalizeName(columnName),
                        buildDisplayType(rs, primaryKey),
                        nullable,
                        defaultValue,
                        primaryKey,
                        descriptionCatalog.columnDescription(tableName, columnName)
                                .orElseGet(() -> defaultColumnDescription(remarks, primaryKey, nullable, defaultValue)),
                        rs.getInt("ORDINAL_POSITION")
                ));
            }
        }

        columns.sort(Comparator.comparingInt(ColumnSpec::ordinalPosition));
        return columns;
    }

    Set<String> getPrimaryKeys(DatabaseMetaData metaData, String schema, String tableName) throws SQLException {
        Set<String> primaryKeys = new HashSet<>();
        try (ResultSet rs = metaData.getPrimaryKeys(null, schema, tableName)) {
            while (rs.next()) {
                primaryKeys.add(normalizeName(rs.getString("COLUMN_NAME")));
            }
        }
        if (primaryKeys.isEmpty()) {
            try (ResultSet rs = metaData.getPrimaryKeys(null, schema.toUpperCase(Locale.ROOT), tableName.toUpperCase(Locale.ROOT))) {
                while (rs.next()) {
                    primaryKeys.add(normalizeName(rs.getString("COLUMN_NAME")));
                }
            }
        }
        if (primaryKeys.isEmpty()) {
            try (ResultSet rs = metaData.getPrimaryKeys(null, schema.toLowerCase(Locale.ROOT), tableName.toLowerCase(Locale.ROOT))) {
                while (rs.next()) {
                    primaryKeys.add(normalizeName(rs.getString("COLUMN_NAME")));
                }
            }
        }
        return primaryKeys;
    }

    String buildDisplayType(ResultSet rs, boolean primaryKey) throws SQLException {
        int dataType = rs.getInt("DATA_TYPE");
        String typeName = normalizeTypeName(rs.getString("TYPE_NAME"));
        int size = rs.getInt("COLUMN_SIZE");
        int scale = rs.getInt("DECIMAL_DIGITS");

        String displayType = switch (dataType) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR ->
                    typeName + "(" + size + ")";
            case Types.DECIMAL, Types.NUMERIC -> typeName + "(" + size + ", " + scale + ")";
            default -> typeName;
        };
        return primaryKey ? "PK / " + displayType : displayType;
    }

    boolean isSystemTable(String tableName) {
        String name = normalizeName(tableName).toUpperCase(Locale.ROOT);
        return name.equals("FLYWAY_SCHEMA_HISTORY")
                || name.startsWith("INFORMATION_SCHEMA")
                || name.startsWith("SYSTEM_")
                || name.startsWith("SYS_")
                || name.startsWith("DUAL");
    }

    String getTableRemarksFallback(Connection connection, String schema, String tableName) {
        return querySingleRemarks(
                connection,
                "select remarks from information_schema.tables where table_schema = ? and table_name = ?",
                schema,
                tableName
        );
    }

    String getColumnRemarksFallback(Connection connection, String schema, String tableName, String columnName) {
        return querySingleRemarks(
                connection,
                "select remarks from information_schema.columns where table_schema = ? and table_name = ? and column_name = ?",
                schema,
                tableName,
                columnName
        );
    }

    private String querySingleRemarks(Connection connection, String sql, String... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException ignored) {
            return null;
        }
    }

    private String resolveSchema(Connection connection) throws SQLException {
        String schema = connection.getSchema();
        return StringUtils.hasText(schema) ? schema : DEFAULT_SCHEMA;
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : null;
    }

    private String defaultTableDescription(String remarks) {
        return StringUtils.hasText(remarks) ? remarks : "테이블 설명이 등록되지 않았습니다.";
    }

    private String defaultColumnDescription(String remarks, boolean primaryKey, boolean nullable, String defaultValue) {
        if (StringUtils.hasText(remarks)) {
            return remarks;
        }
        List<String> descriptions = new ArrayList<>();
        if (primaryKey) {
            descriptions.add("기본키");
        }
        descriptions.add(nullable ? "NULL 허용" : "필수값");
        if (StringUtils.hasText(defaultValue)) {
            descriptions.add("기본값 " + defaultValue);
        }
        return String.join(" / ", descriptions);
    }

    private String normalizeTypeName(String typeName) {
        String name = StringUtils.hasText(typeName) ? typeName.toUpperCase(Locale.ROOT) : "UNKNOWN";
        return switch (name) {
            case "CHARACTER VARYING" -> "VARCHAR";
            case "NUMERIC" -> "DECIMAL";
            case "TIMESTAMP WITH TIME ZONE" -> "TIMESTAMP";
            default -> name;
        };
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    public record TableSpec(String tableName, String tableDescription, List<ColumnSpec> columns) {
    }

    public record ColumnSpec(
            String columnName,
            String dataType,
            boolean nullable,
            String defaultValue,
            boolean primaryKey,
            String columnDescription,
            int ordinalPosition
    ) {
    }
}
