/**
 * Local embed_images tool that reads a markdown file and embeds all image references as base64.
 * Runs on the bridge side (client machine) for local file operations.
 *
 * Input: { file_path: string } — absolute path to a .md file
 * Output: { markdown: string, images_embedded: number, images_failed: string[] }
 *
 * Replaces: ![alt](relative/path.png) → ![alt](data:image/png;base64,...)
 */

import * as fs from 'node:fs';
import * as path from 'node:path';

export interface EmbedImagesArgs {
  file_path: string;
}

export interface EmbedImagesResult {
  markdown: string;
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

export function handleEmbedImages(args: EmbedImagesArgs): EmbedImagesResult {
  const filePath = args.file_path;

  if (!filePath) {
    throw new Error("Missing 'file_path' parameter");
  }

  if (!fs.existsSync(filePath)) {
    throw new Error(`File not found: ${filePath}`);
  }

  const markdown = fs.readFileSync(filePath, 'utf-8');
  const baseDir = path.dirname(filePath);

  let imagesEmbedded = 0;
  const imagesFailed: string[] = [];
  let totalSizeBytes = 0;

  // Regex to match markdown image references: ![alt](path)
  // Excludes URLs (http://, https://, data:)
  const imageRegex = /!\[([^\]]*)\]\(([^)]+)\)/g;

  const result = markdown.replace(imageRegex, (match, alt: string, imgPath: string) => {
    // Skip URLs and already-embedded images
    if (imgPath.startsWith('http://') || imgPath.startsWith('https://') || imgPath.startsWith('data:')) {
      return match;
    }

    // Resolve relative path from markdown file location
    const absoluteImgPath = path.resolve(baseDir, imgPath);

    if (!fs.existsSync(absoluteImgPath)) {
      imagesFailed.push(imgPath);
      return match; // Keep original reference if file not found
    }

    try {
      const ext = path.extname(absoluteImgPath).toLowerCase();
      const mimeType = MIME_TYPES[ext];

      if (!mimeType) {
        imagesFailed.push(`${imgPath} (unsupported format: ${ext})`);
        return match;
      }

      const imageBuffer = fs.readFileSync(absoluteImgPath);
      const base64 = imageBuffer.toString('base64');
      const dataUri = `data:${mimeType};base64,${base64}`;

      totalSizeBytes += imageBuffer.length;
      imagesEmbedded++;

      return `![${alt}](${dataUri})`;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      imagesFailed.push(`${imgPath} (${msg})`);
      return match;
    }
  });

  return {
    markdown: result,
    images_embedded: imagesEmbedded,
    images_failed: imagesFailed,
    total_size_bytes: totalSizeBytes,
  };
}
