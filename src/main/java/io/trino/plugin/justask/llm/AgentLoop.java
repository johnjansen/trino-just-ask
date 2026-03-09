package io.trino.plugin.justask.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.trino.plugin.justask.docs.DocReader;
import io.trino.spi.TrinoException;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.trino.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;

public class AgentLoop
{
    private static final Pattern SQL_CODE_BLOCK = Pattern.compile(
            "```(?:sql)?\\s*\\n(.*?)\\n\\s*```",
            Pattern.DOTALL);

    private final LlmClient llmClient;
    private final PromptTemplate promptTemplate;
    private final DocReader docReader;
    private final int maxToolCalls;

    public AgentLoop(LlmClient llmClient, PromptTemplate promptTemplate, DocReader docReader, int maxToolCalls)
    {
        this.llmClient = llmClient;
        this.promptTemplate = promptTemplate;
        this.docReader = docReader;
        this.maxToolCalls = maxToolCalls;
    }

    public String generateSql(String question, String catalog)
    {
        String systemPrompt = promptTemplate.render(catalog);

        String userMessage = question;
        if (catalog != null) {
            String index = docReader.readCatalogIndex(catalog);
            if (index != null) {
                userMessage = "Catalog documentation index:\n\n" + index + "\n\nQuestion: " + question;
            }
        }

        ObjectNode requestBody = llmClient.buildRequestBody(systemPrompt, userMessage);

        try {
            int toolCallCount = 0;
            while (toolCallCount < maxToolCalls) {
                JsonNode response = llmClient.sendRequest(requestBody);
                LlmClient.ParsedResponse parsed = LlmClient.parseResponse(response);

                if (!parsed.isToolCall()) {
                    return extractSql(parsed.textContent());
                }

                llmClient.appendAssistantMessage(requestBody, parsed.rawMessage());
                for (LlmClient.ToolCall toolCall : parsed.toolCalls()) {
                    toolCallCount++;
                    String result = handleToolCall(toolCall, catalog);
                    llmClient.appendToolResult(requestBody, toolCall.id(), toolCall.name(), result);
                }
            }

            throw new TrinoException(GENERIC_INTERNAL_ERROR,
                    "LLM agent exceeded maximum tool calls (" + maxToolCalls + ")");
        }
        catch (IOException | InterruptedException e) {
            throw new TrinoException(GENERIC_INTERNAL_ERROR, "LLM request failed: " + e.getMessage(), e);
        }
    }

    private String handleToolCall(LlmClient.ToolCall toolCall, String catalog)
    {
        if (!"read_doc".equals(toolCall.name())) {
            return "Unknown tool: " + toolCall.name();
        }

        String path = toolCall.arguments().get("path").asText();
        try {
            if (catalog != null) {
                return docReader.readDoc(catalog, path);
            }
            return "No catalog specified — cannot read documentation";
        }
        catch (IllegalArgumentException e) {
            return "Error reading document: " + e.getMessage();
        }
    }

    public static String extractSql(String response)
    {
        Matcher matcher = SQL_CODE_BLOCK.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return response.trim();
    }
}
