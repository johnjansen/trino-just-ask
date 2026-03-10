package io.trino.plugin.justask;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.spi.function.table.ConnectorTableFunctionHandle;

import java.util.List;

public class AskFunctionHandle
        implements ConnectorTableFunctionHandle
{
    private final String question;
    private final String catalog;
    private final String generatedSql;
    private final List<String> columnNames;
    private final List<List<String>> rows;

    @JsonCreator
    public AskFunctionHandle(
            @JsonProperty("question") String question,
            @JsonProperty("catalog") String catalog,
            @JsonProperty("generatedSql") String generatedSql,
            @JsonProperty("columnNames") List<String> columnNames,
            @JsonProperty("rows") List<List<String>> rows)
    {
        this.question = question;
        this.catalog = catalog;
        this.generatedSql = generatedSql;
        this.columnNames = columnNames;
        this.rows = rows;
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

    @JsonProperty
    public List<String> getColumnNames()
    {
        return columnNames;
    }

    @JsonProperty
    public List<List<String>> getRows()
    {
        return rows;
    }
}
