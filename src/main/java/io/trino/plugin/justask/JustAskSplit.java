package io.trino.plugin.justask;

import io.trino.spi.connector.ConnectorSplit;

import java.util.Map;

public enum JustAskSplit
        implements ConnectorSplit
{
    INSTANCE;

    @Override
    public Map<String, String> getSplitInfo()
    {
        return Map.of();
    }
}
