package io.trino.plugin.justask;

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigSecuritySensitive;
import jakarta.validation.constraints.NotNull;

public class JustAskConfig
{
    private String llmEndpoint = "https://api.openai.com/v1";
    private String llmApiKey;
    private String llmModel = "gpt-4o";
    private double llmTemperature = 0.2;
    private int llmMaxTokens = 4096;
    private int llmMaxToolCalls = 10;
    private String docsBaseDir = "etc/justask/catalogs";
    private String promptTemplateFile = "etc/justask/system-prompt.md";
    private String executorJdbcUrl = "jdbc:trino://localhost:8080";

    @NotNull
    public String getLlmEndpoint()
    {
        return llmEndpoint;
    }

    @Config("llm.endpoint")
    public JustAskConfig setLlmEndpoint(String llmEndpoint)
    {
        this.llmEndpoint = llmEndpoint;
        return this;
    }

    @NotNull
    public String getLlmApiKey()
    {
        return llmApiKey;
    }

    @Config("llm.api-key")
    @ConfigSecuritySensitive
    public JustAskConfig setLlmApiKey(String llmApiKey)
    {
        this.llmApiKey = llmApiKey;
        return this;
    }

    @NotNull
    public String getLlmModel()
    {
        return llmModel;
    }

    @Config("llm.model")
    public JustAskConfig setLlmModel(String llmModel)
    {
        this.llmModel = llmModel;
        return this;
    }

    public double getLlmTemperature()
    {
        return llmTemperature;
    }

    @Config("llm.temperature")
    public JustAskConfig setLlmTemperature(double llmTemperature)
    {
        this.llmTemperature = llmTemperature;
        return this;
    }

    public int getLlmMaxTokens()
    {
        return llmMaxTokens;
    }

    @Config("llm.max-tokens")
    public JustAskConfig setLlmMaxTokens(int llmMaxTokens)
    {
        this.llmMaxTokens = llmMaxTokens;
        return this;
    }

    public int getLlmMaxToolCalls()
    {
        return llmMaxToolCalls;
    }

    @Config("llm.max-tool-calls")
    public JustAskConfig setLlmMaxToolCalls(int llmMaxToolCalls)
    {
        this.llmMaxToolCalls = llmMaxToolCalls;
        return this;
    }

    @NotNull
    public String getDocsBaseDir()
    {
        return docsBaseDir;
    }

    @Config("docs.base-dir")
    public JustAskConfig setDocsBaseDir(String docsBaseDir)
    {
        this.docsBaseDir = docsBaseDir;
        return this;
    }

    @NotNull
    public String getPromptTemplateFile()
    {
        return promptTemplateFile;
    }

    @Config("prompt.template-file")
    public JustAskConfig setPromptTemplateFile(String promptTemplateFile)
    {
        this.promptTemplateFile = promptTemplateFile;
        return this;
    }

    @NotNull
    public String getExecutorJdbcUrl()
    {
        return executorJdbcUrl;
    }

    @Config("executor.jdbc-url")
    public JustAskConfig setExecutorJdbcUrl(String executorJdbcUrl)
    {
        this.executorJdbcUrl = executorJdbcUrl;
        return this;
    }
}
