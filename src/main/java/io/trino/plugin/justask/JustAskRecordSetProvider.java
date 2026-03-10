package io.trino.plugin.justask;

import com.google.common.collect.ImmutableList;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorRecordSetProvider;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.connector.InMemoryRecordSet;
import io.trino.spi.connector.RecordSet;
import io.trino.spi.type.Type;

import java.util.List;

import static io.trino.spi.type.VarcharType.VARCHAR;

public class JustAskRecordSetProvider
        implements ConnectorRecordSetProvider
{
    @Override
    public RecordSet getRecordSet(
            ConnectorTransactionHandle transaction,
            ConnectorSession session,
            ConnectorSplit split,
            ConnectorTableHandle table,
            List<? extends ColumnHandle> columns)
    {
        JustAskTableHandle tableHandle = (JustAskTableHandle) table;

        // Build column types (all VARCHAR)
        ImmutableList.Builder<Type> types = ImmutableList.builder();
        int[] columnIndexes = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            types.add(VARCHAR);
            columnIndexes[i] = ((JustAskColumnHandle) columns.get(i)).getIndex();
        }

        InMemoryRecordSet.Builder builder = InMemoryRecordSet.builder(types.build());

        for (List<String> row : tableHandle.getRows()) {
            Object[] values = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                String value = row.get(columnIndexes[i]);
                values[i] = value;
            }
            builder.addRow(values);
        }

        return builder.build();
    }
}
