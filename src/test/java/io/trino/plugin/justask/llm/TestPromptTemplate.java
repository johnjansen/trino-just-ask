package io.trino.plugin.justask.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestPromptTemplate
{
    @TempDir
    Path tempDir;

    @Test
    void testRenderWithCatalog() throws IOException
    {
        Path templateFile = tempDir.resolve("prompt.md");
        Files.writeString(templateFile, "You are a SQL writer.\n{{#if catalog}}\nCatalog: {{catalog}}\n{{/if}}\nWrite SQL.");

        PromptTemplate template = new PromptTemplate(templateFile.toString());
        String result = template.render("sales");
        assertThat(result).contains("Catalog: sales");
        assertThat(result).contains("You are a SQL writer.");
        assertThat(result).contains("Write SQL.");
    }

    @Test
    void testRenderWithoutCatalog() throws IOException
    {
        Path templateFile = tempDir.resolve("prompt.md");
        Files.writeString(templateFile, "You are a SQL writer.\n{{#if catalog}}\nCatalog: {{catalog}}\n{{/if}}\nWrite SQL.");

        PromptTemplate template = new PromptTemplate(templateFile.toString());
        String result = template.render(null);
        assertThat(result).doesNotContain("Catalog:");
        assertThat(result).contains("You are a SQL writer.");
    }
}
