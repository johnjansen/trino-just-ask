# Trino Just Ask

Ask questions in natural language, get SQL results -- as a Trino table function.

`query()` returns the generated SQL. `ask()` generates _and_ executes it, returning the actual result set. Both work as standard table functions, composable with JOINs, WHERE clauses, and anything else Trino supports.

## Quick Start

1. **Build the plugin** (or grab a release JAR):

   ```bash
   mvn clean package
   ```

2. **Deploy** the JAR to your Trino `plugin/justask/` directory.

3. **Add catalog properties** at `etc/catalog/justask.properties`:

   ```properties
   connector.name=justask
   llm.endpoint=https://api.openai.com/v1
   llm.api-key=${ENV:OPENAI_API_KEY}
   llm.model=gpt-4o
   docs.base-dir=etc/justask/catalogs
   prompt.template-file=etc/justask/system-prompt.md
   executor.jdbc-url=jdbc:trino://localhost:8080
   ```

4. **Create catalog documentation** for any catalogs you want the LLM to know about (see [Catalog Documentation](#catalog-documentation) below).

5. **Restart Trino**.

## Usage Examples

```sql
-- Generate SQL from a question (returns a VARCHAR column with the SQL)
SELECT * FROM TABLE(justask.system.query('What are the top 10 customers by total order value?', 'tpch'))

-- Generate AND execute in one step (returns the actual result set)
SELECT * FROM TABLE(justask.system.ask('Show me revenue by nation for 1995', 'tpch'))

-- Unscoped — no catalog docs, LLM works from the question alone
SELECT * FROM TABLE(justask.system.query('SELECT 1'))

-- Composable with JOINs
SELECT a.*, r.name AS region_name
FROM TABLE(justask.system.ask('top 5 nations by order count', 'tpch')) a
JOIN tpch.sf1.region r ON a.regionkey = r.regionkey
```

- `query(question)` or `query(question, catalog)` -- returns a single-row, single-column result containing the generated SQL.
- `ask(question, catalog)` -- generates SQL via the LLM, then executes it against Trino and returns the result set.

## Configuration

All properties go in `etc/catalog/justask.properties`.

| Property | Default | Description |
|---|---|---|
| `connector.name` | _(required)_ | Must be `justask` |
| `llm.endpoint` | `https://api.openai.com/v1` | OpenAI-compatible chat completions endpoint |
| `llm.api-key` | _(required)_ | API key for the LLM provider |
| `llm.model` | `gpt-4o` | Model name to use |
| `llm.temperature` | `0.2` | Sampling temperature |
| `llm.max-tokens` | `4096` | Max tokens in LLM response |
| `llm.max-tool-calls` | `10` | Max agent loop iterations (prevents runaway tool-use) |
| `docs.base-dir` | `etc/justask/catalogs` | Root directory for catalog documentation |
| `prompt.template-file` | `etc/justask/system-prompt.md` | Path to the system prompt template |
| `executor.jdbc-url` | `jdbc:trino://localhost:8080` | JDBC URL used by `ask()` to execute generated SQL |

The `llm.endpoint` works with any OpenAI-compatible API: OpenAI, Azure OpenAI, Ollama, vLLM, etc.

## Catalog Documentation

Catalog docs tell the LLM about your data. Without them (unscoped mode), the LLM can only work from the question text. With them, it reads schema details, business concepts, and example queries on demand.

### Directory Structure

```
etc/justask/catalogs/<catalog-name>/
├── index.md              # Entry point — the LLM reads this first
├── tables/
│   ├── customers.md      # Table schema, columns, relationships
│   └── orders.md
├── concepts/
│   └── business-terms.md # Maps natural language to SQL patterns
└── examples/
    └── common-queries.md # Example queries the LLM can reference
```

### Index File

The `index.md` is the entry point. It should list all tables with links, and optionally reference concept and example files. The LLM reads this first to understand what documentation is available.

```markdown
# My Catalog

## Tables
- [customers](tables/customers.md) — Customer master data
- [orders](tables/orders.md) — Order history

## Concepts
- [Business terms](concepts/business-terms.md) — Maps "best", "top", "revenue" to query patterns

## Example Queries
- [Common queries](examples/common-queries.md)
```

### How the LLM Uses Docs

The LLM operates in an agent loop. When a catalog is specified, the LLM receives the `index.md` content and has access to a `read_doc` tool. It can call `read_doc(path)` to read any file referenced in the index (or referenced from other docs). This lets the LLM pull in only the documentation it needs for a given question, rather than stuffing everything into the prompt.

## Testing with TPC-H

The repo includes catalog documentation for Trino's built-in `tpch` connector, so you can test immediately.

1. **Enable the tpch catalog** in Trino (add `etc/catalog/tpch.properties`):

   ```properties
   connector.name=tpch
   ```

2. **Point `docs.base-dir`** to the included docs (this is the default):

   ```properties
   docs.base-dir=etc/justask/catalogs
   ```

   The included `etc/justask/catalogs/tpch/` directory has documentation for all TPC-H tables, business term mappings, and example queries.

3. **Try some questions**:

   ```sql
   SELECT * FROM TABLE(justask.system.ask('What are the top 5 nations by total revenue?', 'tpch'))
   SELECT * FROM TABLE(justask.system.ask('Show me the largest orders over $300,000', 'tpch'))
   SELECT * FROM TABLE(justask.system.ask('Which suppliers are in Europe?', 'tpch'))
   SELECT * FROM TABLE(justask.system.query('Average order value by market segment', 'tpch'))
   ```

## How It Works

1. User calls `query(question, catalog)` or `ask(question, catalog)`.
2. The system prompt template is loaded and rendered with the catalog name.
3. If a catalog is specified, the catalog's `index.md` is included in the initial messages.
4. The LLM enters an agent loop -- it can call `read_doc(path)` to read additional documentation files, then responds with more tool calls or a final answer.
5. SQL is extracted from the LLM's final response (expected in a `` ```sql `` code block).
6. For `query()`: the SQL string is returned as a single-column result.
7. For `ask()`: the SQL is executed against Trino via JDBC and the result set is returned directly.

## Building

```bash
mvn clean package
```

The build produces a plugin JAR. Deploy it to your Trino installation's `plugin/justask/` directory.
