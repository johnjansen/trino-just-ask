# Trino Just Ask — NL2SQL Function Plugin Design

## Overview

A config-driven Trino function plugin that converts natural language questions to SQL and optionally executes them. Uses an OpenAI-compatible LLM with tool-use to read per-catalog documentation on demand.

## User-Facing SQL Interface

Three table functions registered via `FunctionProvider` SPI:

```sql
-- Unscoped: LLM infers from question alone
SELECT * FROM TABLE(justask.query('show me top 10 customers by revenue'))

-- Scoped: LLM gets catalog documentation context
SELECT * FROM TABLE(justask.query('show me top 10 customers by revenue', 'sales_catalog'))

-- Ask: generates AND executes the SQL, returns actual results
SELECT * FROM TABLE(justask.ask('show me top 10 customers by revenue', 'sales_catalog'))

-- Composable
SELECT a.*, b.region
FROM TABLE(justask.ask('top customers by revenue', 'sales')) a
JOIN geo.regions b ON a.customer_id = b.customer_id
```

- `query()` returns a single VARCHAR column containing the generated SQL.
- `ask()` returns the actual result set from executing the generated SQL.

## Architecture

Function plugin (not connector). Three functions forming a pipeline:

```
ask(question, [catalog])
  └─> query(question, [catalog])    -- generates SQL via LLM
        └─> nl2sql scoped/unscoped  -- agent loop with doc reading
  └─> SqlExecutor                   -- executes generated SQL, returns results
```

### Components

```
src/main/java/io/trino/plugin/justask/
├── JustAskPlugin.java                 # implements Plugin, registers FunctionProvider
├── JustAskFunctionProvider.java       # provides table functions to Trino
├── JustAskConfig.java                 # @Config annotated properties
├── QueryFunction.java                 # query() table function — returns SQL
├── AskFunction.java                   # ask() table function — returns results
├── llm/
│   ├── LlmClient.java                # OpenAI-compatible chat completions
│   ├── AgentLoop.java                 # Tool-use conversation loop
│   └── PromptTemplate.java           # Template loading & rendering
├── docs/
│   └── DocReader.java                 # Reads markdown files for LLM tool
└── executor/
    └── SqlExecutor.java               # JDBC-based query execution for ask()
```

## LLM Agent Loop

1. System prompt loaded from configurable template file. Sets role as Trino SQL writer.
2. If scoped (catalog provided): catalog's `index.md` content included in initial messages.
3. If unscoped: no docs provided, LLM works from question alone.
4. Tool-use loop: LLM can call `read_doc(path)` to read markdown files referenced from index or other docs. Loop continues until LLM returns final text response containing SQL.
5. SQL extracted from final response (expected in a ```sql code block).
6. Max iterations capped (configurable, default 10) to prevent runaway loops.

```
System prompt + user question [+ index.md if scoped]
  -> LLM response (tool call or final answer)
    -> if tool call: read_doc(path) -> return content -> loop
    -> if final answer: extract SQL -> done
```

## Configuration

### `etc/justask.properties`

```properties
# LLM settings
llm.endpoint=https://api.openai.com/v1
llm.api-key=${ENV:OPENAI_API_KEY}
llm.model=gpt-4o
llm.temperature=0.2
llm.max-tokens=4096
llm.max-tool-calls=10

# Paths
docs.base-dir=etc/justask/catalogs
prompt.template-file=etc/justask/system-prompt.md

# SQL execution (for ask())
executor.jdbc-url=jdbc:trino://localhost:8080
```

### `etc/justask/system-prompt.md`

Configurable template with variable substitution:

```markdown
You are a SQL query writer for Trino.
Given a user's natural language question, write a single valid Trino SQL query.

{{#if catalog}}
You have access to documentation for the "{{catalog}}" catalog.
Use the read_doc tool to read files referenced in the documentation.
{{/if}}

Return ONLY the SQL query in a ```sql code block.
```

### Per-Catalog Documentation

```
etc/justask/catalogs/<catalog-name>/
├── index.md          # entry point, references other files
├── tables/
│   ├── customers.md  # table schema, purpose, relationships
│   └── orders.md
├── concepts/
│   └── best.md       # maps "best" to query patterns
└── examples/
    └── revenue.md    # example queries
```

## Key Design Decisions

1. **Function plugin, not connector** — uses `FunctionProvider` SPI for table functions
2. **OpenAI-compatible API** — works with any provider (OpenAI, Anthropic via proxy, Ollama, etc.)
3. **Agent loop with single `read_doc` tool** — LLM pulls docs on demand, files manually cross-referenced
4. **Configurable system prompt template** — users tune behavior without code changes
5. **`query()` returns SQL as VARCHAR, `ask()` returns executed result set**
6. **Errors throw** — standard Trino error propagation, no retry
7. **Scoped vs unscoped** — scoped loads catalog index.md, unscoped gets no docs
8. **Config via properties file** — idiomatic Trino style

## Build

- Java, Maven
- Targets latest Trino version
- Produces a plugin JAR for deployment to `plugin/justask/`
