package io.trino.plugin.justask.docs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DocReader
{
    private final Path baseDir;

    public DocReader(String baseDir)
    {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    public String readDoc(String catalog, String relativePath)
    {
        Path resolved = baseDir.resolve(catalog).resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Path traversal not allowed: " + relativePath);
        }
        if (!Files.exists(resolved)) {
            throw new IllegalArgumentException("Document not found: " + catalog + "/" + relativePath);
        }
        try {
            return Files.readString(resolved);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read document: " + resolved, e);
        }
    }

    public String readCatalogIndex(String catalog)
    {
        Path indexPath = baseDir.resolve(catalog).resolve("index.md").normalize();
        if (!Files.exists(indexPath)) {
            return null;
        }
        try {
            return Files.readString(indexPath);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read catalog index: " + indexPath, e);
        }
    }
}
