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

## Using Your Own Data

### Generate documentation automatically

The included script connects to Trino, introspects your catalog's schemas and tables, then uses an LLM to generate complete documentation — table descriptions, primary/foreign keys, relationships, join patterns, business terms, and example queries.

```bash
# Using local Ollama (default)
./scripts/generate-catalog-docs.sh my_catalog

# Specify schema, output directory, and Trino host
./scripts/generate-catalog-docs.sh -s public -o docs/my_catalog -h trino.internal my_catalog

# Using OpenAI
./scripts/generate-catalog-docs.sh \
    --llm-endpoint https://api.openai.com/v1 \
    --llm-key sk-... \
    --llm-model gpt-4o \
    my_catalog
```

This generates:

```
etc/justask/catalogs/my_catalog/
├── index.md                    # Catalog overview with table links
├── tables/
│   ├── users.md                # Columns, keys, relationships, join patterns
│   ├── orders.md
│   └── ...
├── concepts/
│   └── business-terms.md       # Natural language → SQL mappings
└── examples/
    └── common-queries.md       # Ready-to-use example queries
```

The LLM infers keys, relationships, and business terms from column names and types. Review the output and refine anything domain-specific — the generated docs are a solid starting point but you know your data best.

Run `./scripts/generate-catalog-docs.sh --help` for all options.

### Manual documentation

You can also write docs by hand or edit the generated ones. Only `index.md` is required — it must list all documentation files as relative links so the LLM's `read_doc` tool can find them. See `etc/justask/catalogs/tpch/` for a complete hand-written example.

### Deploy the documentation

The `docs.base-dir` property points to a directory containing one subdirectory per catalog. The subdirectory name must match the catalog name you pass to `query()` or `ask()`.

```
<docs.base-dir>/
├── sales/           # matches ask('...', 'sales')
│   ├── index.md
│   └── tables/
├── analytics/       # matches ask('...', 'analytics')
│   ├── index.md
│   └── tables/
```

#### Bare metal / VM

Place docs anywhere on the Trino server filesystem and set `docs.base-dir` to that path:

```properties
docs.base-dir=/opt/trino/justask/catalogs
```

#### Docker / Podman

Mount your docs directory into the container. The plugin reads docs from inside the container, so `docs.base-dir` must be the container path:

```properties
# In justask.properties (container path):
docs.base-dir=/etc/trino/justask/catalogs
```

```bash
# Mount when starting the container:
docker run -d \
    -v /path/to/my-docs:/etc/trino/justask/catalogs \
    -v /path/to/system-prompt.md:/etc/trino/justask/system-prompt.md \
    -v /path/to/just-ask-trino.jar:/usr/lib/trino/plugin/justask/just-ask-trino.jar \
    -p 8080:8080 \
    trinodb/trino:473
```

The system prompt template (`prompt.template-file`) also needs to be accessible at the configured path inside the container. The repo includes a default at `etc/justask/system-prompt.md`.

### How the LLM uses docs

The LLM operates in an agent loop. When a catalog is specified:

1. The `index.md` content is included in the initial prompt
2. The LLM has a `read_doc` tool it can call with a relative path (e.g. `read_doc("tables/users.md")`)
3. The LLM reads only the files it needs for the question, keeping context focused
4. After gathering enough context, it generates SQL

This means you can document dozens of tables without bloating every request — the LLM only reads what's relevant.

## Testing with TPC-H

The repo includes complete catalog documentation for Trino's built-in `tpch` connector at `etc/justask/catalogs/tpch/`. This is a good reference when writing docs for your own catalogs — it covers all 8 TPC-H tables, business term mappings, date handling guides, and example queries.

**Run the example script** (requires Docker or Podman):

```bash
OPENAI_API_KEY=your-key ./examples/run.sh
```

This builds the plugin, starts Trino in a container with the plugin and TPC-H catalog pre-configured, and runs several example queries. For Ollama, set the endpoint in `examples/trino-config/catalog/justask.properties` before running.

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
