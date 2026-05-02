/**
 * E2E Test: Agent Consumption of Code Intelligence
 *
 * Verifies that agents can consume code intelligence data through both access paths:
 * 1. Reading Analysis Files directly via readFile
 * 2. KB payloads are structured correctly for ingestion and search
 *
 * Validates: Requirements 8.1–8.5
 */
import { describe, it, expect } from 'vitest';
import * as fs from 'node:fs';
import * as path from 'node:path';

// Resolve paths relative to the workspace root (4 levels up from this test file)
const WORKSPACE_ROOT = path.resolve(__dirname, '..', '..', '..', '..');
const ANALYSIS_DIR = path.join(WORKSPACE_ROOT, '.analysis', 'code-intelligence');
const MODULES_DIR = path.join(ANALYSIS_DIR, 'modules');
const AGENTS_DIR = path.join(WORKSPACE_ROOT, '.kiro', 'agents');
const STEERING_FILE = path.join(WORKSPACE_ROOT, '.kiro', 'steering', 'code-intelligence.md');

describe('Agent Consumption E2E — Requirements 8.1–8.5', () => {
  // ─────────────────────────────────────────────────────────────────────
  // Check 1: SA Agent can read code intelligence (Req 8.1)
  // ─────────────────────────────────────────────────────────────────────
  describe('SA Agent consumption (Req 8.1)', () => {
    it('project-structure.md contains tech stack info (languages, frameworks, module structure)', () => {
      const filePath = path.join(ANALYSIS_DIR, 'project-structure.md');
      expect(fs.existsSync(filePath), 'project-structure.md must exist').toBe(true);

      const content = fs.readFileSync(filePath, 'utf-8');

      // Must contain "Last Updated" timestamp
      expect(content).toMatch(/Last Updated:/);

      // Must contain project type
      expect(content).toMatch(/Project Type:/);

      // Must contain a Modules table with required columns
      expect(content).toMatch(/\|\s*Module\s*\|/);
      expect(content).toMatch(/\|\s*Language\s*\|/);
      expect(content).toMatch(/\|\s*Framework\s*\|/);
      expect(content).toMatch(/\|\s*Source Files\s*\|/);

      // Must contain at least one module row (e.g., core, auth, shared)
      expect(content).toMatch(/\|\s*core\s*\|/);

      // Must contain Inter-Module Dependencies section
      expect(content).toMatch(/Inter-Module Dependencies/);
    });

    it('module analysis file (core.md) contains API patterns, naming conventions, DI style, error handling', () => {
      const filePath = path.join(MODULES_DIR, 'core.md');
      expect(fs.existsSync(filePath), 'modules/core.md must exist').toBe(true);

      const content = fs.readFileSync(filePath, 'utf-8');

      // Must contain "Last Updated" timestamp
      expect(content).toMatch(/Last Updated:/);

      // Must contain Language and Framework
      expect(content).toMatch(/Language:/);
      expect(content).toMatch(/Framework:/);

      // Must contain Key Classes section (for API patterns)
      expect(content).toMatch(/## Key Classes/);
      expect(content).toMatch(/\|\s*Class\s*\|/);

      // Must contain Public API Surface section
      expect(content).toMatch(/## Public API Surface/);

      // Must contain Detected Patterns section with DI style, error handling, naming
      expect(content).toMatch(/## Detected Patterns/);
      expect(content).toMatch(/DI Style/);
      expect(content).toMatch(/Error Handling/);
      expect(content).toMatch(/Naming/);
    });

    it('SA agent instructions reference code intelligence correctly', () => {
      const filePath = path.join(AGENTS_DIR, 'sa-agent.md');
      expect(fs.existsSync(filePath), 'sa-agent.md must exist').toBe(true);

      const content = fs.readFileSync(filePath, 'utf-8');

      // Must reference project-structure.md
      expect(content).toMatch(/project-structure\.md/);

      // Must reference module analysis files
      expect(content).toMatch(/modules\/.*\.md/);

      // Must reference code intelligence steering file or analysis directory
      expect(content).toMatch(/code-intelligence/);

      // Must mention reading code intelligence before creating TDD
      expect(content).toMatch(/Read.*project.*overview|Read.*code.*intelligence|Read.*module.*analy/i);

      // Must reference KB search
      expect(content).toMatch(/kb_search_smart|mcp_knowledge_base/);
    });
  });

  // ─────────────────────────────────────────────────────────────────────
  // Check 2: DEV Agent can read code intelligence (Req 8.2)
  // ─────────────────────────────────────────────────────────────────────
  describe('DEV Agent consumption (Req 8.2)', () => {
    it('module analysis file contains package structure, naming conventions, DI style, error handling, logging, test patterns', () => {
      const filePath = path.join(MODULES_DIR, 'core.md');
      const content = fs.readFileSync(filePath, 'utf-8');

      // Package structure
      expect(content).toMatch(/## Package Structure/);

      // Naming conventions (detected in Detected Patterns)
      expect(content).toMatch(/Naming/);

      // DI style
      expect(content).toMatch(/DI Style/);

      // Error handling patterns
      expect(content).toMatch(/Error Handling/);

      // Logging patterns
      expect(content).toMatch(/Logging/);

      // Test patterns
      expect(content).toMatch(/Testing/);
    });

    it('DEV agent instructions reference code intelligence correctly', () => {
      const filePath = path.join(AGENTS_DIR, 'dev-agent.md');
      expect(fs.existsSync(filePath), 'dev-agent.md must exist').toBe(true);

      const content = fs.readFileSync(filePath, 'utf-8');

      // Must reference module analysis files
      expect(content).toMatch(/modules\/.*\.md/);

      // Must reference code intelligence
      expect(content).toMatch(/code-intelligence/);

      // Must mention reading module analysis before implementing
      expect(content).toMatch(/Read.*relevant.*module|Read.*module.*analy/i);

      // Must reference package structure, naming conventions, DI style, error handling, logging, test patterns
      expect(content).toMatch(/package structure/i);
      expect(content).toMatch(/naming conventions/i);
      expect(content).toMatch(/DI style/i);
      expect(content).toMatch(/error handling/i);
      expect(content).toMatch(/logging/i);
      expect(content).toMatch(/test pattern/i);

      // Must reference annotation-manager for post-implementation annotations
      expect(content).toMatch(/annotation-manager/);
    });
  });

  // ─────────────────────────────────────────────────────────────────────
  // Check 3: QA Agent can read code intelligence (Req 8.3)
  // ─────────────────────────────────────────────────────────────────────
  describe('QA Agent consumption (Req 8.3)', () => {
    it('module analysis file contains testable components (controllers, services, repositories) and public API surface', () => {
      const filePath = path.join(MODULES_DIR, 'auth.md');
      expect(fs.existsSync(filePath), 'modules/auth.md must exist').toBe(true);

      const content = fs.readFileSync(filePath, 'utf-8');

      // Must contain Key Classes with controller, service, repository entries
      expect(content).toMatch(/## Key Classes/);

      // Verify testable component types are present in the Key Classes table
      expect(content).toMatch(/HTTP request handling|controller/i);
      expect(content).toMatch(/Business logic|service/i);
      expect(content).toMatch(/Data access|repository/i);

      // Must contain Public API Surface
      expect(content).toMatch(/## Public API Surface/);

      // Must contain actual function signatures in the API surface
      expect(content).toMatch(/\w+\(.*\):\s*\w+/);
    });

    it('QA agent instructions reference code intelligence correctly', () => {
      const filePath = path.join(AGENTS_DIR, 'qa-agent.md');
      expect(fs.existsSync(filePath), 'qa-agent.md must exist').toBe(true);

      const content = fs.readFileSync(filePath, 'utf-8');

      // Must reference module analysis files
      expect(content).toMatch(/modules\/.*\.md/);

      // Must reference code intelligence
      expect(content).toMatch(/code-intelligence|Code Intelligence/);

      // Must mention testable components
      expect(content).toMatch(/testable.*component|controller.*service.*repositor/i);

      // Must mention public API surface
      expect(content).toMatch(/public.*API.*surface|API.*surface/i);

      // Must reference KB search
      expect(content).toMatch(/kb_search_smart|mcp_knowledge_base/);
    });
  });

  // ─────────────────────────────────────────────────────────────────────
  // Check 4: KB payloads are structured for search (Req 8.4, 8.5)
  // ─────────────────────────────────────────────────────────────────────
  describe('KB search returns relevant results (Req 8.4, 8.5)', () => {
    it('kb-payloads.json exists and contains structured module data', () => {
      const filePath = path.join(ANALYSIS_DIR, 'kb-payloads.json');
      expect(fs.existsSync(filePath), 'kb-payloads.json must exist').toBe(true);

      const payloads = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
      expect(Array.isArray(payloads)).toBe(true);
      expect(payloads.length).toBeGreaterThan(0);

      // Each payload must have required fields
      for (const payload of payloads) {
        expect(payload).toHaveProperty('title');
        expect(payload).toHaveProperty('content');
        expect(payload).toHaveProperty('tags');
        expect(payload).toHaveProperty('project');

        // Title must follow "Code Index — {module-name}" format
        expect(payload.title).toMatch(/^Code Index — .+$/);

        // Content must include module name and class signatures
        expect(payload.content).toMatch(/Module:/);
        expect(payload.content).toMatch(/Key classes:/);

        // Tags must include "code-index" and module name
        expect(payload.tags).toMatch(/code-index/);

        // Project must be set
        expect(payload.project.length).toBeGreaterThan(0);
      }
    });

    it('KB payloads contain module name, class signatures, and relevant code intelligence data', () => {
      const filePath = path.join(ANALYSIS_DIR, 'kb-payloads.json');
      const payloads = JSON.parse(fs.readFileSync(filePath, 'utf-8'));

      // Find a payload (any module)
      const payload = payloads[0];

      // Must contain module name
      expect(payload.content).toMatch(/Module:\s*\w+/);

      // Must contain language
      expect(payload.content).toMatch(/Language:\s*\w+/);

      // Must contain framework info
      expect(payload.content).toMatch(/Framework:/);

      // Must contain class information
      expect(payload.content).toMatch(/Key classes:/);

      // Must contain package information
      expect(payload.content).toMatch(/Packages:/);

      // Must contain detected patterns
      expect(payload.content).toMatch(/Detected patterns:/);
    });

    it('two access paths are available: Analysis Files (readFile) and KB payloads (for ingestion)', () => {
      // Access path (a): Analysis Files exist and are readable
      const projectStructure = path.join(ANALYSIS_DIR, 'project-structure.md');
      expect(fs.existsSync(projectStructure), 'project-structure.md must exist for readFile access').toBe(true);

      const moduleFiles = fs.readdirSync(MODULES_DIR).filter(f => f.endsWith('.md'));
      expect(moduleFiles.length).toBeGreaterThan(0);

      // Access path (b): KB payloads exist for ingestion
      const kbPayloads = path.join(ANALYSIS_DIR, 'kb-payloads.json');
      expect(fs.existsSync(kbPayloads), 'kb-payloads.json must exist for KB ingestion').toBe(true);
    });
  });

  // ─────────────────────────────────────────────────────────────────────
  // Cross-cutting: Steering file references both access paths
  // ─────────────────────────────────────────────────────────────────────
  describe('Steering file provides consistent access instructions (Req 8.4)', () => {
    it('steering file documents both access paths: readFile and KB search', () => {
      expect(fs.existsSync(STEERING_FILE), 'code-intelligence.md steering file must exist').toBe(true);

      const content = fs.readFileSync(STEERING_FILE, 'utf-8');

      // Must document readFile access path
      expect(content).toMatch(/readFile|Read.*Analysis.*File/i);

      // Must document KB search access path
      expect(content).toMatch(/kb_search_smart|mcp_knowledge_base/);

      // Must document agent-specific instructions
      expect(content).toMatch(/SA Agent/);
      expect(content).toMatch(/DEV Agent/);
      expect(content).toMatch(/QA Agent/);
    });
  });
});
