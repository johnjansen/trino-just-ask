package io.trino.plugin.justask;

import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.connector.ConnectorTableMetadata;
import io.trino.spi.connector.TableFunctionApplicationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

class TestJustAskMetadata
{
    private final JustAskMetadata metadata = new JustAskMetadata();

    @Test
    void testApplyQueryFunction()
    {
        QueryFunctionHandle handle = new QueryFunctionHandle("question", "catalog", "SELECT 1");
        Optional<TableFunctionApplicationResult<io.trino.spi.connector.ConnectorTableHandle>> result =
                metadata.applyTableFunction(null, handle);

        assertThat(result).isPresent();

        JustAskTableHandle tableHandle = (JustAskTableHandle) result.get().getTableHandle();
        assertThat(tableHandle.getColumnNames()).containsExactly("sql");
        assertThat(tableHandle.getRows()).hasSize(1);
        assertThat(tableHandle.getRows().get(0)).containsExactly("SELECT 1");

        List<ColumnHandle> columnHandles = result.get().getColumnHandles();
        assertThat(columnHandles).hasSize(1);
        assertThat(((JustAskColumnHandle) columnHandles.get(0)).getName()).isEqualTo("sql");
        assertThat(((JustAskColumnHandle) columnHandles.get(0)).getIndex()).isEqualTo(0);
    }

    @Test
    void testApplyAskFunction()
    {
        List<String> columns = List.of("name", "count");
        List<List<String>> rows = List.of(
                List.of("alice", "10"),
                List.of("bob", "20"));
        AskFunctionHandle handle = new AskFunctionHandle("q", "c", "sql", columns, rows);

        Optional<TableFunctionApplicationResult<io.trino.spi.connector.ConnectorTableHandle>> result =
                metadata.applyTableFunction(null, handle);

        assertThat(result).isPresent();

        JustAskTableHandle tableHandle = (JustAskTableHandle) result.get().getTableHandle();
        assertThat(tableHandle.getColumnNames()).containsExactly("name", "count");
        assertThat(tableHandle.getRows()).hasSize(2);

        List<ColumnHandle> columnHandles = result.get().getColumnHandles();
        assertThat(columnHandles).hasSize(2);
        assertThat(((JustAskColumnHandle) columnHandles.get(0)).getName()).isEqualTo("name");
        assertThat(((JustAskColumnHandle) columnHandles.get(1)).getName()).isEqualTo("count");
    }

    @Test
    void testGetTableMetadata()
    {
        JustAskTableHandle tableHandle = new JustAskTableHandle(
                List.of("col1", "col2"), List.of());

        ConnectorTableMetadata tableMetadata = metadata.getTableMetadata(null, tableHandle);
        assertThat(tableMetadata.getColumns()).hasSize(2);
        assertThat(tableMetadata.getColumns().get(0).getName()).isEqualTo("col1");
        assertThat(tableMetadata.getColumns().get(0).getType()).isEqualTo(VARCHAR);
        assertThat(tableMetadata.getColumns().get(1).getName()).isEqualTo("col2");
    }

    @Test
    void testGetColumnHandles()
    {
        JustAskTableHandle tableHandle = new JustAskTableHandle(
                List.of("a", "b", "c"), List.of());

        Map<String, ColumnHandle> handles = metadata.getColumnHandles(null, tableHandle);
        assertThat(handles).hasSize(3);
        assertThat(handles).containsKeys("a", "b", "c");
        assertThat(((JustAskColumnHandle) handles.get("a")).getIndex()).isEqualTo(0);
        assertThat(((JustAskColumnHandle) handles.get("c")).getIndex()).isEqualTo(2);
    }

    @Test
    void testGetColumnMetadata()
    {
        JustAskColumnHandle columnHandle = new JustAskColumnHandle("mycol", 0);
        ColumnMetadata columnMetadata = metadata.getColumnMetadata(null, null, columnHandle);
        assertThat(columnMetadata.getName()).isEqualTo("mycol");
        assertThat(columnMetadata.getType()).isEqualTo(VARCHAR);
    }
}
