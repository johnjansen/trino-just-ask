package io.trino.plugin.justask;

import com.google.inject.Inject;
import io.trino.spi.connector.Connector;
import io.trino.spi.connector.ConnectorMetadata;
import io.trino.spi.connector.ConnectorRecordSetProvider;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplitManager;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.function.table.ConnectorTableFunction;
import io.trino.spi.transaction.IsolationLevel;

import java.util.Set;

public class JustAskConnector
        implements Connector
{
    private final Set<ConnectorTableFunction> tableFunctions;

    @Inject
    public JustAskConnector(Set<ConnectorTableFunction> tableFunctions)
    {
        this.tableFunctions = tableFunctions;
    }

    @Override
    public ConnectorTransactionHandle beginTransaction(IsolationLevel isolationLevel, boolean readOnly, boolean autoCommit)
    {
        return JustAskTransactionHandle.INSTANCE;
    }

    @Override
    public ConnectorMetadata getMetadata(ConnectorSession session, ConnectorTransactionHandle transactionHandle)
    {
        return new JustAskMetadata();
    }

    @Override
    public ConnectorSplitManager getSplitManager()
    {
        return new JustAskSplitManager();
    }

    @Override
    public ConnectorRecordSetProvider getRecordSetProvider()
    {
        return new JustAskRecordSetProvider();
    }

    @Override
    public Set<ConnectorTableFunction> getTableFunctions()
    {
        return tableFunctions;
    }
}
