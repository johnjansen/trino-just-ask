# executor — SQL Execution

Executes generated SQL against Trino via JDBC. Used by the `ask()` table function.

- `SqlExecutor` — Connects to Trino using a configured JDBC URL, executes a query, and returns a `SqlResult` containing column names, column types, and row data. Results are used by `AskFunction` to build the dynamic return type descriptor.
