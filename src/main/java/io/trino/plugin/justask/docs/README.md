# docs — Documentation Reader

Reads per-catalog markdown documentation files from disk, used as the LLM's `read_doc` tool.

- `DocReader` — Resolves file paths relative to a base directory and catalog name. Reads markdown files and returns their content. Includes path traversal protection to prevent reading files outside the docs directory. Also provides `readCatalogIndex(catalog)` to load a catalog's `index.md` entry point.
