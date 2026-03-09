package io.trino.plugin.justask.llm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PromptTemplate
{
    private static final Pattern IF_CATALOG_BLOCK = Pattern.compile(
            "\\{\\{#if catalog}}(.*?)\\{\\{/if}}",
            Pattern.DOTALL);
    private static final Pattern CATALOG_VAR = Pattern.compile("\\{\\{catalog}}");

    private final String templateContent;

    public PromptTemplate(String templateFilePath)
    {
        try {
            this.templateContent = Files.readString(Path.of(templateFilePath));
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read prompt template: " + templateFilePath, e);
        }
    }

    public String render(String catalog)
    {
        String result = templateContent;

        Matcher matcher = IF_CATALOG_BLOCK.matcher(result);
        if (catalog != null) {
            result = matcher.replaceAll("$1");
        }
        else {
            result = matcher.replaceAll("");
        }

        if (catalog != null) {
            result = CATALOG_VAR.matcher(result).replaceAll(Matcher.quoteReplacement(catalog));
        }

        result = result.replaceAll("\n{3,}", "\n\n").trim();

        return result;
    }
}
