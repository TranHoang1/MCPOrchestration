# UI Specification — MTO-94 Per-User Credentials

| Field | Value |
|-------|-------|
| Ticket | MTO-94 |
| Version | 1.0 |
| Created | 2025-07-07 |
| Status | Draft |

---

## 1. Overview

This document specifies the UI design for the Per-User Credentials feature (MTO-94). The feature adds three pages to the MCP Orchestrator Admin Portal:

1. **Login Page** (`/login`) — Authentication entry point
2. **Profile Page** (`/profile`) — User info, bridge token, server credentials
3. **Admin Schemas Page** (`/admin/schemas`) — Credential schema management (admin only)

## 2. Design System

### 2.1 Color Palette

| Token | Value | Usage |
|-------|-------|-------|
| `--bg` | `#1a1a2e` | Page background |
| `--surface` | `#16213e` | Card/section background |
| `--card` | `#0f3460` | Nested card background |
| `--accent` | `#e94560` | Primary action, errors |
| `--success` | `#4caf50` | Success states |
| `--warning` | `#ff9800` | Warning states |
| `--text` | `#eaeaea` | Primary text |
| `--muted` | `#8892b0` | Secondary text, labels |

### 2.2 Typography

- **Font family:** System font stack (`-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif`)
- **Headings:** 1.4rem (page title), 1.1rem (section title)
- **Body:** 0.9rem
- **Small:** 0.85rem (labels, badges)
- **Monospace:** System monospace (token display)

### 2.3 Spacing & Layout

- **Border radius:** 8px (all cards, buttons, inputs)
- **Page max-width:** 900px (profile), 420px (login)
- **Section padding:** 24px
- **Card gap:** 20px between sections

### 2.4 Components

| Component | Style | States |
|-----------|-------|--------|
| Button Primary | `--accent` bg, white text | default, hover (0.85 opacity), disabled (0.5) |
| Button Success | `--success` bg, white text | default, hover, disabled |
| Button Muted | `--card` bg, border `--muted` | default, hover |
| Button Danger | `#c62828` bg, white text | default, hover |
| Input | `--bg` bg, `--card` border | default, focus (`--accent` border) |
| Badge | Rounded pill, colored bg | complete (green), partial (orange), none (red) |
| Alert | Colored border + bg | success (green), error (red) |

---

## 3. Page Specifications

### 3.1 Login Page (`/login`)

#### Layout
- Centered vertically and horizontally
- Single card (420px max-width)
- Dark background with subtle shadow

#### Sections
1. **Header** — Logo emoji (🔐), title "MCP Orchestrator", subtitle "Sign in to continue"
2. **Error Alert** — Hidden by default, shows on login failure
3. **Login Form** — Username input, Password input (with show/hide toggle), Submit button
4. **SSO Section** — Divider "or", SSO button (hidden if SSO not configured)

#### Interactions

| Action | Trigger | Result |
|--------|---------|--------|
| Enable submit | Both fields non-empty | Button becomes clickable |
| Submit form | Click "Sign In" or Enter | POST `/api/auth/login`, show spinner |
| Login success | 200 response | Store token, redirect to `/profile` |
| Login failure | Non-200 response | Show error alert with message, clear password |
| Toggle password | Click "Show"/"Hide" | Toggle input type password/text |
| SSO login | Click SSO button | Redirect to `/api/auth/sso/authorize` |
| Auto-redirect | Token exists in localStorage | Redirect to `/profile` immediately |

#### Accessibility
- `autofocus` on username field
- `aria-required="true"` on required inputs
- `aria-live="polite"` on error alert
- `aria-label` on toggle password button
- Form uses `novalidate` (custom validation)

---

### 3.2 Profile Page (`/profile`)

#### Layout
- Full-width with 900px max-width
- Top bar with title + logout button
- Three sections stacked vertically

#### Section 1: User Information
- Grid layout: label (120px) + value
- Fields: Name, Email, Role
- Data source: localStorage `user_info` or JWT decode

#### Section 2: Bridge Token
- Status text (no active token / token active with expiry)
- Row: Expiry days input (number, 1-365) + Generate button
- Token display area (monospace, word-break, auto-hide after 60s)
- Copy button (absolute positioned top-right of token display)

#### Section 3: Server Credentials
- List of server cards (dynamic from API)
- Each card: header (name + status badge) + expandable form

#### Server Card States

| Status | Border Color | Badge |
|--------|-------------|-------|
| `complete` | Green | ✅ Complete |
| `partial` | Orange | ⚠️ Partial |
| `none` | Red | ❌ None |

#### Server Card Interactions

| Action | Trigger | Result |
|--------|---------|--------|
| Expand | Click header | Toggle form visibility, load fields from API |
| Save | Click Save | PUT credentials, show success toast, refresh list |
| Clear | Click Clear All | Confirm dialog → DELETE credentials |
| Collapse | Click header again | Hide form |

#### Accessibility
- Server headers have `role="button"`, `tabindex="0"`, `aria-expanded`
- Keyboard: Enter/Space toggles expansion
- `aria-live="polite"` on alerts and token display
- `aria-label` on copy button

---

### 3.3 Admin Schemas Page (`/admin/schemas`)

#### Layout
- Full-width with 900px max-width
- Top bar with title + back button
- Server selector + schema editor

#### Sections
1. **Server List** — Dropdown or card list of configured upstream servers
2. **Schema Editor** — For selected server, show/edit credential field definitions
3. **Field Editor** — Add/remove/reorder fields with properties

#### Field Properties

| Property | Type | Description |
|----------|------|-------------|
| `field_key` | text | Unique identifier (e.g., `api_key`) |
| `field_label` | text | Display label (e.g., "API Key") |
| `field_type` | select | `text` or `password` |
| `required` | checkbox | Whether field is mandatory |
| `description` | text | Help text for users |

#### Interactions

| Action | Trigger | Result |
|--------|---------|--------|
| Select server | Click server card | Load schema fields |
| Add field | Click "Add Field" | Append empty field row |
| Remove field | Click ✕ on field | Remove field (confirm if has data) |
| Save schema | Click Save | PUT schema, success toast |
| Reorder | Drag handle | Reorder fields |

---

## 4. Error Handling

### 4.1 Error Display Patterns

| Context | Pattern | Duration |
|---------|---------|----------|
| Login failure | Inline alert below header | 5 seconds auto-dismiss |
| API error (profile) | Top alert bar | 5 seconds auto-dismiss |
| Success feedback | Top alert bar (green) | 4 seconds auto-dismiss |
| Network error | Alert with retry suggestion | Manual dismiss |

### 4.2 Error Messages

| Error Code | User-Facing Message |
|------------|-------------------|
| `INVALID_CREDENTIALS` | "Invalid username or password" |
| `ACCOUNT_DISABLED` | "Account is disabled. Contact admin." |
| `TOKEN_EXPIRED` | Auto-redirect to login |
| `FORBIDDEN` | "You don't have permission for this action" |
| `INTERNAL_ERROR` | "Something went wrong. Please try again." |

---

## 5. Responsive Behavior

| Breakpoint | Behavior |
|-----------|----------|
| > 900px | Full layout, max-width constrained |
| 600-900px | Reduce padding, stack grid items |
| < 600px | Single column, full-width cards |

---

## 6. Security Considerations

- Tokens stored in `localStorage` (acceptable for admin portal)
- Token auto-hide after 60 seconds display
- Password field uses `autocomplete="current-password"`
- 401 responses trigger automatic logout + redirect
- Bridge tokens shown once, not retrievable after page refresh
- Confirm dialog before destructive actions (clear credentials)

---

## 7. Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | UI Sitemap | [ui-sitemap.png](diagrams/ui-sitemap.png) | [ui-sitemap.drawio](diagrams/ui-sitemap.drawio) |
| 2 | UI Screenflow | [ui-screenflow.png](diagrams/ui-screenflow.png) | [ui-screenflow.drawio](diagrams/ui-screenflow.drawio) |
