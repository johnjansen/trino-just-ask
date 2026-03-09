package io.trino.plugin.justask;

import io.trino.spi.connector.ConnectorTransactionHandle;

public enum JustAskTransactionHandle
        implements ConnectorTransactionHandle
{
    INSTANCE
}
