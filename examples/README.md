# Examples

Run the plugin end-to-end with Docker and Trino's built-in TPC-H dataset.

## Prerequisites

- Docker and Docker Compose
- Maven and Java 21+
- An OpenAI API key (or any OpenAI-compatible endpoint)

## Quick Run

```bash
OPENAI_API_KEY=sk-... ./examples/run.sh
```

This will:
1. Build the plugin JAR
2. Copy TPC-H catalog docs into the Trino config
3. Start Trino in Docker with the plugin loaded
4. Wait for Trino to be ready
5. Run three example queries demonstrating `query()` and `ask()`

## Manual Usage

After running the script, Trino stays running at `localhost:8080`. Connect interactively:

```bash
cd examples
docker compose exec trino trino
```

Then try:

```sql
-- See what SQL the LLM generates
SELECT * FROM TABLE(justask.system.query('top 10 customers by lifetime spend', 'tpch'));

-- Generate and execute in one step
SELECT * FROM TABLE(justask.system.ask('total revenue by region', 'tpch'));

-- Join AI results with other data
SELECT a.*, r.name AS region_name
FROM TABLE(justask.system.ask('top 5 nations by order count', 'tpch')) a
JOIN tpch.sf1.region r ON a.regionkey = r.regionkey;
```

## Teardown

```bash
cd examples
docker compose down
```

## Using a Different LLM Provider

Edit `trino-config/catalog/justask.properties` to point at any OpenAI-compatible endpoint:

```properties
# Ollama
llm.endpoint=http://host.docker.internal:11434/v1
llm.api-key=unused
llm.model=llama3

# Azure OpenAI
llm.endpoint=https://your-resource.openai.azure.com/openai/deployments/gpt-4o
llm.api-key=your-azure-key
```
