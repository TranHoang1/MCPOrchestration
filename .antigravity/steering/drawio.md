---
name: drawio
description: Always use when user asks to create, generate, draw, or design a diagram, flowchart, architecture diagram, ER diagram, sequence diagram, class diagram, network diagram, mockup, wireframe, or UI sketch, or mentions draw.io, drawio, drawoi, .drawio files, or diagram export to PNG/SVG/PDF.
inclusion: manual
---

# Draw.io Diagram Skill

Generate draw.io diagrams as native `.drawio` files. Optionally export to PNG, SVG, or PDF with the diagram XML embedded (so the exported file remains editable in draw.io).

## How to create a diagram

1. **Generate draw.io XML** in mxGraphModel format for the requested diagram
2. **Write the XML** to a `.drawio` file in the current working directory using the Write tool
3. **Post-process edge routing** (optional): If `npx @drawio/postprocess` is available, run it on the `.drawio` file to optimize edge routing (simplify waypoints, fix edge-vertex collisions, straighten approach angles). Skip silently if not available — do not install it or ask the user about it
4. **If the user requested an export format** (png, svg, pdf), locate the draw.io CLI (see below), export with `--embed-diagram`, then delete the source `.drawio` file. If the CLI is not found, keep the `.drawio` file and tell the user they can install the draw.io desktop app to enable export, or open the `.drawio` file directly
5. **Open the result** — the exported file if exported, or the `.drawio` file otherwise. If the open command fails, print the file path so the user can open it manually

## Choosing the output format

Check the user's request for a format preference. Examples:

- `create a flowchart` → `flowchart.drawio`
- `png flowchart for login` → `login-flow.drawio.png`
- `svg: ER diagram` → `er-diagram.drawio.svg`
- `pdf architecture overview` → `architecture-overview.drawio.pdf`

If no format is mentioned, just write the `.drawio` file and open it in draw.io. The user can always ask to export later.

### Supported export formats

| Format | Embed XML | Notes |
|--------|-----------|-------|
| `png` | Yes (`-e`) | Viewable everywhere, editable in draw.io |
| `svg` | Yes (`-e`) | Scalable, editable in draw.io |
| `pdf` | Yes (`-e`) | Printable, editable in draw.io |
| `jpg` | No | Lossy, no embedded XML support |

PNG, SVG, and PDF all support `--embed-diagram` — the exported file contains the full diagram XML, so opening it in draw.io recovers the editable diagram.

## draw.io CLI

The draw.io desktop app includes a command-line interface for exporting.

### Locating the CLI (MANDATORY — MUST SEARCH BEFORE EXPORT)

**CRITICAL: You MUST dynamically locate the draw.io executable before attempting any export. Do NOT hardcode a path and assume it exists. Follow this discovery procedure:**

#### Step 1: Search for the executable

Run the appropriate search command for the detected environment:

**Windows (native or PowerShell):**
```powershell
# Try PATH first
where.exe draw.io 2>$null

# If not on PATH, search Program Files
Get-ChildItem "C:\Program Files" -Filter "draw.io.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
Get-ChildItem "C:\Program Files (x86)" -Filter "draw.io.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
```

**WSL2 (Windows Subsystem for Linux):**
```bash
# Detect WSL2
grep -qi microsoft /proc/version 2>/dev/null && echo "WSL2"
# Search via Windows path
ls "/mnt/c/Program Files/draw.io/draw.io.exe" 2>/dev/null
```

**macOS:**
```bash
ls /Applications/draw.io.app/Contents/MacOS/draw.io 2>/dev/null
which drawio 2>/dev/null
```

**Linux (native):**
```bash
which drawio 2>/dev/null
snap list drawio 2>/dev/null
```

#### Step 2: Store the discovered path

After finding the executable, store the full path in a variable and use it for ALL subsequent export commands. Example:

```powershell
$DRAWIO = "C:\Program Files\draw.io\draw.io.exe"  # discovered path
& $DRAWIO -x -f png -b 10 -o "output.png" "input.drawio"
```

#### Step 3: Handle "not found"

If the executable is NOT found after searching:
1. **Keep all `.drawio` files** — they are still editable in draw.io web or desktop
2. **Inform the user**: "draw.io desktop app not found. Install it from https://www.drawio.com/ to enable PNG/SVG/PDF export. The `.drawio` files can be opened directly in draw.io."
3. **Do NOT retry or ask the user to install** — just continue with the `.drawio` files
4. **Still embed Mermaid diagrams** in the markdown as fallback visualization

#### Common installation paths (for reference only — ALWAYS search dynamically)

| Environment | Typical Path |
|-------------|-------------|
| Windows | `C:\Program Files\draw.io\draw.io.exe` |
| WSL2 | `/mnt/c/Program Files/draw.io/draw.io.exe` |
| macOS | `/Applications/draw.io.app/Contents/MacOS/draw.io` |
| Linux (snap) | `/snap/bin/drawio` |
| Linux (apt/flatpak) | `drawio` (on PATH) |

### Export command

```bash
drawio -x -f <format> -e -b 10 -o <output> <input.drawio>
```

Key flags:
- `-x` / `--export`: export mode
- `-f` / `--format`: output format (png, svg, pdf, jpg)
- `-e` / `--embed-diagram`: embed diagram XML in the output (PNG, SVG, PDF only)
- `-o` / `--output`: output file path
- `-b` / `--border`: border width around diagram (default: 0)
- `-t` / `--transparent`: transparent background (PNG only)
- `-s` / `--scale`: scale the diagram size
- `--width` / `--height`: fit into specified dimensions (preserves aspect ratio)
- `-a` / `--all-pages`: export all pages (PDF only)
- `-p` / `--page-index`: select a specific page (1-based)

### Opening the result

| Environment | Command |
|-------------|---------|
| Windows | `start <file>` |
| macOS | `open <file>` |
| Linux (native) | `xdg-open <file>` |
| WSL2 | `cmd.exe /c start "" "$(wslpath -w <file>)"` |

## File naming

- Use a descriptive filename based on the diagram content (e.g., `login-flow`, `database-schema`)
- Use lowercase with hyphens for multi-word names
- For export, use double extensions: `name.drawio.png`, `name.drawio.svg`, `name.drawio.pdf` — this signals the file contains embedded diagram XML
- After a successful export, delete the intermediate `.drawio` file — the exported file contains the full diagram

## XML format

A `.drawio` file is native mxGraphModel XML. Always generate XML directly — Mermaid and CSV formats require server-side conversion and cannot be saved as native files.

### Basic structure

Every diagram must have this structure:

```xml
<mxGraphModel adaptiveColors="auto">
  <root>
    <mxCell id="0"/>
    <mxCell id="1" parent="0"/>
  </root>
</mxGraphModel>
```

- Cell `id="0"` is the root layer
- Cell `id="1"` is the default parent layer
- All diagram elements use `parent="1"` unless using multiple layers

---

# draw.io XML Reference (Embedded)

Detailed reference for styles, edge routing, containers, layers, tags, metadata, and dark mode. Consult this when generating draw.io XML diagrams.

## Reasoning budget (read this first)

Your job is to declare the **logical structure** of the diagram — what nodes exist, what edges connect them, what labels they carry, what lane/container groups them. draw.io's edge router and (when available) a post-layout pass handle routing and placement; you do **not** need to do layout math.

**Do NOT** in your reasoning:

- Do NOT use self-closing edge cells. **EVERY edge `<mxCell>` MUST have a child `<mxGeometry relative="1" as="geometry"/>`**. Writing `<mxCell id="e1" edge="1" ... />` (self-closing) causes arrows to NOT RENDER — they become invisible. Always use the expanded form with the geometry child element.
- Do NOT wrap XML in `<mxfile>` or `<diagram>` tags. Write bare `<mxGraphModel>` only — the `<mxfile>` wrapper causes export issues with the draw.io CLI.
- Do NOT debate the topic. The user asked for a flowchart / architecture / sequence / etc. — pick one concrete scenario on your first impulse and commit. Never write "Actually, let me think of something else…" or pitch alternatives.
- Do NOT debate flat-lanes vs nested-pools, horizontal vs vertical orientation, one vs multiple variations. Pick the first reasonable option (almost always: flat swimlanes, top-down or left-right based on what fits the content). Do not flip-flop.
- Do NOT compute x/y coordinates in prose. No "column spacings of 160px totaling 1840px width — that's too wide, let me tighten to 1700…" loops. Use the rigid grid below; do the arithmetic in your head and write the XML.
- Do NOT re-derive drawio mechanics (`horizontal=0`, `startSize=110`, nested-lane coordinates). Use the templates below as-is.
- Do NOT enumerate columns ("customer lane columns 0-10, web app 1-7"). Place a node, move on.
- Do NOT add `<Array as="points">` waypoints. Edges are routed automatically.
- Do NOT set `exitX` / `exitY` / `entryX` / `entryY` connection-point overrides unless you have specific geometric intent.
- Do NOT verify, re-check, or adjust coordinates after placing a node.
- Do NOT narrate "building the diagram / finalizing the XML / now let me…". Just emit XML.
- Do NOT write out lists of node positions as planning text. Emit them as `<mxCell>` elements directly.

**Do** in your reasoning:

- Identify the diagram type + actors/stages (1-2 short sentences).
- Identify any grouping (swimlanes? containers? none?).
- Go straight to XML.

**Rigid grid — use for every XML diagram:**

- Column x = `col_index * 220 + 60`  (col 0 = 60, col 1 = 280, col 2 = 500, …)
- Row y = `row_index * 160 + 60`     (row 0 = 60, row 1 = 220, row 2 = 380, …)
- Node size: rectangles `160×70`, diamonds `140×80`, circles `60×60`, documents `140×80`, cylinders `120×70`
- **Minimum gap between nodes: 80px** (horizontal and vertical) — this ensures edges have space to route without crossing nodes
- **For class diagrams**: use `col_index * 300 + 40` (wider spacing for large class boxes)
- **For state diagrams**: use `col_index * 250 + 60` (medium spacing)

Pick a `(col, row)` for each node. The wider spacing ensures orthogonal edges can route between nodes without overlapping them.

## General principles

- **Professional & consistent styling across ALL diagrams in a project** — use the same color palette, font sizes, and border styles for all diagrams in the same document set. This creates a cohesive, professional look.
- **Standard color palette for MCP Orchestration project:**
  - Primary components: `fillColor=#e1f5fe;strokeColor=#0288d1` (light blue)
  - Secondary components: `fillColor=#fff3e0;strokeColor=#f57c00` (light orange)
  - Services/Logic: `fillColor=#e8f5e9;strokeColor=#388e3c` (light green)
  - Data/Storage: `fillColor=#f3e5f5;strokeColor=#7b1fa2` (light purple)
  - External/Error: `fillColor=#fce4ec;strokeColor=#c62828` (light red)
  - Neutral: `fillColor=#f5f5f5;strokeColor=#666666` (light gray)
- **Font consistency:** `fontSize=11` for node labels, `fontSize=10` for edge labels, `fontSize=13;fontStyle=1` for titles
- **Edge consistency:** ALL edges in non-sequence diagrams use `edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;endArrow=classic;endSize=6;`
- **Node spacing:** Minimum 80px gap between any two nodes — edges need this space to route cleanly
- **Use proper draw.io shapes and connectors** — choose the semantically correct shape for each element (e.g., `shape=cylinder3` for databases and tanks, `rhombus` for decisions, `shape=mxgraph.pid2valves.*` for valves in P&IDs). draw.io has extensive shape libraries; prefer domain-appropriate shapes over generic rectangles.
- **Decide whether to search for shapes** — before generating a diagram, decide if it needs domain-specific shapes from draw.io's extended libraries. **Skip `search_shapes`** for standard diagram types that use basic geometric shapes: flowcharts, UML (class, sequence, state, activity), ERD, org charts, mind maps, Venn diagrams, timelines, wireframes, and any diagram using only rectangles, diamonds, circles, cylinders, and arrows. Also skip if the user explicitly asks to use basic/simple shapes or says not to search. **Use `search_shapes`** when the diagram requires industry-specific or branded icons: cloud architecture (AWS, Azure, GCP), network topology (Cisco, rack equipment), P&ID (valves, instruments, vessels), electrical/circuit diagrams, Kubernetes, BPMN with specific task types, or any domain where the user expects realistic/standardized symbols rather than labeled boxes.
- **Match the language of labels to the user's language** — if the user writes in German, French, Japanese, etc., all diagram labels, titles, and annotations should be in that same language.
- **MANDATORY: Validate edges before writing file** — After generating the XML, scan every `edge="1"` cell and confirm it has a `<mxGeometry relative="1" as="geometry"/>` child. If any edge is self-closing (`<mxCell ... edge="1" ... />`), fix it before writing. Self-closing edges are the #1 cause of invisible arrows in exported PNGs.
- **MANDATORY: Use bare `<mxGraphModel>` only** — Do NOT wrap XML in `<mxfile><diagram>...</diagram></mxfile>`. The draw.io CLI export works with bare `<mxGraphModel>` but may fail or produce corrupt output with the `<mxfile>` wrapper. Always start with `<mxGraphModel adaptiveColors="auto">` and end with `</mxGraphModel>`.

## Common styles

**Rounded rectangle:**
```xml
<mxCell id="2" value="Label" style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="1">
  <mxGeometry x="100" y="100" width="120" height="60" as="geometry"/>
</mxCell>
```

**Diamond (decision):**
```xml
<mxCell id="3" value="Condition?" style="rhombus;whiteSpace=wrap;html=1;" vertex="1" parent="1">
  <mxGeometry x="100" y="200" width="120" height="80" as="geometry"/>
</mxCell>
```

**Arrow (edge):**
```xml
<mxCell id="4" value="" style="edgeStyle=orthogonalEdgeStyle;html=1;" edge="1" source="2" target="3" parent="1">
  <mxGeometry relative="1" as="geometry"/>
</mxCell>
```

**Labeled arrow:**
```xml
<mxCell id="5" value="Yes" style="edgeStyle=orthogonalEdgeStyle;html=1;" edge="1" source="3" target="6" parent="1">
  <mxGeometry relative="1" as="geometry"/>
</mxCell>
```

## Style properties

| Property | Values | Use for |
|----------|--------|---------|
| `rounded=1` | 0 or 1 | Rounded corners |
| `whiteSpace=wrap` | wrap | Text wrapping |
| `fillColor=#dae8fc` | Hex color | Background color |
| `strokeColor=#6c8ebf` | Hex color | Border color |
| `fontColor=#333333` | Hex color | Text color |
| `shape=cylinder3` | shape name | Database cylinders |
| `shape=mxgraph.flowchart.document` | shape name | Document shapes |
| `ellipse` | style keyword | Circles/ovals |
| `rhombus` | style keyword | Diamonds |
| `edgeStyle=orthogonalEdgeStyle` | style keyword | Right-angle connectors |
| `edgeStyle=elbowEdgeStyle` | style keyword | Elbow connectors |
| `dashed=1` | 0 or 1 | Dashed lines |
| `swimlane` | style keyword | Swimlane containers |
| `group` | style keyword | Invisible container (pointerEvents=0) |
| `container=1` | 0 or 1 | Enable container behavior on any shape |
| `pointerEvents=0` | 0 or 1 | Prevent container from capturing child connections |
| `html=1` | 0 or 1 | Enable HTML rendering in labels (required for `<b>`, `<br>`, `<font>`, etc.) |
| `shape=umlLifeline;perimeter=lifelinePerimeter;size=16` | shape | UML sequence diagram lifeline (size = header height) |

## HTML labels

**Always include `html=1` in the style** when the `value` attribute contains any HTML tags (`<b>`, `<br>`, `<font>`, `<i>`, `<u>`, `<hr>`, `<p>`, `<table>`, etc.). Without `html=1`, HTML tags are displayed as literal text instead of being rendered.

HTML in attribute values must be **XML-escaped**: `<` → `&lt;`, `>` → `&gt;`, `&` → `&amp;`, `"` → `&quot;`

```xml
<mxCell value="&lt;b&gt;Title&lt;/b&gt;&lt;br&gt;Description"
        style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="1">
  <mxGeometry x="100" y="100" width="120" height="60" as="geometry"/>
</mxCell>
```

**Line breaks:** Use `&#xa;` (works with both `html=1` and `html=0`) or `&lt;br&gt;` (requires `html=1`) for line breaks — never use `\n`, which renders as literal backslash-n text instead of a newline.

**Best practice:** Always include `html=1` in every cell style. This ensures labels render correctly whether they contain HTML or plain text — plain text is unaffected by the flag.

**Bold/italic/underline:** Use `fontStyle` in the style string when the entire label should be bold (`fontStyle=1`), italic (`fontStyle=2`), or underline (`fontStyle=4`). Values can be combined via bitwise OR (e.g., `fontStyle=3` = bold+italic). Use HTML tags (`<b>`, `<i>`, `<u>`) only when formatting part of the label (e.g., bold title with normal description). Never combine `fontStyle` with HTML tags for the same effect — this is redundant and causes visible raw tags if `html=1` is missing.

## Edges

**CRITICAL: Every edge `mxCell` must contain a `<mxGeometry relative="1" as="geometry" />` child element.** Self-closing edge cells (e.g. `<mxCell ... edge="1" ... />`) are invalid and will not render correctly. Always use the expanded form:
```xml
<mxCell id="e1" edge="1" parent="1" source="a" target="b" style="...">
  <mxGeometry relative="1" as="geometry" />
</mxCell>
```

**Edge routing is automatic.** After the diagram renders, the viewer runs an ELK edge-routing pass that pins vertices and recomputes bend points + connection points. You do **not** need to:
- Add `<mxPoint>` waypoints
- Set `exitX` / `exitY` / `entryX` / `entryY`
- Route around obstacles
- Worry about edge-vertex collisions or parallel edge spacing

Just declare `source` and `target` and let ELK do the routing. The ELK pass also reverts itself if it made routing worse — so your edges are at worst unchanged, never worse.

**What you still choose: the edge style.** The style determines the overall look (orthogonal angles, curves, straight lines) — ELK honors the style family when routing.

| Style | Syntax | Best for |
|-------|--------|---------|
| **Orthogonal** | `edgeStyle=orthogonalEdgeStyle` | Flowcharts, architecture, network diagrams, BPMN — any diagram with right-angle connectors |
| **Straight** | no `edgeStyle` | UML class/sequence diagrams, direct point-to-point connections. For sequence diagram messages use `endSize=6;startSize=6;` to keep arrowheads small |
| **Entity Relation** | `edgeStyle=entityRelationEdgeStyle` | ER diagrams — creates perpendicular stubs at both ends |
| **Curved** | `curved=1` | Mind maps, informal diagrams |
| **Elbow** | `edgeStyle=elbowEdgeStyle;elbow=vertical;` | Rarely needed — `orthogonalEdgeStyle` handles almost all cases; use this only for simple 1-bend linear flows |

**Use a consistent edge style within each diagram.** Pick one based on diagram type and apply it to all edges: ER → `entityRelationEdgeStyle`; UML class → straight; mind maps → curved; flowcharts/architecture/network → `orthogonalEdgeStyle`.

**Useful edge style attributes** that apply regardless of routing:
- `rounded=1` — rounded corners at bend points (recommended for orthogonal)
- `endArrow=classic` / `endArrow=none` — arrow heads
- `dashed=1` — dashed line
- `strokeColor=#...`, `strokeWidth=2` — color/width
- Edge labels: set `value` directly on the edge cell

### Self-Call (UML Sequence Diagram)

**CRITICAL — Self-call arrows in sequence diagrams MUST point DOWNWARD, not upward.** A self-call represents an object calling its own method — the arrow goes RIGHT then DOWN then LEFT back to the lifeline.

**Correct pattern:**
```xml
<mxCell id="self1" value="validate()" style="html=1;endSize=6;startSize=6;" edge="1" parent="1">
  <mxGeometry relative="1" as="geometry">
    <mxPoint x="350" y="200" as="sourcePoint"/>
    <mxPoint x="350" y="230" as="targetPoint"/>
    <Array as="points">
      <mxPoint x="400" y="200"/>
      <mxPoint x="400" y="230"/>
    </Array>
  </mxGeometry>
</mxCell>
```

**Rules for self-call waypoints:**
- sourcePoint and targetPoint are BOTH on the lifeline (same X, different Y)
- sourcePoint Y = start of self-call, targetPoint Y = end of self-call (Y increases)
- Only **2 waypoints** — both at X = lifeline_x + 50
- First waypoint: `x = lifeline_x + 50`, `y = sourcePoint.y` (go RIGHT)
- Second waypoint: `x = lifeline_x + 50`, `y = targetPoint.y` (go DOWN)
- **NEVER add a 3rd waypoint** back to the lifeline — draw.io auto-connects to targetPoint
- **NEVER set targetPoint to the waypoint X** — targetPoint must be on the lifeline (same X as sourcePoint)

**Visual result (correct):**
```
    │
    ├──────┐  "validate()"
    │      │
    │◄─────┘
    │
```

**Common mistake (WRONG — arrow points UP):**
```
    │
    │◄─────┐  ← WRONG: arrow goes UP
    │      │
    ├──────┘
    │
```
This happens when waypoint Y values are in wrong order (decreasing instead of increasing).

### Self-Loop (State Diagram)

**For state diagrams**, a self-loop (state transitions back to itself, e.g., "Health check OK" on CONNECTED) uses a different pattern than sequence diagram self-calls:

**Correct pattern — loop on LEFT side of node:**
```xml
<mxCell id="self_loop" value="Health check OK" style="html=1;endArrow=classic;endSize=6;rounded=1;" edge="1" parent="1">
  <mxGeometry relative="1" as="geometry">
    <mxPoint x="170" y="340" as="sourcePoint"/>
    <mxPoint x="170" y="310" as="targetPoint"/>
    <Array as="points">
      <mxPoint x="110" y="340"/>
      <mxPoint x="110" y="310"/>
    </Array>
  </mxGeometry>
</mxCell>
```

**Rules for state diagram self-loops:**
- sourcePoint and targetPoint are on the SAME side of the node (left edge: x = node.x)
- sourcePoint Y slightly below center, targetPoint Y slightly above center
- 2 waypoints at X = node.x - 60 (go LEFT of node)
- Creates a visible loop arc on the left side
- **NEVER** set targetPoint below or outside the node — it must return to the same node

### ⛔ CRITICAL: Use source/target Cell IDs for Auto-Routing (State & Class Diagrams)

**For state diagrams and class diagrams, ALWAYS use `source` and `target` attributes referencing cell IDs** instead of `<mxPoint as="sourcePoint"/>`. This enables draw.io's automatic edge routing which avoids overlapping nodes.

**WRONG — absolute coordinates, no auto-routing (edges cross nodes):**
```xml
<mxCell id="e4" edge="1" style="html=1;endArrow=classic;" parent="1">
  <mxGeometry relative="1" as="geometry">
    <mxPoint x="310" y="330" as="sourcePoint"/>
    <mxPoint x="440" y="330" as="targetPoint"/>
  </mxGeometry>
</mxCell>
```

**CORRECT — cell references, auto-routing enabled (edges avoid nodes):**
```xml
<mxCell id="e4" value="Health check failed" style="edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;endArrow=classic;endSize=6;" edge="1" source="connected" target="disconnected" parent="1">
  <mxGeometry relative="1" as="geometry"/>
</mxCell>
```

**Rules:**
- State diagrams: ALL edges MUST use `source="stateId"` and `target="stateId"`
- Class diagrams: ALL edges MUST use `source="classId"` and `target="classId"`
- Use `edgeStyle=orthogonalEdgeStyle;rounded=1;` for clean right-angle routing
- draw.io ELK router automatically avoids crossing other nodes when source/target are cell IDs
- Self-loops: use `source="nodeId" target="nodeId"` — draw.io renders a proper loop arc automatically
- **Exception**: Sequence diagrams still use sourcePoint/targetPoint (lifelines are not standard cells)

**Self-loop with cell references (simplest — draw.io auto-renders loop):**
```xml
<mxCell id="e5" value="Health check OK" style="edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;endArrow=classic;endSize=6;" edge="1" source="connected" target="connected" parent="1">
  <mxGeometry relative="1" as="geometry"/>
</mxCell>
```

## Containers and groups

For architecture diagrams or any diagram with nested elements, use draw.io's proper parent-child containment — do **not** just place shapes on top of larger shapes.

### How containment works

Set `parent="containerId"` on child cells. Children use **relative coordinates** within the container.

### Container types

| Type | Style | When to use |
|------|-------|-------------|
| **Group** (invisible) | `group;` | No visual border needed, container has no connections. Includes `pointerEvents=0` so child connections are not captured |
| **Swimlane** (titled) | `swimlane;startSize=30;` | Container needs a visible title bar/header, or the container itself has connections |
| **Custom container** | Add `container=1;pointerEvents=0;` to any shape style | Any shape acting as a container without its own connections |

### Key rules

- **Edges to children inside containers naturally cross the container boundary** — this is correct and expected. Do not add extra waypoints or complex routing to avoid a parent container when connecting to shapes inside it.
- **Always add `pointerEvents=0;`** to container styles that should not capture connections being rewired between children
- Only omit `pointerEvents=0` when the container itself needs to be connectable — in that case, use `swimlane` style which handles this correctly (the client area is transparent for mouse events while the header remains connectable)
- Children must set `parent="containerId"` and use coordinates **relative to the container**

### Example: Architecture container with swimlane

```xml
<mxCell id="svc1" value="User Service" style="swimlane;startSize=30;fillColor=#dae8fc;strokeColor=#6c8ebf;html=1;" vertex="1" parent="1">
  <mxGeometry x="100" y="100" width="300" height="200" as="geometry"/>
</mxCell>
<mxCell id="api1" value="REST API" style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="svc1">
  <mxGeometry x="20" y="40" width="120" height="60" as="geometry"/>
</mxCell>
<mxCell id="db1" value="Database" style="shape=cylinder3;whiteSpace=wrap;html=1;" vertex="1" parent="svc1">
  <mxGeometry x="160" y="40" width="120" height="60" as="geometry"/>
</mxCell>
```

### Example: Invisible group container

```xml
<mxCell id="grp1" value="" style="group;" vertex="1" parent="1">
  <mxGeometry x="100" y="100" width="300" height="200" as="geometry"/>
</mxCell>
<mxCell id="c1" value="Component A" style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="grp1">
  <mxGeometry x="10" y="10" width="120" height="60" as="geometry"/>
</mxCell>
```

### Swimlanes for grouped actors (BPMN-style flowcharts)

Use **flat swimlanes** at `parent="1"`, stacked vertically. One row of nodes per lane.

**Fixed values — do not compute or debate:**
- Lane size: `x=0, y=lane_index*150, width=CANVAS_W, height=150`
- Lane style: `swimlane;horizontal=0;startSize=110;fillColor=<pastel>;html=1;`
- Child nodes inside a lane: `parent="<lane_id>"`, `x = 120 + col*180`, `y = 45` (always 45), size 140×60 (or 140×80 for diamonds)
- Cross-lane edges: `parent="1"` (not inside a lane)

Pick `CANVAS_W = max_col * 180 + 300`. Choose lane colors from `#f5f5f5, #e8f4f8, #fff0e6, #e8f5e9, #fff9e6, #fce4ec` in that order.

```xml
<mxCell id="lane1" value="Customer" style="swimlane;horizontal=0;startSize=110;fillColor=#f5f5f5;html=1;" vertex="1" parent="1">
  <mxGeometry x="0" y="0" width="1800" height="150" as="geometry"/>
</mxCell>
<mxCell id="n1" value="Place Order" style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="lane1">
  <mxGeometry x="120" y="45" width="140" height="60" as="geometry"/>
</mxCell>
<mxCell id="lane2" value="System" style="swimlane;horizontal=0;startSize=110;fillColor=#e8f4f8;html=1;" vertex="1" parent="1">
  <mxGeometry x="0" y="150" width="1800" height="150" as="geometry"/>
</mxCell>
<mxCell id="n2" value="Validate" style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="lane2">
  <mxGeometry x="300" y="45" width="140" height="60" as="geometry"/>
</mxCell>
<mxCell id="e1" edge="1" parent="1" source="n1" target="n2" style="edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;">
  <mxGeometry relative="1" as="geometry"/>
</mxCell>
```

Do NOT nest lanes inside a pool. Do NOT vary lane heights. Do NOT compute title-area offset — it is always 110, children start at x=120 to clear it.

### Nested architecture containers (cloud, infra, network topologies)

For diagrams with **nested groupings** — VPC → Availability Zone → EC2 instance, Datacenter → Rack → Server, Region → Environment → Service — use nested swimlanes. This is where the AI most often flattens hierarchy that should be nested. Treat each level as a swimlane container.

**Rules:**
- Every container is a `swimlane` with `startSize=24` (title area at the top).
- Child cells set `parent="<container_id>"` and use coordinates **relative to their parent** (origin 0,0 is the parent's top-left, below the title).
- Edges between cells in **different** containers must have `parent="1"` (not a container) — otherwise they render inside the container and get clipped.
- For industry-specific icons (AWS/Azure/GCP logos, Cisco equipment, etc.), call `search_shapes` to get the exact `style` string and substitute it into a regular vertex — the container structure stays the same.

```xml
<mxCell id="vpc" value="VPC" style="swimlane;startSize=24;fillColor=#dae8fc;strokeColor=#6c8ebf;html=1;" vertex="1" parent="1">
  <mxGeometry x="0" y="0" width="720" height="360" as="geometry"/>
</mxCell>
<mxCell id="az1" value="AZ us-east-1a" style="swimlane;startSize=24;fillColor=#fff2cc;strokeColor=#d6b656;html=1;" vertex="1" parent="vpc">
  <mxGeometry x="20" y="36" width="320" height="300" as="geometry"/>
</mxCell>
<mxCell id="web1" value="web-1" style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="az1">
  <mxGeometry x="30" y="40" width="120" height="60" as="geometry"/>
</mxCell>
<mxCell id="db1" value="db-1" style="shape=cylinder3;whiteSpace=wrap;html=1;" vertex="1" parent="az1">
  <mxGeometry x="180" y="40" width="100" height="70" as="geometry"/>
</mxCell>
<mxCell id="az2" value="AZ us-east-1b" style="swimlane;startSize=24;fillColor=#fff2cc;strokeColor=#d6b656;html=1;" vertex="1" parent="vpc">
  <mxGeometry x="360" y="36" width="340" height="300" as="geometry"/>
</mxCell>
<mxCell id="web2" value="web-2" style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="az2">
  <mxGeometry x="30" y="40" width="120" height="60" as="geometry"/>
</mxCell>
<mxCell id="e1" edge="1" parent="1" source="web1" target="web2" style="edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;">
  <mxGeometry relative="1" as="geometry"/>
</mxCell>
```

### Cross-functional flowcharts (actor × phase grid, as a table)

Cross-functional flowcharts show a process across **two axes at once** — actors (rows) and phases (columns). Use drawio's `table` shape, which auto-arranges cells into a grid via `childLayout=tableLayout`. This is the canonical draw.io pattern and is distinct from plain swimlanes (which only group on one axis).

**Structure:**
- Outer container: `shape=table;childLayout=tableLayout;startSize=0;collapsible=0;fillColor=none;`
- Rows are children of the table: `shape=tableRow;horizontal=0;startSize=0;collapsible=0;`
- Cells are children of rows — regular vertices, one per (actor, phase) intersection
- Row heights and cell widths are set via `mxGeometry`; they tile automatically
- First row = phase headers; first cell of every other row = actor label
- Process nodes go INSIDE the appropriate cell (parent = cell id) at coordinates relative to the cell
- Cross-cell edges must use `parent="1"` (same rule as containers)

```xml
<mxCell id="tbl" style="shape=table;childLayout=tableLayout;startSize=0;collapsible=0;fillColor=none;" vertex="1" parent="1">
  <mxGeometry x="0" y="0" width="900" height="320" as="geometry"/>
</mxCell>
<mxCell id="r0" style="shape=tableRow;horizontal=0;startSize=0;collapsible=0;" vertex="1" parent="tbl">
  <mxGeometry width="900" height="40" as="geometry"/>
</mxCell>
<mxCell id="h0" style="text;html=1;" vertex="1" parent="r0">
  <mxGeometry width="140" height="40" as="geometry"/>
</mxCell>
<mxCell id="h1" value="Order" style="text;align=center;fontStyle=1;fillColor=#e8e8e8;" vertex="1" parent="r0">
  <mxGeometry x="140" width="380" height="40" as="geometry"/>
</mxCell>
<mxCell id="h2" value="Fulfill" style="text;align=center;fontStyle=1;fillColor=#e8e8e8;" vertex="1" parent="r0">
  <mxGeometry x="520" width="380" height="40" as="geometry"/>
</mxCell>
<mxCell id="r1" style="shape=tableRow;horizontal=0;startSize=0;collapsible=0;" vertex="1" parent="tbl">
  <mxGeometry y="40" width="900" height="140" as="geometry"/>
</mxCell>
<mxCell id="a1" value="Customer" style="fillColor=#dae8fc;fontStyle=1;" vertex="1" parent="r1">
  <mxGeometry width="140" height="140" as="geometry"/>
</mxCell>
<mxCell id="c_cust_order" style="fillColor=none;" vertex="1" parent="r1">
  <mxGeometry x="140" width="380" height="140" as="geometry"/>
</mxCell>
<mxCell id="t_place" value="Place Order" style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="c_cust_order">
  <mxGeometry x="120" y="40" width="140" height="60" as="geometry"/>
</mxCell>
<mxCell id="c_cust_fulfill" style="fillColor=none;" vertex="1" parent="r1">
  <mxGeometry x="520" width="380" height="140" as="geometry"/>
</mxCell>
<mxCell id="r2" style="shape=tableRow;horizontal=0;startSize=0;collapsible=0;" vertex="1" parent="tbl">
  <mxGeometry y="180" width="900" height="140" as="geometry"/>
</mxCell>
<mxCell id="a2" value="System" style="fillColor=#d5e8d4;fontStyle=1;" vertex="1" parent="r2">
  <mxGeometry width="140" height="140" as="geometry"/>
</mxCell>
<mxCell id="c_sys_order" style="fillColor=none;" vertex="1" parent="r2">
  <mxGeometry x="140" width="380" height="140" as="geometry"/>
</mxCell>
<mxCell id="t_validate" value="Validate" style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="c_sys_order">
  <mxGeometry x="120" y="40" width="140" height="60" as="geometry"/>
</mxCell>
<mxCell id="c_sys_fulfill" style="fillColor=none;" vertex="1" parent="r2">
  <mxGeometry x="520" width="380" height="140" as="geometry"/>
</mxCell>
<mxCell id="t_ship" value="Ship" style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="c_sys_fulfill">
  <mxGeometry x="120" y="40" width="140" height="60" as="geometry"/>
</mxCell>
<mxCell id="e1" edge="1" parent="1" source="t_place" target="t_validate" style="edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;">
  <mxGeometry relative="1" as="geometry"/>
</mxCell>
<mxCell id="e2" edge="1" parent="1" source="t_validate" target="t_ship" style="edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;">
  <mxGeometry relative="1" as="geometry"/>
</mxCell>
```

**When to use cross-functional tables vs flat swimlanes:**
- Flat swimlanes — one-dimensional (actors only, or phases only). Simpler. Use this when you just need to show who does what in sequence.
- Cross-functional table — two-dimensional (actors AND phases). Use this when **both** the actor and the process stage matter, and every step belongs to a specific (actor, phase) cell.

**Do NOT** nest swimlanes inside a table row, do NOT set `startSize` on rows or cells (columns tile from `x=0`), and do NOT rely on the AI to produce exact widths that sum to the table width — close-enough totals are fine, the `tableLayout` normalizes them.

## Layers

Layers control visibility and z-order. Every cell belongs to exactly one layer. Use layers to manage diagram complexity — viewers can toggle layer visibility to show or hide groups of elements (e.g., "Physical Infrastructure" vs "Logical Network" vs "Security Zones").

Cell `id="0"` is the root and cell `id="1"` is the default layer — both always exist. Additional layers are `mxCell` elements with `parent="0"`:

```xml
<mxGraphModel>
  <root>
    <mxCell id="0"/>
    <mxCell id="1" parent="0"/>
    <mxCell id="2" value="Annotations" parent="0"/>
    <mxCell id="10" value="Server" style="rounded=1;html=1;" vertex="1" parent="1">
      <mxGeometry x="100" y="100" width="120" height="60" as="geometry"/>
    </mxCell>
    <mxCell id="20" value="Note: deprecated" style="text;" vertex="1" parent="2">
      <mxGeometry x="100" y="170" width="120" height="30" as="geometry"/>
    </mxCell>
  </root>
</mxGraphModel>
```

- A layer is an `mxCell` with `parent="0"` and no `vertex` or `edge` attribute
- Assign shapes to a layer by setting `parent` to the layer's id
- Later layers render on top of earlier layers (higher z-order)
- Add `visible="0"` as an attribute on the layer cell to hide it by default
- Use layers when the diagram has distinct conceptual groupings that viewers may want to toggle independently

## Tags

Tags are visual filters that let viewers show or hide elements by category. Unlike layers, a single element can have multiple tags, making tags ideal for cross-cutting concerns (e.g., tagging shapes as "critical", "v2", or "backend").

Tags require wrapping `mxCell` in an `<object>` element. Tags are assigned via the `tags` attribute as a space-separated string:

```xml
<mxGraphModel>
  <root>
    <mxCell id="0"/>
    <mxCell id="1" parent="0"/>
    <object id="2" label="Auth Service" tags="critical v2">
      <mxCell style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="1">
        <mxGeometry x="100" y="100" width="120" height="60" as="geometry"/>
      </mxCell>
    </object>
    <object id="3" label="Legacy API" tags="critical deprecated">
      <mxCell style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="1">
        <mxGeometry x="300" y="100" width="120" height="60" as="geometry"/>
      </mxCell>
    </object>
  </root>
</mxGraphModel>
```

- Tags require the `<object>` wrapper — a plain `mxCell` cannot have tags
- The `label` attribute on `<object>` replaces `value` on `mxCell`
- Tags are space-separated in the `tags` attribute
- Viewers filter the diagram by selecting tags in the draw.io UI (Edit > Tags)
- Tags do not affect z-order or structural grouping — they are purely a visibility filter

## Metadata and placeholders

Metadata stores custom key-value properties on shapes as additional attributes on the `<object>` wrapper element. Combined with placeholders, metadata values can be displayed in labels — useful for data-driven diagrams showing status, owner, IP addresses, or versions on each shape.

Set `placeholders="1"` on the `<object>` to enable `%propertyName%` substitution in the `label`:

```xml
<mxGraphModel>
  <root>
    <mxCell id="0"/>
    <mxCell id="1" parent="0"/>
    <object id="2" label="&lt;b&gt;%component%&lt;/b&gt;&lt;br&gt;Owner: %owner%&lt;br&gt;Status: %status%"
            placeholders="1" component="Auth Service" owner="Team Backend" status="Active">
      <mxCell style="rounded=1;whiteSpace=wrap;html=1;" vertex="1" parent="1">
        <mxGeometry x="100" y="100" width="160" height="80" as="geometry"/>
      </mxCell>
    </object>
  </root>
</mxGraphModel>
```

- Custom properties are plain XML attributes on `<object>` (e.g., `component="Auth Service"`)
- Set `placeholders="1"` to enable `%key%` substitution in the label and tooltip
- The label must use `html=1` style when using HTML formatting with placeholders
- Placeholders resolve by walking up the containment hierarchy: shape attributes first, then parent container, then layer, then root — first match wins
- Predefined placeholders work without custom properties: `%id%`, `%width%`, `%height%`, `%date%`, `%time%`, `%timestamp%`, `%page%`, `%pagenumber%`, `%pagecount%`, `%filename%`
- Use `%%` for a literal percent sign in labels
- Tags, metadata, and placeholders can all be combined on the same `<object>` element
- Use metadata when shapes represent data records (servers, services, components) and you want to attach structured information beyond the visible label

## Dark mode colors

draw.io supports automatic dark mode rendering. How colors behave depends on the property:

- **`strokeColor`, `fillColor`, `fontColor`** default to `"default"`, which renders as black in light theme and white in dark theme. When no explicit color is set, colors adapt automatically.
- **Explicit colors** (e.g. `fillColor=#DAE8FC`) specify the light-mode color. The dark-mode color is computed automatically by inverting the RGB values (blending toward the inverse at 93%) and rotating the hue by 180° (via `mxUtils.getInverseColor`).
- **`light-dark()` function** — To specify both colors explicitly, use `light-dark(lightColor,darkColor)` in the style string, e.g. `fontColor=light-dark(#7EA6E0,#FF0000)`. The first argument is used in light mode, the second in dark mode.

To enable dark mode color adaptation, the `mxGraphModel` element must include `adaptiveColors="auto"`.

When generating diagrams, you generally do not need to specify dark-mode colors — the automatic inversion handles most cases. Use `light-dark()` only when the automatic inverse color is unsatisfactory.

## Automatic edge routing

Every XML diagram rendered in the viewer automatically runs an ELK (Eclipse Layout Kernel) edge-routing pass **after** the initial render:

1. Vertex positions are pinned (the AI's placement is respected — no vertex moves).
2. ELK recomputes bend points + connection points for every edge (orthogonal routing).
3. A metric (edge-vertex intersections) compares before vs. after. If ELK made collisions worse, the edge routing is reverted to your original.
4. The exported XML (copy/clipboard, "Open in draw.io") reflects whatever is finally shown — so downstream consumers also get the cleaned-up edges.

You do not need to request this. Place vertices where they belong and write edges naively — the viewer handles connector cleanup.

This also means: there is no server-side post-processing pass. What you generate is what the viewer starts with; the ELK pass is the only correction.

## Post-layout (optional, overrides vertex positions)

For cases where you want a **full** re-layout — moving vertices to canonical positions — set the optional `postLayout` parameter on `create_diagram`. Vertices animate (morph) from their original positions to the algorithm's layout.

| Value | ELK algorithm | Best for |
|-------|---------------|----------|
| `verticalFlow` | `layered` (DOWN) | Flowcharts, process diagrams |
| `horizontalFlow` | `layered` (RIGHT) | Pipelines, swim lanes |
| `tree` | `mrtree` | Org charts, decision trees, hierarchies |
| `force` | `force` | Networks without clear hierarchy |
| `stress` | `stress` | Small-to-mid general graphs (usually tighter than force) |
| `radial` | `radial` | Concentric layers around a root |

**Usually omit `postLayout`.** The automatic edge-routing pass above handles the common case. Only set `postLayout` when the user explicitly wants a canonical layout, or when you know vertex placement is significantly off.

**When NOT to use:**
- The user has asked for specific positions (swim lanes with exact lanes, architecture diagrams with meaningful spatial arrangement).
- The diagram relies on containers/grouping where spatial layout encodes information.
- For Mermaid diagrams — the native Mermaid layout already runs through full ELK; `postLayout` would override that with a different algorithm.

## Style reference

Complete style reference (all shape types, style properties, color palettes, HTML labels, and more): https://github.com/jgraph/drawio-mcp/blob/main/shared/style-reference.md

XML Schema (XSD): https://github.com/jgraph/drawio-mcp/blob/main/shared/mxfile.xsd

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| draw.io CLI not found | Desktop app not installed or not on PATH | Keep the `.drawio` file and tell the user to install the draw.io desktop app, or open the file manually |
| Export produces empty/corrupt file | Invalid XML (e.g. double hyphens in comments, unescaped special characters) | Validate XML well-formedness before writing; see the XML well-formedness section below |
| Diagram opens but looks blank | Missing root cells `id="0"` and `id="1"` | Ensure the basic mxGraphModel structure is complete |
| Edges not rendering | Edge mxCell is self-closing (no child mxGeometry element) | Every edge must have `<mxGeometry relative="1" as="geometry" />` as a child element |
| Self-call arrow points wrong direction | Waypoint Y values in wrong order (decreasing instead of increasing) | Second waypoint Y must be GREATER than first waypoint Y |
| Alt/fragment box has no arrows inside | Only text label placed, no actual edge cells inside the fragment | Add dashed return arrow inside alt box showing error response |
| File won't open after export | Incorrect file path or missing file association | Print the absolute file path so the user can open it manually |

## ⛔ MANDATORY: Post-Generation Verification & Retry

**After creating ANY .drawio file, the agent MUST verify the output and fix issues before reporting completion.**

### Verification Steps (MANDATORY after every diagram)

1. **Export to PNG** — run draw.io CLI export
2. **Validate XML patterns** — scan the .drawio file for known issues:

| Check | Pattern to Detect | Fix |
|-------|------------------|-----|
| Self-closing edges | `edge="1"` followed by `/>` on same line (no `<mxGeometry>` child) | Add `<mxGeometry relative="1" as="geometry"/>` child |
| Self-call wrong direction | Edge with `source` = `target` AND waypoint Y[1] < Y[0] | Swap waypoint Y values so Y increases (arrow goes DOWN) |
| Alt/fragment box empty | Rectangle with "alt" label but no edge cells with source/target inside its bounds | Add dashed return arrow inside the fragment |
| `<mxfile>` wrapper | File starts with `<mxfile>` instead of `<mxGraphModel>` | Strip wrapper, keep only `<mxGraphModel>` content |
| Missing lifeline | Sequence diagram header without corresponding lifeline cell | Add `shape=umlLifeline` cell below header |

3. **If any issue found → FIX immediately** — do NOT report completion with known issues
4. **Re-export PNG** after fix
5. **Verify PNG file size** — if PNG is < 1 KB, the export likely failed (blank image). Re-check XML.

### Retry Policy

```
attempts = 0
max_attempts = 3

while issues_found AND attempts < max_attempts:
    1. Fix identified issues in .drawio XML
    2. Re-write .drawio file
    3. Re-export to PNG
    4. Re-validate
    attempts++

if attempts >= max_attempts AND issues still exist:
    Report to user: "⚠️ Diagram {name} has known issues after {max_attempts} fix attempts: {list issues}"
```

### Export Timeout Handling

**draw.io CLI can timeout on complex diagrams** (many elements, large canvas). When this happens:

1. **Increase timeout** — add `--timeout 30000` (30 seconds) to export command:
   ```powershell
   & "C:\Program Files\draw.io\draw.io.exe" -x -f png -b 10 --no-sandbox --timeout 30000 -o "output.png" "input.drawio"
   ```

   **⚠️ ALWAYS use `--no-sandbox` on Windows** — without it, draw.io CLI silently fails (exit code 0 but no PNG created) for complex diagrams.

2. **If still times out — simplify the diagram:**
   - Split into 2 smaller diagrams (e.g., happy path + error cases)
   - Remove decorative elements (notes, alt boxes with only text)
   - Reduce number of lifelines (combine related components)

3. **If alt/fragment boxes cause timeout:**
   - Replace complex `shape=mxgraph.uml25.frame` with simple `rounded=0;dashed=1;strokeColor=#FF0000;fillColor=none;` rectangle
   - Move error flow to a separate diagram: `{name}-error-cases.drawio`

4. **Verify PNG after export:**
   - Check file size: if < 5 KB for a sequence diagram → likely incomplete render
   - If incomplete: re-export with `--timeout 60000` or simplify

5. **Report to user if export fails after retries:**
   ```
   "⚠️ draw.io export timed out for {name}.drawio. The .drawio file is correct but PNG may be incomplete.
   Options: (1) Open .drawio in draw.io desktop to verify, (2) Split into smaller diagrams."
   ```

### Common Sequence Diagram Issues & Fixes

| Issue | Root Cause | Fix |
|-------|-----------|-----|
| Self-call arrow points UP (↗) | Waypoints: `[{x:560, y:310}, {x:560, y:280}]` — Y decreases | Change to: `[{x:560, y:280}, {x:560, y:310}]` — Y increases |
| Alt box has no error arrow | Only placed rectangle + text, forgot edge | Add: `<mxCell edge="1" source="dispatcher_lifeline" target="kiro_lifeline" style="dashed=1;strokeColor=#FF0000;">` inside alt bounds |
| Messages overlap | Multiple edges at same Y coordinate | Space messages 40-50px apart vertically |
| Lifeline too short | Lifeline height < last message Y | Extend lifeline height to cover all messages + 50px margin |

## CRITICAL: XML well-formedness

- **NEVER include ANY XML comments (`<!-- -->`) in the output.** XML comments are strictly forbidden — they waste tokens, can cause parse errors, and serve no purpose in diagram XML.
- Escape special characters in attribute values: `&amp;`, `&lt;`, `&gt;`, `&quot;`
- Always use unique `id` values for each `mxCell`

## Branching edges — avoiding overlapping arrows (IMPORTANT)

When a decision diamond (rhombus) has multiple outgoing edges that fan out to different targets, **do NOT rely on `edgeStyle=orthogonalEdgeStyle` with `exitX/exitY` overrides** — the auto-router often collapses them into overlapping paths, especially when multiple edges later converge back to a single target.

### The problem

If you use `edgeStyle=orthogonalEdgeStyle` on all branching edges from a diamond, the auto-router picks similar paths and the arrows overlap visually, making the diagram unreadable. This is especially bad when 3+ branches fan out and then reconverge.

### The solution — explicit waypoints

For branching/converging edges, **drop `edgeStyle=orthogonalEdgeStyle`** and use **explicit `<mxPoint>` waypoints** inside `<Array as="points">` to control the exact path:

```xml
<mxCell id="ee" value="Create" edge="1" parent="1" source="diamond1" target="boxLeft" style="html=1;rounded=1;">
  <mxGeometry relative="1" as="geometry">
    <Array as="points">
      <mxPoint x="150" y="550"/>
      <mxPoint x="150" y="650"/>
    </Array>
  </mxGeometry>
</mxCell>

<mxCell id="ef" value="Update" edge="1" parent="1" source="diamond1" target="boxCenter" style="edgeStyle=orthogonalEdgeStyle;html=1;rounded=1;">
  <mxGeometry relative="1" as="geometry"/>
</mxCell>

<mxCell id="eg" value="Skip" edge="1" parent="1" source="diamond1" target="boxRight" style="html=1;rounded=1;">
  <mxGeometry relative="1" as="geometry">
    <Array as="points">
      <mxPoint x="750" y="550"/>
      <mxPoint x="750" y="650"/>
    </Array>
  </mxGeometry>
</mxCell>
```

### Rules for branching/converging patterns

1. **Center branch** (straight down from diamond): Use `edgeStyle=orthogonalEdgeStyle` — auto-routing works fine for the straight path.
2. **Left and right branches**: Drop `edgeStyle`, use explicit waypoints to route the edge clearly to the left/right target. Pick x-coordinates that align with the target's center.
3. **Converging edges** (multiple sources → single target): Same approach — use explicit waypoints so each edge arrives from a distinct direction. Route left sources down-then-right, right sources down-then-left, center source straight down.
4. **Spacing**: Keep at least 200px horizontal gap between branch targets to give arrows room.
5. **Waypoint y-coordinate**: Place the horizontal segment of the waypoint at least 50px below the diamond's bottom edge, so the label has room.

### When to use this pattern

- Any diamond with 3+ outgoing edges
- Any point where 3+ edges converge into one target
- Flowcharts with decision matrices (e.g., ContactFlag: Create / Update / Skip)
- Activity diagrams with parallel branches merging

### When NOT needed

- Simple 2-way branches (Yes/No) — `edgeStyle=orthogonalEdgeStyle` with default routing handles these fine
- Linear flows (A → B → C) — auto-routing is sufficient
