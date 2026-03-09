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

    @Test
    void testBuildRequestBody()
    {
        LlmClient client = new LlmClient(
                "https://api.example.com/v1",
                "test-key",
                "gpt-4o",
                0.2,
                4096);

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
        assertThat(parsed.toolCalls().get(0).arguments().get("path").asText()).isEqualTo("tables/users.md");
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
    }
}
