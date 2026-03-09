package io.trino.plugin.justask.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDocReader
{
    @TempDir
    Path tempDir;

    @Test
    void testReadExistingDoc() throws IOException
    {
        Path catalogDir = tempDir.resolve("my_catalog");
        Files.createDirectories(catalogDir);
        Files.writeString(catalogDir.resolve("index.md"), "# My Catalog\n\nSome docs.");

        DocReader reader = new DocReader(tempDir.toString());
        String content = reader.readDoc("my_catalog", "index.md");
        assertThat(content).isEqualTo("# My Catalog\n\nSome docs.");
    }

    @Test
    void testReadNestedDoc() throws IOException
    {
        Path tablesDir = tempDir.resolve("my_catalog/tables");
        Files.createDirectories(tablesDir);
        Files.writeString(tablesDir.resolve("users.md"), "# Users Table");

        DocReader reader = new DocReader(tempDir.toString());
        String content = reader.readDoc("my_catalog", "tables/users.md");
        assertThat(content).isEqualTo("# Users Table");
    }

    @Test
    void testReadNonexistentDocThrows()
    {
        DocReader reader = new DocReader(tempDir.toString());
        assertThatThrownBy(() -> reader.readDoc("my_catalog", "missing.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void testPathTraversalBlocked() throws IOException
    {
        Files.writeString(tempDir.resolve("secret.txt"), "secret");
        Path catalogDir = tempDir.resolve("my_catalog");
        Files.createDirectories(catalogDir);

        DocReader reader = new DocReader(tempDir.toString());
        assertThatThrownBy(() -> reader.readDoc("my_catalog", "../../secret.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testReadCatalogIndex() throws IOException
    {
        Path catalogDir = tempDir.resolve("sales");
        Files.createDirectories(catalogDir);
        Files.writeString(catalogDir.resolve("index.md"), "# Sales Catalog");

        DocReader reader = new DocReader(tempDir.toString());
        String content = reader.readCatalogIndex("sales");
        assertThat(content).isEqualTo("# Sales Catalog");
    }

    @Test
    void testReadCatalogIndexMissing()
    {
        DocReader reader = new DocReader(tempDir.toString());
        assertThat(reader.readCatalogIndex("nonexistent")).isNull();
    }
}
