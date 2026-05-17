/**
 * Merges local and remote tool definitions into a single unified list.
 * Handles conflict resolution (local-first by default).
 */

import { ToolDefinition } from '../local/server-process.js';

export type ConflictResolution = 'local-first' | 'remote-first';

export interface RegistryTool extends ToolDefinition {
  source: 'local' | 'remote';
  serverName?: string;
}

export class UnifiedRegistry {
  private merged: RegistryTool[] = [];
  private localTools: RegistryTool[] = [];
  private remoteTools: RegistryTool[] = [];
  private readonly conflictResolution: ConflictResolution;

  constructor(conflictResolution: ConflictResolution = 'local-first') {
    this.conflictResolution = conflictResolution;
  }

  /** Get all merged tools. */
  getAll(): RegistryTool[] { return [...this.merged]; }

  /** Get tool definitions formatted for MCP tools/list response. */
  getToolDefinitions(): ToolDefinition[] {
    return this.merged.map(({ name, description, inputSchema }) => ({
      name,
      ...(description && { description }),
      ...(inputSchema && { inputSchema }),
    }));
  }

  /** Update local tools and re-merge. */
  setLocalTools(tools: ToolDefinition[], serverMap?: Map<string, string[]>): void {
    this.localTools = tools.map((t) => ({
      ...t,
      source: 'local' as const,
      serverName: serverMap ? findServer(t.name, serverMap) : undefined,
    }));
    this.rebuild();
  }

  /** Update remote tools and re-merge. */
  setRemoteTools(tools: ToolDefinition[]): void {
    this.remoteTools = tools.map((t) => ({ ...t, source: 'remote' as const }));
    this.rebuild();
  }

  /** Force a full refresh of the merged list. */
  refresh(localTools: ToolDefinition[], remoteTools: ToolDefinition[]): void {
    this.setLocalTools(localTools);
    this.setRemoteTools(remoteTools);
  }

  /** Check if a tool exists in the registry. */
  has(toolName: string): boolean {
    return this.merged.some((t) => t.name === toolName);
  }

  /** Find a tool by name. */
  find(toolName: string): RegistryTool | undefined {
    return this.merged.find((t) => t.name === toolName);
  }

  get localCount(): number { return this.localTools.length; }
  get remoteCount(): number { return this.remoteTools.length; }
  get totalCount(): number { return this.merged.length; }

  private rebuild(): void {
    const map = new Map<string, RegistryTool>();

    if (this.conflictResolution === 'local-first') {
      // Remote first (will be overwritten by local)
      for (const tool of this.remoteTools) map.set(tool.name, tool);
      for (const tool of this.localTools) map.set(tool.name, tool);
    } else {
      // Local first (will be overwritten by remote)
      for (const tool of this.localTools) map.set(tool.name, tool);
      for (const tool of this.remoteTools) map.set(tool.name, tool);
    }

    this.merged = [...map.values()];
  }
}

/** Find which server a tool belongs to from a server→tools mapping. */
function findServer(toolName: string, serverMap: Map<string, string[]>): string | undefined {
  for (const [server, tools] of serverMap) {
    if (tools.includes(toolName)) return server;
  }
  return undefined;
}
