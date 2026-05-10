/**
 * Local embed_images tool — pure file I/O, no AI processing.
 * Reads a markdown file, replaces local image references with inline base64 data URIs,
 * and writes the result to output_path (or overwrites the original if not specified).
 *
 * Input: { file_path: string, output_path?: string }
 * Output: metadata only (output_path, images_embedded, images_failed, total_size_bytes)
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import { getWorkspaceRoot } from './local-stream-write.js';

export interface EmbedImagesArgs {
  file_path: string;
  output_path?: string;
}

export interface EmbedImagesResult {
  output_path: string;
  images_embedded: number;
  images_failed: string[];
  total_size_bytes: number;
}

const MIME_TYPES: Record<string, string> = {
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp',
  '.bmp': 'image/bmp',
};

const IMAGE_REGEX = /!\[([^\]]*)\]\(([^)]+)\)/g;

export function handleEmbedImages(args: EmbedImagesArgs): EmbedImagesResult {
  const rawPath = args.file_path;
  if (!rawPath) {
    throw new Error("Missing 'file_path' parameter");
  }

  const filePath = resolvePath(rawPath);
  if (!fs.existsSync(filePath)) {
    throw new Error(`File not found: ${filePath}`);
  }

  const markdown = fs.readFileSync(filePath, 'utf-8');
  const baseDir = path.dirname(filePath);

  let imagesEmbedded = 0;
  const imagesFailed: string[] = [];
  let totalSizeBytes = 0;

  const result = markdown.replace(IMAGE_REGEX, (match, alt: string, imgPath: string) => {
    if (isSkippable(imgPath)) return match;

    const absoluteImgPath = path.resolve(baseDir, imgPath);
    if (!fs.existsSync(absoluteImgPath)) {
      imagesFailed.push(imgPath);
      return match;
    }

    const embedded = embedImage(absoluteImgPath, imgPath, imagesFailed);
    if (!embedded) return match;

    totalSizeBytes += embedded.size;
    imagesEmbedded++;
    return `![${alt}](${embedded.dataUri})`;
  });

  // Write to output_path if specified, otherwise overwrite original
  const outputPath = args.output_path ? resolvePath(args.output_path) : filePath;
  fs.writeFileSync(outputPath, result, 'utf-8');

  return {
    output_path: outputPath,
    images_embedded: imagesEmbedded,
    images_failed: imagesFailed,
    total_size_bytes: totalSizeBytes,
  };
}

function resolvePath(rawPath: string): string {
  return path.isAbsolute(rawPath) ? rawPath : path.resolve(getWorkspaceRoot(), rawPath);
}

function isSkippable(imgPath: string): boolean {
  return imgPath.startsWith('http://') || imgPath.startsWith('https://') || imgPath.startsWith('data:');
}

function embedImage(
  absolutePath: string,
  originalPath: string,
  failures: string[]
): { dataUri: string; size: number } | null {
  const ext = path.extname(absolutePath).toLowerCase();
  const mimeType = MIME_TYPES[ext];
  if (!mimeType) {
    failures.push(`${originalPath} (unsupported: ${ext})`);
    return null;
  }
  try {
    const buffer = fs.readFileSync(absolutePath);
    const base64 = buffer.toString('base64');
    return { dataUri: `data:${mimeType};base64,${base64}`, size: buffer.length };
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    failures.push(`${originalPath} (${msg})`);
    return null;
  }
}
