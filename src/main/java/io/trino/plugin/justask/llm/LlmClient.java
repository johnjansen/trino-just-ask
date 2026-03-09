package io.trino.plugin.justask.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class LlmClient
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final HttpClient httpClient;

    public LlmClient(String endpoint, String apiKey, String model, double temperature, int maxTokens)
    {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.httpClient = HttpClient.newHttpClient();
    }

    public ObjectNode buildRequestBody(String systemPrompt, String userMessage)
    {
        ObjectNode request = MAPPER.createObjectNode();
        request.put("model", model);
        request.put("temperature", temperature);
        request.put("max_tokens", maxTokens);

        ArrayNode messages = request.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userMessage);

        ArrayNode tools = request.putArray("tools");
        ObjectNode readDocTool = tools.addObject();
        readDocTool.put("type", "function");
        ObjectNode function = readDocTool.putObject("function");
        function.put("name", "read_doc");
        function.put("description", "Read the contents of a documentation file at the specified path");
        ObjectNode parameters = function.putObject("parameters");
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ObjectNode pathProp = properties.putObject("path");
        pathProp.put("type", "string");
        pathProp.put("description", "Relative path to the documentation file (e.g., 'tables/users.md')");
        parameters.putArray("required").add("path");
        parameters.put("additionalProperties", false);

        return request;
    }

    public JsonNode sendRequest(ObjectNode requestBody) throws IOException, InterruptedException
    {
        String body = MAPPER.writeValueAsString(requestBody);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("LLM API returned status " + response.statusCode() + ": " + response.body());
        }
        return MAPPER.readTree(response.body());
    }

    public void appendAssistantMessage(ObjectNode requestBody, JsonNode assistantMessage)
    {
        ArrayNode messages = (ArrayNode) requestBody.get("messages");
        messages.add(assistantMessage);
    }

    public void appendToolResult(ObjectNode requestBody, String toolCallId, String name, String content)
    {
        ArrayNode messages = (ArrayNode) requestBody.get("messages");
        ObjectNode toolMessage = messages.addObject();
        toolMessage.put("role", "tool");
        toolMessage.put("tool_call_id", toolCallId);
        toolMessage.put("name", name);
        toolMessage.put("content", content);
    }

    public static ParsedResponse parseResponse(JsonNode response)
    {
        JsonNode message = response.get("choices").get(0).get("message");
        String finishReason = response.get("choices").get(0).get("finish_reason").asText();

        if ("tool_calls".equals(finishReason) && message.has("tool_calls")) {
            List<ToolCall> toolCalls = new ArrayList<>();
            for (JsonNode tc : message.get("tool_calls")) {
                String id = tc.get("id").asText();
                String name = tc.get("function").get("name").asText();
                try {
                    JsonNode arguments = MAPPER.readTree(tc.get("function").get("arguments").asText());
                    toolCalls.add(new ToolCall(id, name, arguments));
                }
                catch (IOException e) {
                    throw new RuntimeException("Failed to parse tool call arguments", e);
                }
            }
            return new ParsedResponse(true, null, toolCalls, message);
        }

        String content = message.has("content") && !message.get("content").isNull()
                ? message.get("content").asText()
                : "";
        return new ParsedResponse(false, content, List.of(), message);
    }

    public record ToolCall(String id, String name, JsonNode arguments) {}

    public record ParsedResponse(
            boolean isToolCall,
            String textContent,
            List<ToolCall> toolCalls,
            JsonNode rawMessage) {}
}
