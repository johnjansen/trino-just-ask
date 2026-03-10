package io.trino.plugin.justask;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.spi.connector.ColumnHandle;

public class JustAskColumnHandle
        implements ColumnHandle
{
    private final String name;
    private final int index;

    @JsonCreator
    public JustAskColumnHandle(
            @JsonProperty("name") String name,
            @JsonProperty("index") int index)
    {
        this.name = name;
        this.index = index;
    }

    @JsonProperty
    public String getName()
    {
        return name;
    }

    @JsonProperty
    public int getIndex()
    {
        return index;
    }
}
