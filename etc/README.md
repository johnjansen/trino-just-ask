# Configuration

Trino plugin configuration files and catalog documentation.

- `catalog/justask.properties` — Trino catalog properties file. Deploy to your Trino `etc/catalog/` directory.
- `justask/system-prompt.md` — Configurable system prompt template with mustache-style variable substitution.
- `justask/catalogs/` — Per-catalog documentation directories. Each subdirectory contains markdown files that the LLM reads via its `read_doc` tool to understand your data before generating SQL.
