package io.trino.plugin.justask;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.spi.connector.ConnectorTableHandle;

import java.util.List;

public class JustAskTableHandle
        implements ConnectorTableHandle
{
    private final List<String> columnNames;
    private final List<List<String>> rows;

    @JsonCreator
    public JustAskTableHandle(
            @JsonProperty("columnNames") List<String> columnNames,
            @JsonProperty("rows") List<List<String>> rows)
    {
        this.columnNames = columnNames;
        this.rows = rows;
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
