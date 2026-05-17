/**
 * Code Intelligence module entry point for Node.js bridge.
 * Initializes database, provides tool handlers, manages background indexing.
 */

export { initializeDatabase, CodeIntelDb } from './database.js';
export { scanWorkspace, hashFile, detectLanguage, ScannedFile } from './scanner.js';
export { extractSymbols, ExtractedSymbol } from './extractor.js';
export {
  handleCodeSearch,
  handleCodeSymbols,
  handleCodeContext,
  handleCodeModules,
  handleCodeIndexStatus,
} from './tools.js';
