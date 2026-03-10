# Just Ask Trino!

Ask questions in natural language, get SQL results -- as a Trino table function.

`query()` returns the generated SQL. `ask()` generates _and_ executes it, returning the actual result set. Both work as standard table functions, composable with JOINs, WHERE clauses, and anything else Trino supports.

## Quick Start

1. **Build the plugin**:

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

4. **Create catalog documentation** for any catalogs you want the LLM to know about (see [Catalog Documentation](#catalog-documentation)).

5. **Restart Trino**.

## Examples

All examples below were tested against Trino's built-in `tpch` connector (sf1 scale) using Ollama with `glm-5:cloud`. Output varies between runs since the LLM generates SQL dynamically.

### Generate SQL with `query()`

Returns a single column `sql` containing the generated SQL:

```sql
SELECT * FROM TABLE(justask.system.query(
  'What are the top 5 nations by total order revenue?',
  'tpch'
));
```

```
SELECT n.name AS nation,
       SUM(l.extendedprice * (1 - l.discount)) AS total_revenue
FROM tpch.sf1.lineitem l
JOIN tpch.sf1.orders o ON l.orderkey = o.orderkey
JOIN tpch.sf1.customer c ON o.custkey = c.custkey
JOIN tpch.sf1.nation n ON c.nationkey = n.nationkey
GROUP BY n.name
ORDER BY total_revenue DESC
LIMIT 5
```

### Simple `ask()` — count with grouping

Generates SQL _and_ executes it, returning the result set directly:

```sql
SELECT * FROM TABLE(justask.system.ask(
  'How many customers are in each market segment?',
  'tpch'
));
```

```
 mktsegment | customer_count
------------+----------------
 HOUSEHOLD  | 30189
 BUILDING   | 30142
 FURNITURE  | 29968
 MACHINERY  | 29949
 AUTOMOBILE | 29752
(5 rows)
```

### Aggregation with `ask()`

```sql
SELECT * FROM TABLE(justask.system.ask(
  'Which 3 nations have the most suppliers?',
  'tpch'
));
```

```
 nation  | supplier_count
---------+----------------
 IRAQ    | 438
 PERU    | 421
 ALGERIA | 420
(3 rows)
```

### Unscoped mode — no catalog docs

Without a catalog argument, the LLM works from the question alone:

```sql
SELECT * FROM TABLE(justask.system.query(
  'Write a query to list all schemas in the tpch catalog'
));
```

```
              sql
-------------------------------
 SHOW SCHEMAS FROM tpch
(1 row)
```

### Composable with standard SQL

Table functions return regular result sets, so you can compose them:

```sql
-- Use ask() results in a subquery
SELECT * FROM TABLE(justask.system.ask(
  'top 5 nations by order count',
  'tpch'
)) WHERE order_count > 50000;

-- JOIN ask() with other tables
SELECT a.*, r.name AS region_name
FROM TABLE(justask.system.ask('top 5 nations by order count', 'tpch')) a
JOIN tpch.sf1.region r ON a.regionkey = r.regionkey;
```

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

### Using with Ollama

```properties
connector.name=justask
llm.endpoint=http://host.docker.internal:11434/v1
llm.api-key=unused
llm.model=glm-5:cloud
docs.base-dir=etc/justask/catalogs
prompt.template-file=etc/justask/system-prompt.md
executor.jdbc-url=jdbc:trino://localhost:8080
```

For Podman, use `host.containers.internal` instead of `host.docker.internal`.

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

   The included `etc/justask/catalogs/tpch/` directory has documentation for all 8 TPC-H tables, business term mappings, date handling guides, and example queries.

3. **Run the example script** (requires Docker or Podman):

   ```bash
   OPENAI_API_KEY=your-key ./examples/run.sh
   ```

   This builds the plugin, starts Trino in a container with the plugin and TPC-H catalog pre-configured, and runs several example queries.

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

The build produces a plugin JAR at `target/just-ask-trino-1.0-SNAPSHOT-jar-with-dependencies.jar`. Deploy it to your Trino installation's `plugin/justask/` directory.
