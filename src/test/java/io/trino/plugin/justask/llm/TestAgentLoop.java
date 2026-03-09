package io.trino.plugin.justask.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAgentLoop
{
    @Test
    void testExtractSqlFromCodeBlock()
    {
        String response = "Here's your query:\n```sql\nSELECT * FROM users WHERE active = true\n```";
        String sql = AgentLoop.extractSql(response);
        assertThat(sql).isEqualTo("SELECT * FROM users WHERE active = true");
    }

    @Test
    void testExtractSqlPlainText()
    {
        String response = "SELECT * FROM users WHERE active = true";
        String sql = AgentLoop.extractSql(response);
        assertThat(sql).isEqualTo("SELECT * FROM users WHERE active = true");
    }

    @Test
    void testExtractSqlFromGenericCodeBlock()
    {
        String response = "```\nSELECT 1\n```";
        String sql = AgentLoop.extractSql(response);
        assertThat(sql).isEqualTo("SELECT 1");
    }
}
