package io.trino.plugin.justask;

import io.airlift.bootstrap.Bootstrap;
import io.trino.spi.connector.Connector;
import io.trino.spi.connector.ConnectorContext;
import io.trino.spi.connector.ConnectorFactory;

import java.util.Map;

public class JustAskConnectorFactory
        implements ConnectorFactory
{
    @Override
    public String getName()
    {
        return "justask";
    }

    @Override
    public Connector create(String catalogName, Map<String, String> config, ConnectorContext context)
    {
        Bootstrap bootstrap = new Bootstrap(new JustAskModule());
        return bootstrap
                .doNotInitializeLogging()
                .setRequiredConfigurationProperties(config)
                .initialize()
                .getInstance(JustAskConnector.class);
    }
}
