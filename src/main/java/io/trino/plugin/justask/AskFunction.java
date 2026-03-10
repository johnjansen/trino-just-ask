package io.trino.plugin.justask;

import com.google.inject.Inject;
import io.airlift.slice.Slice;
import io.trino.plugin.justask.executor.SqlExecutor;
import io.trino.plugin.justask.llm.AgentLoop;
import io.trino.spi.connector.ConnectorAccessControl;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.function.table.AbstractConnectorTableFunction;
import io.trino.spi.function.table.Argument;
import io.trino.spi.function.table.ReturnTypeSpecification.GenericTable;
import io.trino.spi.function.table.ScalarArgument;
import io.trino.spi.function.table.ScalarArgumentSpecification;
import io.trino.spi.function.table.TableFunctionAnalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.trino.spi.function.table.Descriptor.descriptor;
import static io.trino.spi.type.VarcharType.VARCHAR;

public class AskFunction
        extends AbstractConnectorTableFunction
{
    private final AgentLoop agentLoop;
    private final SqlExecutor sqlExecutor;

    @Inject
    public AskFunction(AgentLoop agentLoop, SqlExecutor sqlExecutor)
    {
        super(
                "system",
                "ask",
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
                GenericTable.GENERIC_TABLE);
        this.agentLoop = agentLoop;
        this.sqlExecutor = sqlExecutor;
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
        SqlExecutor.SqlResult result = sqlExecutor.execute(generatedSql);

        // Convert all values to strings for storage in the handle
        List<List<String>> stringRows = new ArrayList<>();
        for (List<Object> row : result.rows()) {
            List<String> stringRow = new ArrayList<>();
            for (Object value : row) {
                stringRow.add(value != null ? value.toString() : null);
            }
            stringRows.add(stringRow);
        }

        List<String> columnNames = result.columnNames();
        io.trino.spi.function.table.Descriptor returnedType = descriptor(
                columnNames,
                columnNames.stream().map(name -> (io.trino.spi.type.Type) VARCHAR).collect(Collectors.toList()));

        return TableFunctionAnalysis.builder()
                .returnedType(returnedType)
                .handle(new AskFunctionHandle(question, catalog, generatedSql, columnNames, stringRows))
                .build();
    }
}
