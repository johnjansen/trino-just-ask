package io.trino.plugin.justask;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
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
        Multibinder.newSetBinder(binder(), ConnectorTableFunction.class);
    }
}
