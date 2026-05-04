# Module Analysis — root

**Last Updated:** 2026-05-03T03:51:59.579Z
**Language:** Kotlin 2.3.20 | **Framework:** Ktor 3.4.0 | **Platform:** JVM 21

## Package Structure

```
root/
├── com.orchestrator.mcp/     # Application logic
├── com.orchestrator.mcp.config/     # Configuration
├── com.orchestrator.mcp.di/     # Application logic
├── com.orchestrator.mcp.discovery/     # Application logic
├── com.orchestrator.mcp.discovery.model/     # Domain model
├── com.orchestrator.mcp.embedding/     # Application logic
├── com.orchestrator.mcp.execution/     # Application logic
├── com.orchestrator.mcp.execution.model/     # Domain model
├── com.orchestrator.mcp.model/     # Domain model
├── com.orchestrator.mcp.protocol/     # Application logic
├── com.orchestrator.mcp.protocol.model/     # Domain model
├── com.orchestrator.mcp.registry/     # Application logic
├── com.orchestrator.mcp.transport/     # Application logic
├── com.orchestrator.mcp.upstream/     # Application logic
├── com.orchestrator.mcp.upstream.model/     # Domain model
├── com.orchestrator.mcp.util/     # Utility functions
├── com.orchestrator.mcp.vectordb/     # Application logic
├── com.orchestrator.mcp.vectordb.model/     # Domain model
├── com.orchestrator.mcp.e2e/     # Application logic
└── com.orchestrator.mcp.it/     # Application logic
```

## Key Classes

| Class | Package | Responsibility | Visibility |
|-------|---------|---------------|------------|
| ConfigValidator | com.orchestrator.mcp.config | Input validation | public |
| ConfigurationManager | com.orchestrator.mcp.config | Application component | public |
| ConfigurationManagerImpl | com.orchestrator.mcp.config | Application component | public |
| ExternalConfigScanner | com.orchestrator.mcp.config | Application component | public |
| JsonConfigLoader | com.orchestrator.mcp.config | Application component | public |
| OrchestratorConfig | com.orchestrator.mcp.config | Configuration | data |
| OrchestratorSettings | com.orchestrator.mcp | Application component | data |
| ServerConfig | com.orchestrator.mcp.config | Configuration | data |
| DiscoveryConfig | com.orchestrator.mcp.config | Configuration | data |
| ExecutionConfig | com.orchestrator.mcp.config | Configuration | data |
| EmbeddingConfig | com.orchestrator.mcp.config | Configuration | data |
| VectorDbConfig | com.orchestrator.mcp.config | Configuration | data |
| HealthConfig | com.orchestrator.mcp.config | Configuration | data |
| UpstreamServerConfig | com.orchestrator.mcp.config | Configuration | data |
| KeywordSearchEngine | com.orchestrator.mcp | Application component | public |
| ToolDiscoveryService | com.orchestrator.mcp | Business logic | public |
| ToolDiscoveryServiceImpl | com.orchestrator.mcp | Business logic | public |
| FindToolsResponse | com.orchestrator.mcp | Data transfer object | data |
| ToolResult | com.orchestrator.mcp | Application component | data |
| EmbeddingService | com.orchestrator.mcp | Business logic | public |
| OpenAiEmbeddingService | com.orchestrator.mcp | Business logic | public |
| ToolExecutionDispatcher | com.orchestrator.mcp | Application component | public |
| ToolExecutionDispatcherImpl | com.orchestrator.mcp | Application component | public |
| ExecuteToolResponse | com.orchestrator.mcp | Data transfer object | data |
| ExecutionContentItem | com.orchestrator.mcp | Application component | data |
| ExecutionMeta | com.orchestrator.mcp | Application component | data |
| ErrorCodes | com.orchestrator.mcp | Application component | public |
| McpOrchestratorException | com.orchestrator.mcp | Error handling | sealed |
| InvalidParamsException | com.orchestrator.mcp | Error handling | public |
| ToolNotFoundException | com.orchestrator.mcp | Error handling | public |
| ServerUnavailableException | com.orchestrator.mcp | Error handling | public |
| ExecutionTimeoutException | com.orchestrator.mcp | Error handling | public |
| UpstreamErrorException | com.orchestrator.mcp | Error handling | public |
| VectorDbUnavailableException | com.orchestrator.mcp | Error handling | public |
| EmbeddingServiceException | com.orchestrator.mcp | Error handling | public |
| ConfigException | com.orchestrator.mcp.config | Error handling | public |
| GenericMcpException | com.orchestrator.mcp | Error handling | public |
| ToolDefinition | com.orchestrator.mcp | Application component | data |
| ToolEntry | com.orchestrator.mcp | Application component | data |
| JsonRpcHandler | com.orchestrator.mcp | HTTP request handling | public |
| is | com.orchestrator.mcp | Application component | public |
| McpProtocolHandler | com.orchestrator.mcp | HTTP request handling | public |
| McpServerFactory | com.orchestrator.mcp | Object creation | public |
| McpToolRegistrar | com.orchestrator.mcp | Application component | public |
| JsonRpcRequest | com.orchestrator.mcp | Data transfer object | data |
| JsonRpcResponse | com.orchestrator.mcp | Data transfer object | data |
| JsonRpcError | com.orchestrator.mcp | Error handling | data |
| InitializeParams | com.orchestrator.mcp | Application component | data |
| ClientInfo | com.orchestrator.mcp | Application component | data |
| InitializeResult | com.orchestrator.mcp | Application component | data |
| ServerCapabilities | com.orchestrator.mcp | Application component | data |
| ServerInfo | com.orchestrator.mcp | Application component | data |
| ToolsListResult | com.orchestrator.mcp | Application component | data |
| McpToolDefinition | com.orchestrator.mcp | Application component | data |
| ToolCallParams | com.orchestrator.mcp | Application component | data |
| ToolCallResult | com.orchestrator.mcp | Application component | data |
| ContentItem | com.orchestrator.mcp | Application component | data |
| ToolCallMeta | com.orchestrator.mcp | Application component | data |
| ToolIndexer | com.orchestrator.mcp | Application component | public |
| IndexResult | com.orchestrator.mcp | Application component | data |
| ToolRegistry | com.orchestrator.mcp | Application component | public |
| ToolRegistryImpl | com.orchestrator.mcp | Application component | public |
| McpTransport | com.orchestrator.mcp | Application component | public |
| StdioTransport | com.orchestrator.mcp | Application component | public |
| HealthMonitor | com.orchestrator.mcp | Application component | public |
| HttpMcpConnection | com.orchestrator.mcp | Application component | public |
| McpConnection | com.orchestrator.mcp | Application component | public |
| StdioMcpConnection | com.orchestrator.mcp | Application component | public |
| UpstreamServerManager | com.orchestrator.mcp | Application component | public |
| UpstreamServerManagerImpl | com.orchestrator.mcp | Application component | public |
| ServerState | com.orchestrator.mcp | Application component | public |
| TransportType | com.orchestrator.mcp | Application component | public |
| UpstreamServerInfo | com.orchestrator.mcp | Application component | data |
| RetryUtils | com.orchestrator.mcp | Utility functions | public |
| FaissVectorDbClient | com.orchestrator.mcp | External service client | public |
| PointMetadata | com.orchestrator.mcp | Application component | data |
| FaissEntry | com.orchestrator.mcp | Application component | data |
| QdrantVectorDbClient | com.orchestrator.mcp | External service client | public |
| VectorDbClient | com.orchestrator.mcp | External service client | public |
| VectorPoint | com.orchestrator.mcp | Application component | data |
| SearchResult | com.orchestrator.mcp | Application component | data |
| orchestrator | com.orchestrator.mcp | Application component | public |
| configuration | com.orchestrator.mcp.config | Application component | public |
| appender | com.orchestrator.mcp | Application component | public |
| target | com.orchestrator.mcp | Application component | public |
| encoder | com.orchestrator.mcp | Application component | public |
| pattern | com.orchestrator.mcp | Application component | public |
| logger | com.orchestrator.mcp | Application component | public |
| root | com.orchestrator.mcp | Application component | public |
| appender-ref | com.orchestrator.mcp | Application component | public |
| ApplicationTest | com.orchestrator.mcp | Test class | public |
| CliConfigTest | com.orchestrator.mcp.config | Test class | public |
| ConfigurationManagerTest | com.orchestrator.mcp.config | Test class | public |
| ExternalConfigScannerTest | com.orchestrator.mcp.config | Test class | public |
| JsonConfigLoaderTest | com.orchestrator.mcp.config | Test class | public |
| JsonConfigMergeTest | com.orchestrator.mcp.config | Test class | public |
| McpServersFormatTest | com.orchestrator.mcp | Test class | public |
| ToolDiscoveryServiceImplTest | com.orchestrator.mcp | Test class | public |
| E2eConfigApiTest | com.orchestrator.mcp.config | Test class | public |
| E2eDiscoveryApiTest | com.orchestrator.mcp | Test class | public |
| E2eExecutionApiTest | com.orchestrator.mcp | Test class | public |
| E2ePerformanceApiTest | com.orchestrator.mcp | Test class | public |
| E2eProtocolApiTest | com.orchestrator.mcp | Test class | public |
| OpenAiEmbeddingServiceTest | com.orchestrator.mcp | Test class | public |
| ToolExecutionDispatcherImplTest | com.orchestrator.mcp | Test class | public |
| ConfigIntegrationTest | com.orchestrator.mcp.config | Test class | public |
| HealthMonitorIntegrationTest | com.orchestrator.mcp | Test class | public |
| IntegrationTestBase | com.orchestrator.mcp | Application component | public |
| DiscoveryStack | com.orchestrator.mcp | Application component | data |
| ExecutionStack | com.orchestrator.mcp | Application component | data |
| ProtocolStack | com.orchestrator.mcp | Application component | data |
| IndexerStack | com.orchestrator.mcp | Application component | data |
| McpProtocolIntegrationTest | com.orchestrator.mcp | Test class | public |
| TestFixtures | com.orchestrator.mcp | Application component | public |
| ToolDiscoveryIntegrationTest | com.orchestrator.mcp | Test class | public |
| ToolExecutionIntegrationTest | com.orchestrator.mcp | Test class | public |
| ToolIndexingIntegrationTest | com.orchestrator.mcp | Test class | public |
| JsonRpcHandlerTest | com.orchestrator.mcp | Test class | public |
| McpServerFactoryTest | com.orchestrator.mcp | Test class | public |
| ToolIndexerTest | com.orchestrator.mcp | Test class | public |
| ToolRegistryImplTest | com.orchestrator.mcp | Test class | public |
| HealthMonitorTest | com.orchestrator.mcp | Test class | public |
| RetryUtilsTest | com.orchestrator.mcp | Test class | public |
| FaissVectorDbClientTest | com.orchestrator.mcp | Test class | public |

## Public API Surface

- `main(args: Array<String>): Unit`
- `validate(config: OrchestratorConfig): List`
- `validateOrThrow(config: OrchestratorConfig): Unit`
- `getConfig(): OrchestratorConfig`
- `reload(): OrchestratorConfig`
- `watchForChanges(onChange: (OrchestratorConfig): Unit`
- `resolveEnvVars(content: String): String`
- `appModule(configPath: String? = null): Unit`
- `search(query: String, topK: Int, threshold: Float): List`
- `parseRequest(rawMessage: String): JsonRpcRequest`
- `handleInitialize(params: JsonObject?): InitializeResult`
- `handleToolsList(): ToolsListResult`
- `handlePing(): JsonElement`
- `create(): Server`
- `findToolsDefinition(): McpToolDefinition`
- `executeDynamicToolDefinition(): McpToolDefinition`
- `parseToolsList(response: JsonObject): List`
- `lookupTool(toolName: String): ToolEntry`
- `registerTool(entry: ToolEntry): Unit`
- `removeTool(toolName: String): Unit`
- `removeServerTools(serverName: String): Unit`
- `getAllTools(): List`
- `getToolsByServer(serverName: String): List`
- `getToolCount(): Int`
- `onMessage(handler: suspend (String): Unit`
- `start(scope: CoroutineScope): Unit`
- `stop(): Unit`
- `isActive(): Boolean`
- `getConnection(serverName: String): McpConnection`
- `getServerState(serverName: String): ServerState`
- `getAllServerStates(): Map`
- `loadFromDisk(): Unit`
- `defaultBasePath(): String`
- `mockEmbedding(): Unit`
- `mockSearchResults(count: Int, baseScore: Float = 0.9f): List`
- `buildFindToolsRequest(query: String, topK: Int = 5, threshold: Float = 0.7f): String`
- `buildExecuteRequest(toolName: String, args: String = "{}"): String`
- `buildFindToolsRequest(query: String, id: Int = 1): String`
- `createMockClient(responseBody: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient`
- `mockEmbeddingResponse(dimensions: Int = 768): String`
- `mockBatchEmbeddingResponse(count: Int, dimensions: Int = 768): String`
- `mockToolEntry(name: String = "read_logs", serverName: String = "log-server"): Unit`
- `mockUpstreamResponse(): Unit`
- `mockEmbedding(dims: Int = 768): FloatArray`
- `searchResults(count: Int, baseScore: Float = 0.95f): List`
- `sampleTools(count: Int = 10): List`
- `sampleToolDefinitions(count: Int = 5): List`
- `mockUpstreamToolsResponse(tools: List<ToolDefinition>): JsonObject`
- `mockToolCallResponse(text: String = "result data"): JsonObject`
- `mockErrorResponse(message: String = "Internal error"): JsonObject`
- `createEntry(name: String, serverName: String = "test-server"): Unit`
- `createClient(): FaissVectorDbClient`
- `makeVector(seed: Float, dims: Int = 768): FloatArray`

## Dependencies

| Imports From | Classes Used |
|-------------|-------------|
| — | — |

## Detected Patterns

- **DI Style**: none
- **Error Handling**: Result type
- **Naming**: *Service
- **Logging**: SLF4J
- **Testing**: unknown

## Annotations

| Target | Author Agent | Type | Content | Timestamp |
|--------|-------------|------|---------|-----------|
