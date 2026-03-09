# llm — LLM Client and Agent Loop

Handles communication with the OpenAI-compatible LLM API and manages the tool-use conversation loop.

- `LlmClient` — HTTP client for chat completions. Builds request bodies with the `read_doc` tool definition, sends requests, and parses responses (text or tool calls).
- `AgentLoop` — Orchestrates the multi-turn conversation. Sends the system prompt and user question, processes `read_doc` tool calls by delegating to `DocReader`, and loops until the LLM produces a final SQL response. Enforces a max tool call limit.
- `PromptTemplate` — Loads and renders the system prompt template file with mustache-style `{{catalog}}` and `{{#if catalog}}` variable substitution.
