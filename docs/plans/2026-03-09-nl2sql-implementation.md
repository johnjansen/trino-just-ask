# Trino Just Ask — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a config-driven Trino connector plugin that converts natural language to SQL via an OpenAI-compatible LLM agent with doc-reading tool use.

**Architecture:** Lightweight Trino connector (no real data) that registers table functions `query()` and `ask()`. An LLM agent loop with `read_doc` tool reads per-catalog markdown documentation to build context before generating SQL. `ask()` additionally executes the generated SQL via JDBC.

**Tech Stack:** Java 21, Maven, Trino SPI (latest), Jackson for JSON, java.net.http.HttpClient for LLM API calls, Trino JDBC driver for SQL execution.

---

### Task 1: Maven Project Skeleton

**Files:**
- Create: `pom.xml`
- Create: `src/main/resources/META-INF/services/io.trino.spi.Plugin`

**Step 1: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.trino.plugin</groupId>
    <artifactId>trino-just-ask</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>trino-plugin</packaging>

    <name>Trino Just Ask</name>
    <description>Natural language to SQL plugin for Trino</description>

    <parent>
        <groupId>io.trino</groupId>
        <artifactId>trino-root</artifactId>
        <version>473</version>
    </parent>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <trino.version>473</trino.version>
        <dep.airlift.version>247</dep.airlift.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.trino</groupId>
            <artifactId>trino-spi</artifactId>
            <version>${trino.version}</version>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>io.airlift</groupId>
            <artifactId>configuration</artifactId>
            <version>${dep.airlift.version}</version>
        </dependency>

        <dependency>
            <groupId>io.airlift</groupId>
            <artifactId>bootstrap</artifactId>
            <version>${dep.airlift.version}</version>
        </dependency>

        <dependency>
            <groupId>com.google.inject</groupId>
            <artifactId>guice</artifactId>
        </dependency>

        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
        </dependency>

        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-core</artifactId>
        </dependency>

        <dependency>
            <groupId>io.trino</groupId>
            <artifactId>trino-jdbc</artifactId>
            <version>${trino.version}</version>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>io.trino</groupId>
            <artifactId>trino-testing</artifactId>
            <version>${trino.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>io.trino</groupId>
                <artifactId>trino-maven-plugin</artifactId>
                <version>14</version>
                <extensions>true</extensions>
            </plugin>
        </plugins>
    </build>
</project>
```

**Step 2: Create SPI service file**

Create `src/main/resources/META-INF/services/io.trino.spi.Plugin` with content:
```
io.trino.plugin.justask.JustAskPlugin
```

**Step 3: Verify project compiles**

Run: `mvn compile`
Expected: BUILD FAILURE (class not found yet — that's fine, confirms Maven setup works)

**Step 4: Commit**

```bash
git init
git add pom.xml src/main/resources/META-INF/services/io.trino.spi.Plugin
git commit -m "feat: initialize Maven project skeleton with Trino SPI dependencies"
```

---

### Task 2: Plugin + Connector + Config Wiring

**Files:**
- Create: `src/main/java/io/trino/plugin/justask/JustAskPlugin.java`
- Create: `src/main/java/io/trino/plugin/justask/JustAskConnectorFactory.java`
- Create: `src/main/java/io/trino/plugin/justask/JustAskConnector.java`
- Create: `src/main/java/io/trino/plugin/justask/JustAskModule.java`
- Create: `src/main/java/io/trino/plugin/justask/JustAskConfig.java`
- Create: `src/main/java/io/trino/plugin/justask/JustAskMetadata.java`
- Create: `src/main/java/io/trino/plugin/justask/JustAskTransactionHandle.java`

**Step 1: Create JustAskConfig**

```java
package io.trino.plugin.justask;

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.configuration.ConfigSecuritySensitive;

import jakarta.validation.constraints.NotNull;

public class JustAskConfig
{
    private String llmEndpoint = "https://api.openai.com/v1";
    private String llmApiKey;
    private String llmModel = "gpt-4o";
    private double llmTemperature = 0.2;
    private int llmMaxTokens = 4096;
    private int llmMaxToolCalls = 10;
    private String docsBaseDir = "etc/justask/catalogs";
    private String promptTemplateFile = "etc/justask/system-prompt.md";
    private String executorJdbcUrl = "jdbc:trino://localhost:8080";

    @NotNull
    public String getLlmEndpoint()
    {
        return llmEndpoint;
    }

    @Config("llm.endpoint")
    @ConfigDescription("Base URL for OpenAI-compatible API")
    public JustAskConfig setLlmEndpoint(String llmEndpoint)
    {
        this.llmEndpoint = llmEndpoint;
        return this;
    }

    @NotNull
    public String getLlmApiKey()
    {
        return llmApiKey;
    }

    @Config("llm.api-key")
    @ConfigDescription("API key for the LLM provider")
    @ConfigSecuritySensitive
    public JustAskConfig setLlmApiKey(String llmApiKey)
    {
        this.llmApiKey = llmApiKey;
        return this;
    }

    @NotNull
    public String getLlmModel()
    {
        return llmModel;
    }

    @Config("llm.model")
    @ConfigDescription("Model identifier to use")
    public JustAskConfig setLlmModel(String llmModel)
    {
        this.llmModel = llmModel;
        return this;
    }

    public double getLlmTemperature()
    {
        return llmTemperature;
    }

    @Config("llm.temperature")
    public JustAskConfig setLlmTemperature(double llmTemperature)
    {
        this.llmTemperature = llmTemperature;
        return this;
    }

    public int getLlmMaxTokens()
    {
        return llmMaxTokens;
    }

    @Config("llm.max-tokens")
    public JustAskConfig setLlmMaxTokens(int llmMaxTokens)
    {
        this.llmMaxTokens = llmMaxTokens;
        return this;
    }

    public int getLlmMaxToolCalls()
    {
        return llmMaxToolCalls;
    }

    @Config("llm.max-tool-calls")
    @ConfigDescription("Maximum number of tool calls per agent loop")
    public JustAskConfig setLlmMaxToolCalls(int llmMaxToolCalls)
    {
        this.llmMaxToolCalls = llmMaxToolCalls;
        return this;
    }

    @NotNull
    public String getDocsBaseDir()
    {
        return docsBaseDir;
    }

    @Config("docs.base-dir")
    @ConfigDescription("Base directory for per-catalog documentation")
    public JustAskConfig setDocsBaseDir(String docsBaseDir)
    {
        this.docsBaseDir = docsBaseDir;
        return this;
    }

    @NotNull
    public String getPromptTemplateFile()
    {
        return promptTemplateFile;
    }

    @Config("prompt.template-file")
    @ConfigDescription("Path to the system prompt template file")
    public JustAskConfig setPromptTemplateFile(String promptTemplateFile)
    {
        this.promptTemplateFile = promptTemplateFile;
        return this;
    }

    @NotNull
    public String getExecutorJdbcUrl()
    {
        return executorJdbcUrl;
    }

    @Config("executor.jdbc-url")
    @ConfigDescription("JDBC URL for executing generated SQL")
    public JustAskConfig setExecutorJdbcUrl(String executorJdbcUrl)
    {
        this.executorJdbcUrl = executorJdbcUrl;
        return this;
    }
}
```

**Step 2: Create JustAskTransactionHandle**

```java
package io.trino.plugin.justask;

import io.trino.spi.connector.ConnectorTransactionHandle;

public enum JustAskTransactionHandle
        implements ConnectorTransactionHandle
{
    INSTANCE
}
```

**Step 3: Create JustAskMetadata**

```java
package io.trino.plugin.justask;

import io.trino.spi.connector.ConnectorMetadata;

public class JustAskMetadata
        implements ConnectorMetadata
{
}
```

**Step 4: Create JustAskConnector**

```java
package io.trino.plugin.justask;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.trino.spi.connector.Connector;
import io.trino.spi.connector.ConnectorMetadata;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.function.table.ConnectorTableFunction;
import io.trino.spi.transaction.IsolationLevel;

import java.util.Set;

public class JustAskConnector
        implements Connector
{
    private final Set<ConnectorTableFunction> tableFunctions;

    @Inject
    public JustAskConnector(Set<ConnectorTableFunction> tableFunctions)
    {
        this.tableFunctions = ImmutableSet.copyOf(tableFunctions);
    }

    @Override
    public ConnectorTransactionHandle beginTransaction(IsolationLevel isolationLevel, boolean readOnly, boolean autoCommit)
    {
        return JustAskTransactionHandle.INSTANCE;
    }

    @Override
    public ConnectorMetadata getMetadata(ConnectorSession session, ConnectorTransactionHandle transaction)
    {
        return new JustAskMetadata();
    }

    @Override
    public Set<ConnectorTableFunction> getTableFunctions()
    {
        return tableFunctions;
    }
}
```

**Step 5: Create JustAskModule**

```java
package io.trino.plugin.justask;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;

import static io.airlift.configuration.ConfigBinder.configBinder;

public class JustAskModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        configBinder(binder).bindConfig(JustAskConfig.class);
        binder.bind(JustAskConnector.class).in(Scopes.SINGLETON);
    }
}
```

**Step 6: Create JustAskConnectorFactory**

```java
package io.trino.plugin.justask;

import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.trino.spi.connector.Connector;
import io.trino.spi.connector.ConnectorContext;
import io.trino.spi.connector.ConnectorFactory;

import java.util.Map;

public class JustAskConnectorFactory
        implements ConnectorFactory
{
    @Override
    public String getName()
    {
        return "justask";
    }

    @Override
    public Connector create(String catalogName, Map<String, String> requiredConfig, ConnectorContext context)
    {
        Bootstrap app = new Bootstrap(new JustAskModule());
        Injector injector = app
                .doNotInitializeLogging()
                .setRequiredConfigurationProperties(requiredConfig)
                .initialize();

        return injector.getInstance(JustAskConnector.class);
    }
}
```

**Step 7: Create JustAskPlugin**

```java
package io.trino.plugin.justask;

import com.google.common.collect.ImmutableList;
import io.trino.spi.Plugin;
import io.trino.spi.connector.ConnectorFactory;

public class JustAskPlugin
        implements Plugin
{
    @Override
    public Iterable<ConnectorFactory> getConnectorFactories()
    {
        return ImmutableList.of(new JustAskConnectorFactory());
    }
}
```

**Step 8: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

**Step 9: Commit**

```bash
git add -A
git commit -m "feat: add plugin, connector, config, and metadata wiring"
```

---

### Task 3: DocReader

**Files:**
- Create: `src/main/java/io/trino/plugin/justask/docs/DocReader.java`
- Create: `src/test/java/io/trino/plugin/justask/docs/TestDocReader.java`

**Step 1: Write failing test**

```java
package io.trino.plugin.justask.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDocReader
{
    @TempDir
    Path tempDir;

    @Test
    void testReadExistingDoc()
            throws IOException
    {
        Path catalogDir = tempDir.resolve("my_catalog");
        Files.createDirectories(catalogDir);
        Files.writeString(catalogDir.resolve("index.md"), "# My Catalog\n\nSome docs.");

        DocReader reader = new DocReader(tempDir.toString());
        String content = reader.readDoc("my_catalog", "index.md");
        assertThat(content).isEqualTo("# My Catalog\n\nSome docs.");
    }

    @Test
    void testReadNestedDoc()
            throws IOException
    {
        Path tablesDir = tempDir.resolve("my_catalog/tables");
        Files.createDirectories(tablesDir);
        Files.writeString(tablesDir.resolve("users.md"), "# Users Table");

        DocReader reader = new DocReader(tempDir.toString());
        String content = reader.readDoc("my_catalog", "tables/users.md");
        assertThat(content).isEqualTo("# Users Table");
    }

    @Test
    void testReadNonexistentDocThrows()
    {
        DocReader reader = new DocReader(tempDir.toString());
        assertThatThrownBy(() -> reader.readDoc("my_catalog", "missing.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void testPathTraversalBlocked()
            throws IOException
    {
        Files.writeString(tempDir.resolve("secret.txt"), "secret");

        DocReader reader = new DocReader(tempDir.resolve("my_catalog").toString());
        assertThatThrownBy(() -> reader.readDoc("my_catalog", "../../secret.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testReadCatalogIndex()
            throws IOException
    {
        Path catalogDir = tempDir.resolve("sales");
        Files.createDirectories(catalogDir);
        Files.writeString(catalogDir.resolve("index.md"), "# Sales Catalog");

        DocReader reader = new DocReader(tempDir.toString());
        String content = reader.readCatalogIndex("sales");
        assertThat(content).isEqualTo("# Sales Catalog");
    }

    @Test
    void testReadCatalogIndexMissing()
    {
        DocReader reader = new DocReader(tempDir.toString());
        assertThat(reader.readCatalogIndex("nonexistent")).isNull();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl . -Dtest=TestDocReader -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (class DocReader not found)

**Step 3: Write implementation**

```java
package io.trino.plugin.justask.docs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DocReader
{
    private final Path baseDir;

    public DocReader(String baseDir)
    {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    public String readDoc(String catalog, String relativePath)
    {
        Path resolved = baseDir.resolve(catalog).resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Path traversal not allowed: " + relativePath);
        }
        if (!Files.exists(resolved)) {
            throw new IllegalArgumentException("Document not found: " + catalog + "/" + relativePath);
        }
        try {
            return Files.readString(resolved);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read document: " + resolved, e);
        }
    }

    public String readCatalogIndex(String catalog)
    {
        Path indexPath = baseDir.resolve(catalog).resolve("index.md").normalize();
        if (!Files.exists(indexPath)) {
            return null;
        }
        try {
            return Files.readString(indexPath);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read catalog index: " + indexPath, e);
        }
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn test -pl . -Dtest=TestDocReader`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add src/main/java/io/trino/plugin/justask/docs/DocReader.java src/test/java/io/trino/plugin/justask/docs/TestDocReader.java
git commit -m "feat: add DocReader for reading per-catalog markdown documentation"
```

---

### Task 4: PromptTemplate

**Files:**
- Create: `src/main/java/io/trino/plugin/justask/llm/PromptTemplate.java`
- Create: `src/test/java/io/trino/plugin/justask/llm/TestPromptTemplate.java`

**Step 1: Write failing test**

```java
package io.trino.plugin.justask.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestPromptTemplate
{
    @TempDir
    Path tempDir;

    @Test
    void testRenderWithCatalog()
            throws IOException
    {
        Path templateFile = tempDir.resolve("prompt.md");
        Files.writeString(templateFile, """
                You are a SQL writer.
                {{#if catalog}}
                Catalog: {{catalog}}
                {{/if}}
                Write SQL.""");

        PromptTemplate template = new PromptTemplate(templateFile.toString());
        String result = template.render("sales");
        assertThat(result).contains("Catalog: sales");
        assertThat(result).contains("You are a SQL writer.");
        assertThat(result).contains("Write SQL.");
    }

    @Test
    void testRenderWithoutCatalog()
            throws IOException
    {
        Path templateFile = tempDir.resolve("prompt.md");
        Files.writeString(templateFile, """
                You are a SQL writer.
                {{#if catalog}}
                Catalog: {{catalog}}
                {{/if}}
                Write SQL.""");

        PromptTemplate template = new PromptTemplate(templateFile.toString());
        String result = template.render(null);
        assertThat(result).doesNotContain("Catalog:");
        assertThat(result).contains("You are a SQL writer.");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl . -Dtest=TestPromptTemplate`
Expected: FAIL

**Step 3: Write implementation**

Simple mustache-style rendering — no library needed for two variables:

```java
package io.trino.plugin.justask.llm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PromptTemplate
{
    private static final Pattern IF_CATALOG_BLOCK = Pattern.compile(
            "\\{\\{#if catalog}}(.*?)\\{\\{/if}}",
            Pattern.DOTALL);
    private static final Pattern CATALOG_VAR = Pattern.compile("\\{\\{catalog}}");

    private final String templateContent;

    public PromptTemplate(String templateFilePath)
    {
        try {
            this.templateContent = Files.readString(Path.of(templateFilePath));
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read prompt template: " + templateFilePath, e);
        }
    }

    public String render(String catalog)
    {
        String result = templateContent;

        // Process {{#if catalog}}...{{/if}} blocks
        Matcher matcher = IF_CATALOG_BLOCK.matcher(result);
        if (catalog != null) {
            // Keep block content, remove markers
            result = matcher.replaceAll("$1");
        }
        else {
            // Remove entire block
            result = matcher.replaceAll("");
        }

        // Replace {{catalog}} with value
        if (catalog != null) {
            result = CATALOG_VAR.matcher(result).replaceAll(Matcher.quoteReplacement(catalog));
        }

        // Clean up extra blank lines
        result = result.replaceAll("\n{3,}", "\n\n").trim();

        return result;
    }
}
```

**Step 4: Run tests**

Run: `mvn test -pl . -Dtest=TestPromptTemplate`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add src/main/java/io/trino/plugin/justask/llm/PromptTemplate.java src/test/java/io/trino/plugin/justask/llm/TestPromptTemplate.java
git commit -m "feat: add PromptTemplate with mustache-style catalog variable substitution"
```

---

### Task 5: LlmClient

**Files:**
- Create: `src/main/java/io/trino/plugin/justask/llm/LlmClient.java`
- Create: `src/test/java/io/trino/plugin/justask/llm/TestLlmClient.java`

**Step 1: Write failing test**

```java
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
    void testParseToolCallResponse()
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

        JsonNode response = MAPPER.valueToTree(MAPPER.readValue(responseJson, Object.class));
        LlmClient.ParsedResponse parsed = LlmClient.parseResponse(response);

        assertThat(parsed.isToolCall()).isTrue();
        assertThat(parsed.toolCalls()).hasSize(1);
        assertThat(parsed.toolCalls().get(0).name()).isEqualTo("read_doc");
        assertThat(parsed.toolCalls().get(0).arguments().get("path").asText()).isEqualTo("tables/users.md");
    }

    @Test
    void testParseTextResponse()
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

        JsonNode response = MAPPER.valueToTree(MAPPER.readValue(responseJson, Object.class));
        LlmClient.ParsedResponse parsed = LlmClient.parseResponse(response);

        assertThat(parsed.isToolCall()).isFalse();
        assertThat(parsed.textContent()).isEqualTo("```sql\nSELECT * FROM users\n```");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl . -Dtest=TestLlmClient`
Expected: FAIL

**Step 3: Write implementation**

```java
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

    public JsonNode sendRequest(ObjectNode requestBody)
            throws IOException, InterruptedException
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
```

**Step 4: Run tests**

Run: `mvn test -pl . -Dtest=TestLlmClient`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add src/main/java/io/trino/plugin/justask/llm/LlmClient.java src/test/java/io/trino/plugin/justask/llm/TestLlmClient.java
git commit -m "feat: add LlmClient for OpenAI-compatible chat completions with tool-use support"
```

---

### Task 6: AgentLoop

**Files:**
- Create: `src/main/java/io/trino/plugin/justask/llm/AgentLoop.java`
- Create: `src/test/java/io/trino/plugin/justask/llm/TestAgentLoop.java`

**Step 1: Write failing test**

```java
package io.trino.plugin.justask.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestAgentLoop
{
    @TempDir
    Path tempDir;

    @Test
    void testExtractSqlFromCodeBlock()
    {
        String response = "Here's your query:\n```sql\nSELECT * FROM users WHERE active = true\n```";
        String sql = AgentLoop.extractSql(response);
        assertThat(sql).isEqualTo("SELECT * FROM users WHERE active = true");
    }

    @Test
    void testExtractSqlPlainText()
    {
        String response = "SELECT * FROM users WHERE active = true";
        String sql = AgentLoop.extractSql(response);
        assertThat(sql).isEqualTo("SELECT * FROM users WHERE active = true");
    }

    @Test
    void testExtractSqlFromGenericCodeBlock()
    {
        String response = "```\nSELECT 1\n```";
        String sql = AgentLoop.extractSql(response);
        assertThat(sql).isEqualTo("SELECT 1");
    }

    @Test
    void testBuildSystemPromptScoped()
            throws IOException
    {
        Path templateFile = tempDir.resolve("prompt.md");
        Files.writeString(templateFile, """
                You are a SQL writer.
                {{#if catalog}}
                Docs for {{catalog}}:
                {{catalog_index}}
                {{/if}}
                Return SQL.""");

        Path catalogDir = tempDir.resolve("catalogs/sales");
        Files.createDirectories(catalogDir);
        Files.writeString(catalogDir.resolve("index.md"), "# Sales tables");

        PromptTemplate prompt = new PromptTemplate(templateFile.toString());
        // Test that the prompt includes the catalog context
        String rendered = prompt.render("sales");
        assertThat(rendered).contains("Docs for sales");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -pl . -Dtest=TestAgentLoop`
Expected: FAIL

**Step 3: Write implementation**

```java
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

        // If scoped, prepend index content to the user message
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

                // Process tool calls
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
```

**Step 4: Run tests**

Run: `mvn test -pl . -Dtest=TestAgentLoop`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add src/main/java/io/trino/plugin/justask/llm/AgentLoop.java src/test/java/io/trino/plugin/justask/llm/TestAgentLoop.java
git commit -m "feat: add AgentLoop for LLM tool-use conversation with doc reading"
```

---

### Task 7: QueryFunction Table Function

**Files:**
- Create: `src/main/java/io/trino/plugin/justask/QueryFunction.java`
- Create: `src/main/java/io/trino/plugin/justask/QueryFunctionHandle.java`

**Step 1: Write QueryFunctionHandle**

```java
package io.trino.plugin.justask;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.spi.connector.ConnectorTableFunctionHandle;

public class QueryFunctionHandle
        implements ConnectorTableFunctionHandle
{
    private final String question;
    private final String catalog;
    private final String generatedSql;

    @JsonCreator
    public QueryFunctionHandle(
            @JsonProperty("question") String question,
            @JsonProperty("catalog") String catalog,
            @JsonProperty("generatedSql") String generatedSql)
    {
        this.question = question;
        this.catalog = catalog;
        this.generatedSql = generatedSql;
    }

    @JsonProperty
    public String getQuestion()
    {
        return question;
    }

    @JsonProperty
    public String getCatalog()
    {
        return catalog;
    }

    @JsonProperty
    public String getGeneratedSql()
    {
        return generatedSql;
    }
}
```

**Step 2: Write QueryFunction**

```java
package io.trino.plugin.justask;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Provider;
import io.trino.plugin.justask.llm.AgentLoop;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.function.table.AbstractConnectorTableFunction;
import io.trino.spi.function.table.Argument;
import io.trino.spi.function.table.Descriptor;
import io.trino.spi.function.table.ScalarArgument;
import io.trino.spi.function.table.ScalarArgumentSpecification;
import io.trino.spi.function.table.TableFunctionAnalysis;

import java.util.Map;
import java.util.Optional;

import static io.trino.spi.type.VarcharType.VARCHAR;

public class QueryFunction
        extends AbstractConnectorTableFunction
{
    private final Provider<AgentLoop> agentLoopProvider;

    @Inject
    public QueryFunction(Provider<AgentLoop> agentLoopProvider)
    {
        super(
                "system",
                "query",
                ImmutableList.of(
                        ScalarArgumentSpecification.builder()
                                .name("QUESTION")
                                .type(VARCHAR)
                                .build(),
                        ScalarArgumentSpecification.builder()
                                .name("CATALOG")
                                .type(VARCHAR)
                                .defaultValue(null)
                                .build()),
                new Descriptor(ImmutableList.of(
                        new Descriptor.Field("sql", Optional.of(VARCHAR)))));

        this.agentLoopProvider = agentLoopProvider;
    }

    @Override
    public TableFunctionAnalysis analyze(
            ConnectorSession session,
            ConnectorTransactionHandle transaction,
            Map<String, Argument> arguments)
    {
        String question = ((ScalarArgument) arguments.get("QUESTION")).getValue().toString();
        Object catalogArg = ((ScalarArgument) arguments.get("CATALOG")).getValue();
        String catalog = catalogArg != null ? catalogArg.toString() : null;

        String generatedSql = agentLoopProvider.get().generateSql(question, catalog);

        return TableFunctionAnalysis.builder()
                .handle(new QueryFunctionHandle(question, catalog, generatedSql))
                .returnedType(new Descriptor(ImmutableList.of(
                        new Descriptor.Field("sql", Optional.of(VARCHAR)))))
                .build();
    }
}
```

**Step 3: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add src/main/java/io/trino/plugin/justask/QueryFunction.java src/main/java/io/trino/plugin/justask/QueryFunctionHandle.java
git commit -m "feat: add query() table function that generates SQL from natural language"
```

---

### Task 8: AskFunction Table Function

**Files:**
- Create: `src/main/java/io/trino/plugin/justask/AskFunction.java`
- Create: `src/main/java/io/trino/plugin/justask/AskFunctionHandle.java`
- Create: `src/main/java/io/trino/plugin/justask/executor/SqlExecutor.java`

**Step 1: Write SqlExecutor**

```java
package io.trino.plugin.justask.executor;

import io.trino.spi.TrinoException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.trino.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;

public class SqlExecutor
{
    private final String jdbcUrl;

    public SqlExecutor(String jdbcUrl)
    {
        this.jdbcUrl = jdbcUrl;
    }

    public SqlResult execute(String sql)
    {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            List<String> columnNames = new ArrayList<>();
            List<Integer> columnTypes = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(meta.getColumnName(i));
                columnTypes.add(meta.getColumnType(i));
            }

            List<List<Object>> rows = new ArrayList<>();
            while (rs.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                rows.add(row);
            }

            return new SqlResult(columnNames, columnTypes, rows);
        }
        catch (SQLException e) {
            throw new TrinoException(GENERIC_INTERNAL_ERROR,
                    "Failed to execute generated SQL: " + e.getMessage(), e);
        }
    }

    public record SqlResult(
            List<String> columnNames,
            List<Integer> columnTypes,
            List<List<Object>> rows) {}
}
```

**Step 2: Write AskFunctionHandle**

```java
package io.trino.plugin.justask;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.spi.connector.ConnectorTableFunctionHandle;

public class AskFunctionHandle
        implements ConnectorTableFunctionHandle
{
    private final String question;
    private final String catalog;
    private final String generatedSql;

    @JsonCreator
    public AskFunctionHandle(
            @JsonProperty("question") String question,
            @JsonProperty("catalog") String catalog,
            @JsonProperty("generatedSql") String generatedSql)
    {
        this.question = question;
        this.catalog = catalog;
        this.generatedSql = generatedSql;
    }

    @JsonProperty
    public String getQuestion()
    {
        return question;
    }

    @JsonProperty
    public String getCatalog()
    {
        return catalog;
    }

    @JsonProperty
    public String getGeneratedSql()
    {
        return generatedSql;
    }
}
```

**Step 3: Write AskFunction**

```java
package io.trino.plugin.justask;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Provider;
import io.trino.plugin.justask.executor.SqlExecutor;
import io.trino.plugin.justask.llm.AgentLoop;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.function.table.AbstractConnectorTableFunction;
import io.trino.spi.function.table.Argument;
import io.trino.spi.function.table.Descriptor;
import io.trino.spi.function.table.ScalarArgument;
import io.trino.spi.function.table.ScalarArgumentSpecification;
import io.trino.spi.function.table.TableFunctionAnalysis;

import java.util.Map;
import java.util.Optional;

import static io.trino.spi.function.table.ReturnTypeSpecification.GenericTable.GENERIC_TABLE;
import static io.trino.spi.type.VarcharType.VARCHAR;

public class AskFunction
        extends AbstractConnectorTableFunction
{
    private final Provider<AgentLoop> agentLoopProvider;
    private final Provider<SqlExecutor> sqlExecutorProvider;

    @Inject
    public AskFunction(Provider<AgentLoop> agentLoopProvider, Provider<SqlExecutor> sqlExecutorProvider)
    {
        super(
                "system",
                "ask",
                ImmutableList.of(
                        ScalarArgumentSpecification.builder()
                                .name("QUESTION")
                                .type(VARCHAR)
                                .build(),
                        ScalarArgumentSpecification.builder()
                                .name("CATALOG")
                                .type(VARCHAR)
                                .defaultValue(null)
                                .build()),
                GENERIC_TABLE);

        this.agentLoopProvider = agentLoopProvider;
        this.sqlExecutorProvider = sqlExecutorProvider;
    }

    @Override
    public TableFunctionAnalysis analyze(
            ConnectorSession session,
            ConnectorTransactionHandle transaction,
            Map<String, Argument> arguments)
    {
        String question = ((ScalarArgument) arguments.get("QUESTION")).getValue().toString();
        Object catalogArg = ((ScalarArgument) arguments.get("CATALOG")).getValue();
        String catalog = catalogArg != null ? catalogArg.toString() : null;

        // Generate SQL
        String generatedSql = agentLoopProvider.get().generateSql(question, catalog);

        // Execute to discover schema
        SqlExecutor.SqlResult result = sqlExecutorProvider.get().execute(generatedSql);

        // Build dynamic return type from result columns
        ImmutableList.Builder<Descriptor.Field> fields = ImmutableList.builder();
        for (String columnName : result.columnNames()) {
            fields.add(new Descriptor.Field(columnName, Optional.of(VARCHAR)));
        }

        return TableFunctionAnalysis.builder()
                .handle(new AskFunctionHandle(question, catalog, generatedSql))
                .returnedType(new Descriptor(fields.build()))
                .build();
    }
}
```

**Step 4: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add src/main/java/io/trino/plugin/justask/AskFunction.java src/main/java/io/trino/plugin/justask/AskFunctionHandle.java src/main/java/io/trino/plugin/justask/executor/SqlExecutor.java
git commit -m "feat: add ask() table function with SQL generation and execution"
```

---

### Task 9: Wire Table Functions into Connector via Guice

**Files:**
- Modify: `src/main/java/io/trino/plugin/justask/JustAskModule.java`

**Step 1: Update JustAskModule to bind all components**

```java
package io.trino.plugin.justask;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import io.trino.plugin.justask.docs.DocReader;
import io.trino.plugin.justask.executor.SqlExecutor;
import io.trino.plugin.justask.llm.AgentLoop;
import io.trino.plugin.justask.llm.LlmClient;
import io.trino.plugin.justask.llm.PromptTemplate;
import io.trino.spi.function.table.ConnectorTableFunction;

import static io.airlift.configuration.ConfigBinder.configBinder;

public class JustAskModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        configBinder(binder).bindConfig(JustAskConfig.class);
        binder.bind(JustAskConnector.class).in(Scopes.SINGLETON);

        Multibinder<ConnectorTableFunction> tableFunctions = Multibinder.newSetBinder(binder, ConnectorTableFunction.class);
        tableFunctions.addBinding().to(QueryFunction.class).in(Scopes.SINGLETON);
        tableFunctions.addBinding().to(AskFunction.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    public LlmClient provideLlmClient(JustAskConfig config)
    {
        return new LlmClient(
                config.getLlmEndpoint(),
                config.getLlmApiKey(),
                config.getLlmModel(),
                config.getLlmTemperature(),
                config.getLlmMaxTokens());
    }

    @Provides
    @Singleton
    public DocReader provideDocReader(JustAskConfig config)
    {
        return new DocReader(config.getDocsBaseDir());
    }

    @Provides
    @Singleton
    public PromptTemplate providePromptTemplate(JustAskConfig config)
    {
        return new PromptTemplate(config.getPromptTemplateFile());
    }

    @Provides
    @Singleton
    public AgentLoop provideAgentLoop(LlmClient llmClient, PromptTemplate promptTemplate, DocReader docReader, JustAskConfig config)
    {
        return new AgentLoop(llmClient, promptTemplate, docReader, config.getLlmMaxToolCalls());
    }

    @Provides
    @Singleton
    public SqlExecutor provideSqlExecutor(JustAskConfig config)
    {
        return new SqlExecutor(config.getExecutorJdbcUrl());
    }
}
```

**Step 2: Verify compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/io/trino/plugin/justask/JustAskModule.java
git commit -m "feat: wire all components into Guice module with table function bindings"
```

---

### Task 10: Example Configuration Files

**Files:**
- Create: `etc/catalog/justask.properties`
- Create: `etc/justask/system-prompt.md`
- Create: `etc/justask/catalogs/example/index.md`
- Create: `etc/justask/catalogs/example/tables/customers.md`
- Create: `etc/justask/catalogs/example/concepts/best.md`
- Create: `etc/justask/catalogs/example/examples/revenue.md`

**Step 1: Create catalog properties**

```properties
connector.name=justask
llm.endpoint=https://api.openai.com/v1
llm.api-key=${ENV:OPENAI_API_KEY}
llm.model=gpt-4o
llm.temperature=0.2
llm.max-tokens=4096
llm.max-tool-calls=10
docs.base-dir=etc/justask/catalogs
prompt.template-file=etc/justask/system-prompt.md
executor.jdbc-url=jdbc:trino://localhost:8080
```

**Step 2: Create system prompt template**

```markdown
You are a SQL query writer for Trino (formerly PrestoSQL).
Given a user's natural language question, write a single valid Trino SQL query.

Rules:
- Use only standard Trino SQL syntax
- Do not use database-specific functions unless documented
- Always qualify table names with schema where applicable
- Return ONLY the SQL query in a ```sql code block

{{#if catalog}}
You have access to documentation for the "{{catalog}}" catalog.
Use the read_doc tool to read documentation files referenced in the catalog index.
Read the relevant documentation before writing your query.
{{/if}}
```

**Step 3: Create example catalog docs**

`etc/justask/catalogs/example/index.md`:
```markdown
# Example Catalog

## Tables
- [customers](tables/customers.md) — Customer master data
- [orders](tables/orders.md) — Order transactions

## Concepts
- [Defining "best"](concepts/best.md) — How to interpret "best", "top", "most popular"

## Example Queries
- [Revenue reports](examples/revenue.md)
```

`etc/justask/catalogs/example/tables/customers.md`:
```markdown
# customers

Schema: `example.public.customers`

| Column | Type | Description |
|--------|------|-------------|
| customer_id | BIGINT | Primary key |
| name | VARCHAR | Full name |
| email | VARCHAR | Email address |
| region | VARCHAR | Geographic region |
| created_at | TIMESTAMP | Account creation date |
| lifetime_value | DECIMAL(12,2) | Total revenue from customer |

## Relationships
- Referenced by `orders.customer_id`
```

`etc/justask/catalogs/example/concepts/best.md`:
```markdown
# Defining "best"

When a user asks for the "best" customers, products, or similar:

- **"best customers"** → ORDER BY lifetime_value DESC
- **"top N"** → LIMIT N (default 10 if not specified)
- **"most active"** → ORDER BY COUNT(orders) DESC
- **"most popular"** → ORDER BY COUNT(*) DESC or SUM(quantity) DESC
- **"recent"** → ORDER BY created_at DESC or WHERE created_at > CURRENT_DATE - INTERVAL '30' DAY
```

`etc/justask/catalogs/example/examples/revenue.md`:
```markdown
# Revenue Report Examples

## Total revenue by region
```sql
SELECT region, SUM(lifetime_value) AS total_revenue
FROM example.public.customers
GROUP BY region
ORDER BY total_revenue DESC
```

## Top 10 customers by lifetime value
```sql
SELECT customer_id, name, lifetime_value
FROM example.public.customers
ORDER BY lifetime_value DESC
LIMIT 10
```
```

**Step 4: Commit**

```bash
git add etc/
git commit -m "feat: add example configuration files and catalog documentation"
```

---

### Task 11: Full Build and Package Verification

**Step 1: Run full build**

Run: `mvn clean package -DskipTests`
Expected: BUILD SUCCESS, JAR in `target/`

**Step 2: Run all tests**

Run: `mvn test`
Expected: ALL PASS

**Step 3: Commit any fixes needed, then tag**

```bash
git add -A
git commit -m "chore: fix any build issues from full integration"
```
