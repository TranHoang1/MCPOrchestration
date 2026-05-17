/**
 * Embedding fallback with priority chain:
 * 1. Local MCP "embed" tool → 2. Local ONNX model → 3. Download model → 4. Disable
 */

import { isModelCached, getModelPaths, downloadModel } from './model-downloader.js';
import { initSession, isOnnxReady, infer } from './onnx-inference.js';

const DIMENSIONS = 384;
const MAX_BATCH = 32;

export interface EmbeddingProvider {
  embed(texts: string[]): Promise<number[][]>;
  isAvailable(): boolean;
  dimensions(): number;
}

type McpEmbedFn = (texts: string[]) => Promise<number[][]>;

/**
 * Local embedding fallback implementation.
 * Lazy-loads model on first embed() call.
 */
export class EmbeddingFallback implements EmbeddingProvider {
  private initialized = false;
  private available = false;
  private mcpEmbedFn: McpEmbedFn | null = null;

  /** Register an MCP "embed" tool function (highest priority). */
  setMcpEmbed(fn: McpEmbedFn | null): void {
    this.mcpEmbedFn = fn;
  }

  /** Check if any embedding source is available. */
  isAvailable(): boolean {
    if (this.mcpEmbedFn) return true;
    return this.available;
  }

  /** Returns embedding dimensions (384 for all-MiniLM-L6-v2). */
  dimensions(): number {
    return DIMENSIONS;
  }

  /** Embed texts using priority chain. Lazy-loads on first call. */
  async embed(texts: string[]): Promise<number[][]> {
    if (texts.length === 0) return [];

    // Priority 1: MCP embed tool
    if (this.mcpEmbedFn) {
      try {
        return await this.mcpEmbedFn(texts);
      } catch (e: any) {
        console.error(`[embedding] MCP embed failed: ${e.message}, falling back to ONNX`);
      }
    }

    // Lazy init ONNX on first call
    if (!this.initialized) {
      await this.initOnnx();
    }

    if (!this.available) {
      console.error('[embedding] No embedding source available');
      return texts.map(() => new Array(DIMENSIONS).fill(0));
    }

    // Process in batches
    return this.inferBatched(texts);
  }

  private async initOnnx(): Promise<void> {
    this.initialized = true;
    try {
      // Priority 2: Cached model
      if (isModelCached()) {
        const paths = getModelPaths()!;
        this.available = await initSession(paths);
        if (this.available) {
          console.error('[embedding] ONNX model loaded from cache');
          return;
        }
      }

      // Priority 3: Download model
      console.error('[embedding] Downloading model...');
      const paths = await downloadModel();
      this.available = await initSession(paths);
      if (this.available) {
        console.error('[embedding] ONNX model ready after download');
      } else {
        console.error('[embedding] ONNX Runtime not available — embedding disabled');
      }
    } catch (e: any) {
      // Priority 4: Disable gracefully
      console.error(`[embedding] Init failed: ${e.message} — embedding disabled`);
      this.available = false;
    }
  }

  private async inferBatched(texts: string[]): Promise<number[][]> {
    const results: number[][] = [];
    for (let i = 0; i < texts.length; i += MAX_BATCH) {
      const batch = texts.slice(i, i + MAX_BATCH);
      const embeddings = await infer(batch);
      results.push(...embeddings);
    }
    return results;
  }
}
