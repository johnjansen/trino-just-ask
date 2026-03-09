package io.trino.plugin.justask;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import io.trino.plugin.justask.docs.DocReader;
import io.trino.plugin.justask.executor.SqlExecutor;
import io.trino.plugin.justask.llm.AgentLoop;
import io.trino.plugin.justask.llm.LlmClient;
import io.trino.plugin.justask.llm.PromptTemplate;
import io.trino.spi.function.table.ConnectorTableFunction;

import static io.airlift.configuration.ConfigBinder.configBinder;

public class JustAskModule
        extends AbstractModule
{
    @Override
    protected void configure()
    {
        configBinder(binder()).bindConfig(JustAskConfig.class);
        binder().bind(JustAskConnector.class).in(Scopes.SINGLETON);
        Multibinder<ConnectorTableFunction> tableFunctions =
                Multibinder.newSetBinder(binder(), ConnectorTableFunction.class);
        tableFunctions.addBinding().to(QueryFunction.class).in(Scopes.SINGLETON);
        tableFunctions.addBinding().to(AskFunction.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    public LlmClient provideLlmClient(JustAskConfig config)
    {
        return new LlmClient(
                config.getLlmEndpoint(),
                config.getLlmApiKey(),
                config.getLlmModel(),
                config.getLlmTemperature(),
                config.getLlmMaxTokens());
    }

    @Provides
    @Singleton
    public DocReader provideDocReader(JustAskConfig config)
    {
        return new DocReader(config.getDocsBaseDir());
    }

    @Provides
    @Singleton
    public PromptTemplate providePromptTemplate(JustAskConfig config)
    {
        return new PromptTemplate(config.getPromptTemplateFile());
    }

    @Provides
    @Singleton
    public AgentLoop provideAgentLoop(LlmClient llmClient, PromptTemplate promptTemplate, DocReader docReader, JustAskConfig config)
    {
        return new AgentLoop(llmClient, promptTemplate, docReader, config.getLlmMaxToolCalls());
    }

    @Provides
    @Singleton
    public SqlExecutor provideSqlExecutor(JustAskConfig config)
    {
        return new SqlExecutor(config.getExecutorJdbcUrl());
    }
}
