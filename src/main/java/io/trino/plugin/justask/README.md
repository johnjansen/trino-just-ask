# justask — Source Overview

Core plugin package. Contains the Trino connector wiring and table function implementations.

## Plugin Lifecycle
- `JustAskPlugin` — Entry point. Registers the connector factory with Trino.
- `JustAskConnectorFactory` — Creates the connector using Guice bootstrap.
- `JustAskConnector` — Lightweight connector (no real data). Provides table functions.
- `JustAskModule` — Guice module binding all components.
- `JustAskConfig` — `@Config`-annotated properties (LLM endpoint, model, docs path, etc.).
- `JustAskMetadata` / `JustAskTransactionHandle` — Minimal connector metadata and transaction support.

## Table Functions
- `QueryFunction` + `QueryFunctionHandle` — `system.query(question, catalog)`: generates SQL via the LLM agent loop, returns it as a VARCHAR column.
- `AskFunction` + `AskFunctionHandle` — `system.ask(question, catalog)`: generates SQL then executes it, returns the actual result set with dynamic columns.

## Subpackages
- `llm/` — LLM client, agent loop, and prompt template.
- `docs/` — Documentation reader for per-catalog markdown files.
- `executor/` — JDBC-based SQL executor for the `ask()` function.
