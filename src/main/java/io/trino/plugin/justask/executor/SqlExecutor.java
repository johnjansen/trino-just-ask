package io.trino.plugin.justask.executor;

import io.trino.spi.TrinoException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static io.trino.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;

public class SqlExecutor
{
    private final String jdbcUrl;

    public SqlExecutor(String jdbcUrl)
    {
        this.jdbcUrl = jdbcUrl;
        try {
            Class.forName("io.trino.jdbc.TrinoDriver");
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException("Trino JDBC driver not found on classpath", e);
        }
    }

    public SqlResult execute(String sql)
    {
        Properties props = new Properties();
        props.setProperty("user", "justask");
        try (Connection conn = DriverManager.getConnection(jdbcUrl, props);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            List<String> columnNames = new ArrayList<>();
            List<Integer> columnTypes = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(meta.getColumnName(i));
                columnTypes.add(meta.getColumnType(i));
            }

            List<List<Object>> rows = new ArrayList<>();
            while (rs.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                rows.add(row);
            }

            return new SqlResult(columnNames, columnTypes, rows);
        }
        catch (SQLException e) {
            throw new TrinoException(GENERIC_INTERNAL_ERROR,
                    "Failed to execute generated SQL: " + e.getMessage(), e);
        }
    }

    public record SqlResult(
            List<String> columnNames,
            List<Integer> columnTypes,
            List<List<Object>> rows) {}
}
