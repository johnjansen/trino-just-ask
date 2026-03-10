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

    @Test
    void testExtractSqlStripsSemicolon()
    {
        String response = "```sql\nSELECT * FROM users;\n```";
        String sql = AgentLoop.extractSql(response);
        assertThat(sql).isEqualTo("SELECT * FROM users");
    }

    @Test
    void testExtractSqlStripsMultipleSemicolons()
    {
        String response = "SELECT 1; ;  ;";
        String sql = AgentLoop.extractSql(response);
        assertThat(sql).isEqualTo("SELECT 1");
    }

    @Test
    void testExtractSqlFirstCodeBlockWins()
    {
        String response = "Here:\n```sql\nSELECT 1\n```\nOr:\n```sql\nSELECT 2\n```";
        String sql = AgentLoop.extractSql(response);
        assertThat(sql).isEqualTo("SELECT 1");
    }

    @Test
    void testExtractSqlTrimsWhitespace()
    {
        String response = "  \n  SELECT 1  \n  ";
        String sql = AgentLoop.extractSql(response);
        assertThat(sql).isEqualTo("SELECT 1");
    }

    @Test
    void testExtractSqlCodeBlockWithWhitespace()
    {
        String response = "```sql\n  SELECT *\n  FROM users\n  WHERE id = 1\n```";
        String sql = AgentLoop.extractSql(response);
        assertThat(sql).isEqualTo("SELECT *\n  FROM users\n  WHERE id = 1");
    }
}
