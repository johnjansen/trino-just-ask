package io.trino.plugin.justask;

import com.google.inject.Inject;
import io.airlift.slice.Slice;
import io.trino.plugin.justask.llm.AgentLoop;
import io.trino.spi.connector.ConnectorAccessControl;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.function.table.AbstractConnectorTableFunction;
import io.trino.spi.function.table.Argument;
import io.trino.spi.function.table.ReturnTypeSpecification;
import io.trino.spi.function.table.ScalarArgument;
import io.trino.spi.function.table.ScalarArgumentSpecification;
import io.trino.spi.function.table.TableFunctionAnalysis;

import java.util.List;
import java.util.Map;

import static io.trino.spi.function.table.Descriptor.descriptor;

import static io.trino.spi.type.VarcharType.VARCHAR;

public class QueryFunction
        extends AbstractConnectorTableFunction
{
    private final AgentLoop agentLoop;

    @Inject
    public QueryFunction(AgentLoop agentLoop)
    {
        super(
                "system",
                "query",
                List.of(
                        ScalarArgumentSpecification.builder()
                                .name("QUESTION")
                                .type(VARCHAR)
                                .build(),
                        ScalarArgumentSpecification.builder()
                                .name("CATALOG")
                                .type(VARCHAR)
                                .defaultValue(null)
                                .build()),
                new ReturnTypeSpecification.DescribedTable(
                        descriptor(
                                List.of("sql"),
                                List.of(VARCHAR))));
        this.agentLoop = agentLoop;
    }

    @Override
    public TableFunctionAnalysis analyze(
            ConnectorSession session,
            ConnectorTransactionHandle transaction,
            Map<String, Argument> arguments,
            ConnectorAccessControl accessControl)
    {
        Slice questionSlice = (Slice) ((ScalarArgument) arguments.get("QUESTION")).getValue();
        String question = questionSlice.toStringUtf8();

        String catalog = null;
        ScalarArgument catalogArg = (ScalarArgument) arguments.get("CATALOG");
        if (catalogArg.getValue() != null) {
            catalog = ((Slice) catalogArg.getValue()).toStringUtf8();
        }

        String generatedSql = agentLoop.generateSql(question, catalog);

        return TableFunctionAnalysis.builder()
                .handle(new QueryFunctionHandle(question, catalog, generatedSql))
                .build();
    }
}
