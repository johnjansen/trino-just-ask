package io.trino.plugin.justask;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestJustAskConfig
{
    @Test
    void testDefaults()
    {
        JustAskConfig config = new JustAskConfig();

        assertThat(config.getLlmEndpoint()).isEqualTo("https://api.openai.com/v1");
        assertThat(config.getLlmApiKey()).isNull();
        assertThat(config.getLlmModel()).isEqualTo("gpt-4o");
        assertThat(config.getLlmTemperature()).isEqualTo(0.2);
        assertThat(config.getLlmMaxTokens()).isEqualTo(4096);
        assertThat(config.getLlmMaxToolCalls()).isEqualTo(10);
        assertThat(config.getDocsBaseDir()).isEqualTo("etc/justask/catalogs");
        assertThat(config.getPromptTemplateFile()).isEqualTo("etc/justask/system-prompt.md");
        assertThat(config.getExecutorJdbcUrl()).isEqualTo("jdbc:trino://localhost:8080");
    }

    @Test
    void testSetters()
    {
        JustAskConfig config = new JustAskConfig()
                .setLlmEndpoint("http://localhost:11434/v1")
                .setLlmApiKey("sk-test")
                .setLlmModel("llama3")
                .setLlmTemperature(0.5)
                .setLlmMaxTokens(2048)
                .setLlmMaxToolCalls(5)
                .setDocsBaseDir("/opt/docs")
                .setPromptTemplateFile("/opt/prompt.md")
                .setExecutorJdbcUrl("jdbc:trino://remote:8443");

        assertThat(config.getLlmEndpoint()).isEqualTo("http://localhost:11434/v1");
        assertThat(config.getLlmApiKey()).isEqualTo("sk-test");
        assertThat(config.getLlmModel()).isEqualTo("llama3");
        assertThat(config.getLlmTemperature()).isEqualTo(0.5);
        assertThat(config.getLlmMaxTokens()).isEqualTo(2048);
        assertThat(config.getLlmMaxToolCalls()).isEqualTo(5);
        assertThat(config.getDocsBaseDir()).isEqualTo("/opt/docs");
        assertThat(config.getPromptTemplateFile()).isEqualTo("/opt/prompt.md");
        assertThat(config.getExecutorJdbcUrl()).isEqualTo("jdbc:trino://remote:8443");
    }

    @Test
    void testFluentSetters()
    {
        JustAskConfig config = new JustAskConfig();
        JustAskConfig returned = config.setLlmModel("test");
        assertThat(returned).isSameAs(config);
    }
}
