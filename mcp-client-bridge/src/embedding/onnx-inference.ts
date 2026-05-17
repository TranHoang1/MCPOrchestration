/**
 * ONNX Runtime wrapper for all-MiniLM-L6-v2 inference.
 * Returns zeros if onnxruntime-node is not installed (graceful degradation).
 */

import { readFileSync } from 'node:fs';
import { ModelPaths } from './model-downloader.js';

const DIMENSIONS = 384;
const MAX_SEQ_LEN = 256;
const MAX_BATCH = 32;

// Lazy-loaded ONNX Runtime reference
let ort: any = null;
let session: any = null;
let tokenizer: WordPieceTokenizer | null = null;

interface TokenizerConfig {
  model: { vocab: Record<string, number> };
}

/** Simple WordPiece tokenizer using tokenizer.json vocab. */
class WordPieceTokenizer {
  private vocab: Map<string, number>;
  private clsId: number;
  private sepId: number;
  private padId: number;
  private unkId: number;

  constructor(config: TokenizerConfig) {
    this.vocab = new Map(Object.entries(config.model.vocab));
    this.clsId = this.vocab.get('[CLS]') ?? 101;
    this.sepId = this.vocab.get('[SEP]') ?? 102;
    this.padId = this.vocab.get('[PAD]') ?? 0;
    this.unkId = this.vocab.get('[UNK]') ?? 100;
  }

  /** Tokenize text into input_ids with CLS/SEP, truncate/pad to maxLen. */
  tokenize(text: string, maxLen: number): number[] {
    const tokens = this.wordPieceTokenize(text.toLowerCase());
    const truncated = tokens.slice(0, maxLen - 2);
    const ids = [this.clsId, ...truncated, this.sepId];
    while (ids.length < maxLen) ids.push(this.padId);
    return ids;
  }

  private wordPieceTokenize(text: string): number[] {
    const words = text.split(/\s+/).filter((w) => w.length > 0);
    const ids: number[] = [];
    for (const word of words) {
      const subIds = this.tokenizeWord(word);
      ids.push(...subIds);
      if (ids.length >= MAX_SEQ_LEN - 2) break;
    }
    return ids;
  }

  private tokenizeWord(word: string): number[] {
    const ids: number[] = [];
    let start = 0;
    while (start < word.length) {
      let found = false;
      for (let end = word.length; end > start; end--) {
        const sub = start === 0 ? word.slice(start, end) : `##${word.slice(start, end)}`;
        if (this.vocab.has(sub)) {
          ids.push(this.vocab.get(sub)!);
          start = end;
          found = true;
          break;
        }
      }
      if (!found) {
        ids.push(this.unkId);
        start++;
      }
    }
    return ids;
  }
}

/** Try to load ONNX Runtime. Returns false if not available. */
async function loadOrt(): Promise<boolean> {
  if (ort) return true;
  try {
    // Dynamic import — onnxruntime-node is optional
    ort = await import(/* webpackIgnore: true */ 'onnxruntime-node' as string);
    return true;
  } catch {
    return false;
  }
}

/** Initialize ONNX session and tokenizer. Lazy-loaded on first call. */
export async function initSession(paths: ModelPaths): Promise<boolean> {
  if (session) return true;
  const ortAvailable = await loadOrt();
  if (!ortAvailable) {
    console.error('[embedding] onnxruntime-node not installed — using stub');
    return false;
  }
  try {
    session = await ort.InferenceSession.create(paths.modelPath);
    const raw = readFileSync(paths.tokenizerPath, 'utf-8');
    tokenizer = new WordPieceTokenizer(JSON.parse(raw));
    return true;
  } catch (e: any) {
    console.error(`[embedding] Failed to init ONNX session: ${e.message}`);
    return false;
  }
}

/** Check if ONNX inference is ready. */
export function isOnnxReady(): boolean {
  return session !== null && tokenizer !== null;
}

/** Run inference on texts. Returns float[n][384]. */
export async function infer(texts: string[]): Promise<number[][]> {
  if (!isOnnxReady()) return texts.map(() => new Array(DIMENSIONS).fill(0));

  const batch = texts.slice(0, MAX_BATCH);
  const batchSize = batch.length;

  // Tokenize all texts
  const allIds: number[][] = batch.map((t) => tokenizer!.tokenize(t, MAX_SEQ_LEN));
  const inputIds = new BigInt64Array(batchSize * MAX_SEQ_LEN);
  const attentionMask = new BigInt64Array(batchSize * MAX_SEQ_LEN);
  const tokenTypeIds = new BigInt64Array(batchSize * MAX_SEQ_LEN);

  for (let i = 0; i < batchSize; i++) {
    for (let j = 0; j < MAX_SEQ_LEN; j++) {
      const idx = i * MAX_SEQ_LEN + j;
      inputIds[idx] = BigInt(allIds[i][j]);
      attentionMask[idx] = allIds[i][j] !== 0 ? 1n : 0n;
      tokenTypeIds[idx] = 0n;
    }
  }

  const feeds = {
    input_ids: new ort.Tensor('int64', inputIds, [batchSize, MAX_SEQ_LEN]),
    attention_mask: new ort.Tensor('int64', attentionMask, [batchSize, MAX_SEQ_LEN]),
    token_type_ids: new ort.Tensor('int64', tokenTypeIds, [batchSize, MAX_SEQ_LEN]),
  };

  const output = await session.run(feeds);
  return extractEmbeddings(output, batchSize, allIds);
}

/** Mean pooling + L2 normalization on model output. */
function extractEmbeddings(output: any, batchSize: number, allIds: number[][]): number[][] {
  const lastHidden = output['last_hidden_state'] ?? Object.values(output)[0];
  const data: Float32Array = lastHidden.data;
  const results: number[][] = [];

  for (let i = 0; i < batchSize; i++) {
    const embedding = new Array(DIMENSIONS).fill(0);
    let tokenCount = 0;
    for (let j = 0; j < MAX_SEQ_LEN; j++) {
      if (allIds[i][j] === 0) break;
      tokenCount++;
      for (let d = 0; d < DIMENSIONS; d++) {
        embedding[d] += data[i * MAX_SEQ_LEN * DIMENSIONS + j * DIMENSIONS + d];
      }
    }
    // Mean pooling
    if (tokenCount > 0) {
      for (let d = 0; d < DIMENSIONS; d++) embedding[d] /= tokenCount;
    }
    // L2 normalize
    let norm = 0;
    for (let d = 0; d < DIMENSIONS; d++) norm += embedding[d] * embedding[d];
    norm = Math.sqrt(norm);
    if (norm > 0) {
      for (let d = 0; d < DIMENSIONS; d++) embedding[d] /= norm;
    }
    results.push(embedding);
  }
  return results;
}
