package io.trino.plugin.justask;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.spi.function.table.ConnectorTableFunctionHandle;

public class AskFunctionHandle
        implements ConnectorTableFunctionHandle
{
    private final String question;
    private final String catalog;
    private final String generatedSql;

    @JsonCreator
    public AskFunctionHandle(
            @JsonProperty("question") String question,
            @JsonProperty("catalog") String catalog,
            @JsonProperty("generatedSql") String generatedSql)
    {
        this.question = question;
        this.catalog = catalog;
        this.generatedSql = generatedSql;
    }

    @JsonProperty
    public String getQuestion()
    {
        return question;
    }

    @JsonProperty
    public String getCatalog()
    {
        return catalog;
    }

    @JsonProperty
    public String getGeneratedSql()
    {
        return generatedSql;
    }
}
