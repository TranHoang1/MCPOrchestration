/**
 * Downloads and caches the all-MiniLM-L6-v2 ONNX model from HuggingFace.
 * Cache path: ~/.mcp-bridge/models/all-MiniLM-L6-v2/
 */

import { existsSync, mkdirSync, createWriteStream } from 'node:fs';
import { join } from 'node:path';
import { homedir } from 'node:os';
import { get as httpsGet } from 'node:https';
import { get as httpGet } from 'node:http';

const MODEL_NAME = 'all-MiniLM-L6-v2';
const BASE_URL = 'https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main';
const MODEL_FILE = 'onnx/model.onnx';
const TOKENIZER_FILE = 'tokenizer.json';

export interface ModelPaths {
  modelPath: string;
  tokenizerPath: string;
}

/** Get the cache directory for the embedding model. */
export function getCacheDir(): string {
  return join(homedir(), '.mcp-bridge', 'models', MODEL_NAME);
}

/** Check if model files are already cached locally. */
export function isModelCached(): boolean {
  const dir = getCacheDir();
  const modelPath = join(dir, 'model.onnx');
  const tokenizerPath = join(dir, 'tokenizer.json');
  return existsSync(modelPath) && existsSync(tokenizerPath);
}

/** Get paths to cached model files. Returns null if not cached. */
export function getModelPaths(): ModelPaths | null {
  if (!isModelCached()) return null;
  const dir = getCacheDir();
  return {
    modelPath: join(dir, 'model.onnx'),
    tokenizerPath: join(dir, 'tokenizer.json'),
  };
}

/** Download model from HuggingFace and cache locally. */
export async function downloadModel(): Promise<ModelPaths> {
  const dir = getCacheDir();
  mkdirSync(dir, { recursive: true });

  const modelPath = join(dir, 'model.onnx');
  const tokenizerPath = join(dir, 'tokenizer.json');

  if (!existsSync(modelPath)) {
    console.error(`[embedding] Downloading model (~80MB)...`);
    await downloadFile(`${BASE_URL}/${MODEL_FILE}`, modelPath);
    console.error(`[embedding] Model downloaded.`);
  }

  if (!existsSync(tokenizerPath)) {
    console.error(`[embedding] Downloading tokenizer...`);
    await downloadFile(`${BASE_URL}/${TOKENIZER_FILE}`, tokenizerPath);
    console.error(`[embedding] Tokenizer downloaded.`);
  }

  return { modelPath, tokenizerPath };
}

/** Download a file from URL to local path, following redirects. */
function downloadFile(url: string, dest: string, maxRedirects = 5): Promise<void> {
  return new Promise((resolve, reject) => {
    if (maxRedirects <= 0) {
      reject(new Error('Too many redirects'));
      return;
    }
    const getter = url.startsWith('https') ? httpsGet : httpGet;
    getter(url, (res) => {
      // Handle redirects
      if (res.statusCode && res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        downloadFile(res.headers.location, dest, maxRedirects - 1).then(resolve, reject);
        return;
      }
      if (res.statusCode !== 200) {
        reject(new Error(`Download failed: HTTP ${res.statusCode}`));
        return;
      }
      const stream = createWriteStream(dest);
      res.pipe(stream);
      stream.on('finish', () => { stream.close(); resolve(); });
      stream.on('error', reject);
    }).on('error', reject);
  });
}
