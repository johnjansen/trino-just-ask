package io.trino.plugin.justask;

import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.connector.ConnectorMetadata;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTableMetadata;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.TableFunctionApplicationResult;
import io.trino.spi.function.table.ConnectorTableFunctionHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.spi.type.VarcharType.VARCHAR;

public class JustAskMetadata
        implements ConnectorMetadata
{
    @Override
    public Optional<TableFunctionApplicationResult<ConnectorTableHandle>> applyTableFunction(
            ConnectorSession session,
            ConnectorTableFunctionHandle handle)
    {
        if (handle instanceof QueryFunctionHandle queryHandle) {
            List<String> columnNames = List.of("sql");
            List<List<String>> rows = List.of(List.of(queryHandle.getGeneratedSql()));

            JustAskTableHandle tableHandle = new JustAskTableHandle(columnNames, rows);
            List<ColumnHandle> columnHandles = List.of(new JustAskColumnHandle("sql", 0));

            return Optional.of(new TableFunctionApplicationResult<>(tableHandle, columnHandles));
        }

        if (handle instanceof AskFunctionHandle askHandle) {
            JustAskTableHandle tableHandle = new JustAskTableHandle(
                    askHandle.getColumnNames(),
                    askHandle.getRows());

            List<ColumnHandle> columnHandles = new ArrayList<>();
            for (int i = 0; i < askHandle.getColumnNames().size(); i++) {
                columnHandles.add(new JustAskColumnHandle(askHandle.getColumnNames().get(i), i));
            }

            return Optional.of(new TableFunctionApplicationResult<>(tableHandle, columnHandles));
        }

        return Optional.empty();
    }

    @Override
    public ConnectorTableMetadata getTableMetadata(ConnectorSession session, ConnectorTableHandle table)
    {
        JustAskTableHandle tableHandle = (JustAskTableHandle) table;
        List<ColumnMetadata> columns = new ArrayList<>();
        for (String name : tableHandle.getColumnNames()) {
            columns.add(new ColumnMetadata(name, VARCHAR));
        }
        return new ConnectorTableMetadata(new SchemaTableName("system", "result"), columns);
    }

    @Override
    public Map<String, ColumnHandle> getColumnHandles(ConnectorSession session, ConnectorTableHandle table)
    {
        JustAskTableHandle tableHandle = (JustAskTableHandle) table;
        Map<String, ColumnHandle> handles = new java.util.LinkedHashMap<>();
        for (int i = 0; i < tableHandle.getColumnNames().size(); i++) {
            String name = tableHandle.getColumnNames().get(i);
            handles.put(name, new JustAskColumnHandle(name, i));
        }
        return handles;
    }

    @Override
    public ColumnMetadata getColumnMetadata(ConnectorSession session, ConnectorTableHandle table, ColumnHandle column)
    {
        JustAskColumnHandle col = (JustAskColumnHandle) column;
        return new ColumnMetadata(col.getName(), VARCHAR);
    }
}
