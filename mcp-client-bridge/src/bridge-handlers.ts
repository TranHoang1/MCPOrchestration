/**
 * Tool call handlers for bridge-native tools.
 * Extracted from bridge-server.ts for file size compliance.
 */

import { HttpStreamableClient } from './http-streamable-client.js';
import { handleStreamWrite, StreamWriteArgs } from './local-stream-write.js';
import { handleEmbedImages, EmbedImagesArgs } from './local-embed-images.js';

type ToolResult = { content: Array<{ type: 'text'; text: string }>; isError?: boolean };

export class BridgeHandlers {
  constructor(private readonly httpClient: HttpStreamableClient) {}

  async handleFindTools(args: Record<string, unknown>): Promise<ToolResult> {
    const query = args.query as string;
    if (!query) return this.error("Missing 'query' parameter");
    try {
      const response = await this.httpClient.callTool('find_tools', { query });
      return this.ok(response.content?.[0]?.text ?? '{}');
    } catch (err: unknown) {
      return this.error(`find_tools failed: ${this.msg(err)}`);
    }
  }

  async handleExecuteDynamicTool(args: Record<string, unknown>): Promise<ToolResult> {
    const toolName = args.tool_name as string;
    if (!toolName) return this.error("Missing 'tool_name' parameter");
    try {
      const response = await this.httpClient.callTool('execute_dynamic_tool', {
        tool_name: toolName, arguments: args.arguments ?? {},
      });
      return this.ok(response.content?.[0]?.text ?? '{}');
    } catch (err: unknown) {
      return this.error(`execute_dynamic_tool failed: ${this.msg(err)}`);
    }
  }

  handleLocalStreamWrite(args: Record<string, unknown>): ToolResult {
    try {
      return this.ok(JSON.stringify(handleStreamWrite(args as unknown as StreamWriteArgs)));
    } catch (err: unknown) {
      return this.error(`stream_write_file failed: ${this.msg(err)}`);
    }
  }

  handleLocalEmbedImages(args: Record<string, unknown>): ToolResult {
    try {
      return this.ok(JSON.stringify(handleEmbedImages(args as unknown as EmbedImagesArgs)));
    } catch (err: unknown) {
      return this.error(`embed_images failed: ${this.msg(err)}`);
    }
  }

  async proxyToOrchestrator(toolName: string, args: Record<string, unknown>): Promise<ToolResult> {
    try {
      const response = await this.httpClient.callTool(toolName, args);
      return this.ok(response.content?.[0]?.text ?? '{}');
    } catch (err: unknown) {
      return this.error(`${toolName} failed: ${this.msg(err)}`);
    }
  }

  private ok(text: string): ToolResult {
    return { content: [{ type: 'text', text }] };
  }

  private error(text: string): ToolResult {
    return { content: [{ type: 'text', text }], isError: true };
  }

  private msg(err: unknown): string {
    return err instanceof Error ? err.message : String(err);
  }
}
