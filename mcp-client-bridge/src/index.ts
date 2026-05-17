#!/usr/bin/env node
/**
 * MCP Client Bridge — Node.js
 * Connects to a remote MCP Orchestrator via HTTP Streamable transport
 * and exposes tools locally via stdio MCP server.
 */

import { BridgeServer } from './bridge-server.js';
import { BridgeConfig } from './bridge-config.js';

async function main(): Promise<void> {
  const config = BridgeConfig.load(process.argv.slice(2));
  if (config.orchestratorUrls.length > 1) {
    console.error(`[mcp-bridge] Configured ${config.orchestratorUrls.length} URLs (failover enabled)`);
    config.orchestratorUrls.forEach((u, i) => console.error(`  [${i + 1}] ${u}`));
  } else {
    console.error(`[mcp-bridge] Connecting to orchestrator at: ${config.orchestratorUrl}`);
  }
  if (config.token) {
    console.error('[mcp-bridge] Using JWT authentication');
  } else {
    console.error('[mcp-bridge] Warning: No token provided. Running without authentication.');
  }
  if (config.enableLocalServers) {
    console.error('[mcp-bridge] Local server management: ENABLED');
  }

  const bridge = new BridgeServer(config);

  process.on('SIGINT', async () => {
    await bridge.stop();
    process.exit(0);
  });

  process.on('SIGTERM', async () => {
    await bridge.stop();
    process.exit(0);
  });

  await bridge.start();
}

main().catch((err) => {
  console.error('[mcp-bridge] Fatal error:', err);
  process.exit(1);
});
