package io.trino.plugin.justask;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestHandles
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testQueryFunctionHandleProperties()
    {
        QueryFunctionHandle handle = new QueryFunctionHandle("what tables?", "sales", "SELECT 1");
        assertThat(handle.getQuestion()).isEqualTo("what tables?");
        assertThat(handle.getCatalog()).isEqualTo("sales");
        assertThat(handle.getGeneratedSql()).isEqualTo("SELECT 1");
    }

    @Test
    void testQueryFunctionHandleNullCatalog()
    {
        QueryFunctionHandle handle = new QueryFunctionHandle("question", null, "SELECT 1");
        assertThat(handle.getCatalog()).isNull();
    }

    @Test
    void testQueryFunctionHandleJsonRoundTrip() throws Exception
    {
        QueryFunctionHandle original = new QueryFunctionHandle("question", "catalog", "SELECT 1");
        String json = MAPPER.writeValueAsString(original);
        QueryFunctionHandle restored = MAPPER.readValue(json, QueryFunctionHandle.class);

        assertThat(restored.getQuestion()).isEqualTo(original.getQuestion());
        assertThat(restored.getCatalog()).isEqualTo(original.getCatalog());
        assertThat(restored.getGeneratedSql()).isEqualTo(original.getGeneratedSql());
    }

    @Test
    void testAskFunctionHandleProperties()
    {
        List<String> columns = List.of("name", "count");
        List<List<String>> rows = List.of(
                List.of("alice", "10"),
                List.of("bob", "20"));

        AskFunctionHandle handle = new AskFunctionHandle("who?", "sales", "SELECT name, count FROM users", columns, rows);
        assertThat(handle.getQuestion()).isEqualTo("who?");
        assertThat(handle.getCatalog()).isEqualTo("sales");
        assertThat(handle.getGeneratedSql()).isEqualTo("SELECT name, count FROM users");
        assertThat(handle.getColumnNames()).containsExactly("name", "count");
        assertThat(handle.getRows()).hasSize(2);
        assertThat(handle.getRows().get(0)).containsExactly("alice", "10");
    }

    @Test
    void testAskFunctionHandleJsonRoundTrip() throws Exception
    {
        List<String> columns = List.of("a", "b");
        List<List<String>> rows = List.of(List.of("1", "2"));

        AskFunctionHandle original = new AskFunctionHandle("q", "c", "sql", columns, rows);
        String json = MAPPER.writeValueAsString(original);
        AskFunctionHandle restored = MAPPER.readValue(json, AskFunctionHandle.class);

        assertThat(restored.getQuestion()).isEqualTo(original.getQuestion());
        assertThat(restored.getColumnNames()).isEqualTo(original.getColumnNames());
        assertThat(restored.getRows()).isEqualTo(original.getRows());
    }

    @Test
    void testTableHandleProperties()
    {
        List<String> columns = List.of("x", "y");
        List<List<String>> rows = List.of(List.of("1", "2"));

        JustAskTableHandle handle = new JustAskTableHandle(columns, rows);
        assertThat(handle.getColumnNames()).containsExactly("x", "y");
        assertThat(handle.getRows()).hasSize(1);
    }

    @Test
    void testTableHandleJsonRoundTrip() throws Exception
    {
        JustAskTableHandle original = new JustAskTableHandle(
                List.of("col1", "col2"),
                List.of(List.of("a", "b"), List.of("c", "d")));

        String json = MAPPER.writeValueAsString(original);
        JustAskTableHandle restored = MAPPER.readValue(json, JustAskTableHandle.class);

        assertThat(restored.getColumnNames()).isEqualTo(original.getColumnNames());
        assertThat(restored.getRows()).isEqualTo(original.getRows());
    }

    @Test
    void testColumnHandleProperties()
    {
        JustAskColumnHandle handle = new JustAskColumnHandle("name", 3);
        assertThat(handle.getName()).isEqualTo("name");
        assertThat(handle.getIndex()).isEqualTo(3);
    }

    @Test
    void testColumnHandleJsonRoundTrip() throws Exception
    {
        JustAskColumnHandle original = new JustAskColumnHandle("col", 5);
        String json = MAPPER.writeValueAsString(original);
        JustAskColumnHandle restored = MAPPER.readValue(json, JustAskColumnHandle.class);

        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getIndex()).isEqualTo(original.getIndex());
    }
}
