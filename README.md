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

To use Just Ask with your own catalogs, you need to:

1. Write markdown documentation describing your tables
2. Place it where the plugin can read it
3. Configure the paths in `justask.properties`

### Step 1: Write catalog documentation

Create a directory for your catalog with an `index.md` entry point. The LLM reads this first to discover what documentation is available, then uses its `read_doc` tool to fetch specific files on demand.

```
my-catalog-docs/
├── index.md              # Entry point — required
├── tables/
│   ├── users.md          # One file per table
│   ├── orders.md
│   └── products.md
├── concepts/
│   └── business-terms.md # Maps natural language to SQL patterns
└── examples/
    └── common-queries.md # Example queries the LLM can reference
```

Only `index.md` is required. The `tables/`, `concepts/`, and `examples/` directories are a recommended convention — you can organize files however you like as long as `index.md` links to them.

### Step 2: Write the index file

The index should briefly describe the catalog and list every documentation file with a relative link. The LLM uses these links with `read_doc` to pull in details on demand.

```markdown
# Sales Database

The `sales` catalog contains our production sales data.
Tables are at `sales.public.<table>`.

## Tables
- [users](tables/users.md) — User accounts and profiles
- [orders](tables/orders.md) — Purchase orders
- [products](tables/products.md) — Product catalog

## Concepts
- [Business terms](concepts/business-terms.md) — What "revenue", "churn", "MRR" mean in SQL

## Example Queries
- [Common queries](examples/common-queries.md)
```

### Step 3: Write table documentation

For each table, document the fully qualified name, columns, types, keys, and common join patterns. The more context you give, the better the LLM's SQL will be.

```markdown
# users

Schema: `sales.public.users`

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| email | VARCHAR | Unique email address |
| name | VARCHAR | Display name |
| plan | VARCHAR | Subscription plan: free, pro, enterprise |
| created_at | TIMESTAMP | Account creation date |
| country_code | VARCHAR | ISO 3166-1 alpha-2 country code |

## Primary Key
- `id`

## Relationships
- Referenced by `orders.user_id`

## Common Join Patterns

### User with order summary
​```sql
SELECT u.name, u.plan,
       COUNT(o.id) AS order_count,
       SUM(o.total) AS total_spent
FROM sales.public.users u
JOIN sales.public.orders o ON o.user_id = u.id
GROUP BY u.name, u.plan
​```
```

### Step 4: Write business terms (optional but recommended)

Business terms map natural language to SQL patterns. This dramatically improves accuracy for domain-specific questions.

```markdown
# Business Terms

- **"revenue"** → `SUM(o.total)`
- **"MRR"** → `SUM(o.total) / 12` for annual plans, `SUM(o.total)` for monthly
- **"churn"** → users with no orders in the last 90 days
- **"top" / "best"** → `ORDER BY <metric> DESC LIMIT N`
- **"active users"** → users with at least one order in the last 30 days
- **"by region"** → `GROUP BY u.country_code`
```

### Step 5: Deploy the documentation

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
