package io.trino.plugin.justask.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestLlmClient
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmClient createClient()
    {
        return new LlmClient(
                "https://api.example.com/v1",
                "test-key",
                "gpt-4o",
                0.2,
                4096);
    }

    @Test
    void testBuildRequestBody()
    {
        LlmClient client = createClient();
        ObjectNode request = client.buildRequestBody("You are a SQL writer.", "What tables exist?");

        assertThat(request.get("model").asText()).isEqualTo("gpt-4o");
        assertThat(request.get("temperature").asDouble()).isEqualTo(0.2);
        assertThat(request.get("max_tokens").asInt()).isEqualTo(4096);

        ArrayNode messages = (ArrayNode) request.get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("system");
        assertThat(messages.get(0).get("content").asText()).isEqualTo("You are a SQL writer.");
        assertThat(messages.get(1).get("role").asText()).isEqualTo("user");

        ArrayNode tools = (ArrayNode) request.get("tools");
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).get("function").get("name").asText()).isEqualTo("read_doc");
    }

    @Test
    void testBuildRequestBodyToolSchema()
    {
        LlmClient client = createClient();
        ObjectNode request = client.buildRequestBody("system", "user");

        JsonNode tool = request.get("tools").get(0);
        assertThat(tool.get("type").asText()).isEqualTo("function");

        JsonNode function = tool.get("function");
        assertThat(function.get("name").asText()).isEqualTo("read_doc");
        assertThat(function.get("description").asText()).contains("documentation file");

        JsonNode params = function.get("parameters");
        assertThat(params.get("type").asText()).isEqualTo("object");
        assertThat(params.get("properties").has("path")).isTrue();
        assertThat(params.get("required").get(0).asText()).isEqualTo("path");
    }

    @Test
    void testParseToolCallResponse() throws Exception
    {
        String responseJson = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [{
                        "id": "call_123",
                        "type": "function",
                        "function": {
                          "name": "read_doc",
                          "arguments": "{\\"path\\": \\"tables/users.md\\"}"
                        }
                      }]
                    },
                    "finish_reason": "tool_calls"
                  }]
                }
                """;

        JsonNode response = MAPPER.readTree(responseJson);
        LlmClient.ParsedResponse parsed = LlmClient.parseResponse(response);

        assertThat(parsed.isToolCall()).isTrue();
        assertThat(parsed.toolCalls()).hasSize(1);
        assertThat(parsed.toolCalls().get(0).name()).isEqualTo("read_doc");
        assertThat(parsed.toolCalls().get(0).id()).isEqualTo("call_123");
        assertThat(parsed.toolCalls().get(0).arguments().get("path").asText()).isEqualTo("tables/users.md");
    }

    @Test
    void testParseMultipleToolCalls() throws Exception
    {
        String responseJson = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [
                        {
                          "id": "call_1",
                          "type": "function",
                          "function": {
                            "name": "read_doc",
                            "arguments": "{\\"path\\": \\"tables/a.md\\"}"
                          }
                        },
                        {
                          "id": "call_2",
                          "type": "function",
                          "function": {
                            "name": "read_doc",
                            "arguments": "{\\"path\\": \\"tables/b.md\\"}"
                          }
                        }
                      ]
                    },
                    "finish_reason": "tool_calls"
                  }]
                }
                """;

        JsonNode response = MAPPER.readTree(responseJson);
        LlmClient.ParsedResponse parsed = LlmClient.parseResponse(response);

        assertThat(parsed.isToolCall()).isTrue();
        assertThat(parsed.toolCalls()).hasSize(2);
        assertThat(parsed.toolCalls().get(0).id()).isEqualTo("call_1");
        assertThat(parsed.toolCalls().get(1).id()).isEqualTo("call_2");
    }

    @Test
    void testParseTextResponse() throws Exception
    {
        String responseJson = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "```sql\\nSELECT * FROM users\\n```"
                    },
                    "finish_reason": "stop"
                  }]
                }
                """;

        JsonNode response = MAPPER.readTree(responseJson);
        LlmClient.ParsedResponse parsed = LlmClient.parseResponse(response);

        assertThat(parsed.isToolCall()).isFalse();
        assertThat(parsed.textContent()).contains("SELECT * FROM users");
        assertThat(parsed.toolCalls()).isEmpty();
    }

    @Test
    void testParseResponseNullContent() throws Exception
    {
        String responseJson = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": null
                    },
                    "finish_reason": "stop"
                  }]
                }
                """;

        JsonNode response = MAPPER.readTree(responseJson);
        LlmClient.ParsedResponse parsed = LlmClient.parseResponse(response);

        assertThat(parsed.isToolCall()).isFalse();
        assertThat(parsed.textContent()).isEmpty();
    }

    @Test
    void testAppendAssistantMessage()
    {
        LlmClient client = createClient();
        ObjectNode request = client.buildRequestBody("system", "user");

        ObjectNode assistantMsg = MAPPER.createObjectNode();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "Here is the SQL");

        client.appendAssistantMessage(request, assistantMsg);

        ArrayNode messages = (ArrayNode) request.get("messages");
        assertThat(messages).hasSize(3);
        assertThat(messages.get(2).get("role").asText()).isEqualTo("assistant");
        assertThat(messages.get(2).get("content").asText()).isEqualTo("Here is the SQL");
    }

    @Test
    void testAppendToolResult()
    {
        LlmClient client = createClient();
        ObjectNode request = client.buildRequestBody("system", "user");

        client.appendToolResult(request, "call_123", "read_doc", "# Users Table\n...");

        ArrayNode messages = (ArrayNode) request.get("messages");
        assertThat(messages).hasSize(3);

        JsonNode toolMsg = messages.get(2);
        assertThat(toolMsg.get("role").asText()).isEqualTo("tool");
        assertThat(toolMsg.get("tool_call_id").asText()).isEqualTo("call_123");
        assertThat(toolMsg.get("name").asText()).isEqualTo("read_doc");
        assertThat(toolMsg.get("content").asText()).isEqualTo("# Users Table\n...");
    }

    @Test
    void testAppendMultipleToolResults()
    {
        LlmClient client = createClient();
        ObjectNode request = client.buildRequestBody("system", "user");

        client.appendToolResult(request, "call_1", "read_doc", "content1");
        client.appendToolResult(request, "call_2", "read_doc", "content2");

        ArrayNode messages = (ArrayNode) request.get("messages");
        assertThat(messages).hasSize(4);
        assertThat(messages.get(2).get("tool_call_id").asText()).isEqualTo("call_1");
        assertThat(messages.get(3).get("tool_call_id").asText()).isEqualTo("call_2");
    }
}
