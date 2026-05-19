/**
 * Shared navigation bar for all MCP Orchestrator pages.
 * Auto-injects a top nav with links based on auth state and user role.
 * Include via: <script src="nav-bar.js"></script>
 */
(function() {
    'use strict';

    // Context path for reverse proxy deployment (e.g., nginx location /mcp/)
    // Change this value if deploying under a different sub-path, or set to '' for root.
    const CONTEXT_PATH = '/mcp';

    const basePath = window.__MCP_BASE || CONTEXT_PATH;
    window.__MCP_BASE = basePath;

    const NAV_LINKS = [
        { href: basePath + '/sync/graph-viewer', label: '📊 Graph', auth: true },
        { href: basePath + '/sync/dashboard', label: '🔄 Sync', auth: true },
        { href: basePath + '/profile', label: '👤 Profile', auth: true },
        { href: basePath + '/static/admin-users.html', label: '👥 Users', auth: true, admin: true },
        { href: basePath + '/admin/schemas', label: '⚙️ Admin', auth: true, admin: true }
    ];

    function getAuthState() {
        const token = localStorage.getItem('auth_token');
        if (!token) return { loggedIn: false, isAdmin: false, name: null };
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            const roles = payload.roles || [];
            const isAdmin = roles.some(r =>
                r === 'leader' || r === 'system_owner'
            );
            const name = payload.name || payload.email || 'User';
            return { loggedIn: true, isAdmin, name };
        } catch (e) {
            return { loggedIn: false, isAdmin: false, name: null };
        }
    }

    function getCurrentPath() {
        return window.location.pathname;
    }

    function createNavBar() {
        const state = getAuthState();
        const currentPath = getCurrentPath();

        // Don't show nav on login page
        if (currentPath === basePath + '/login' || currentPath === basePath + '/static/login.html') return;

        // If not logged in, redirect to login
        if (!state.loggedIn) {
            window.location.href = basePath + '/login';
            return;
        }

        const nav = document.createElement('nav');
        nav.id = 'mcp-nav';
        nav.setAttribute('aria-label', 'Main navigation');
        nav.innerHTML = buildNavHtml(state, currentPath);
        document.body.prepend(nav);
        injectStyles();
        bindLogout();
    }

    function buildNavHtml(state, currentPath) {
        const links = NAV_LINKS
            .filter(link => {
                if (link.admin && !state.isAdmin) return false;
                return true;
            })
            .map(link => {
                const active = currentPath.startsWith(link.href) ? ' active' : '';
                return `<a href="${link.href}" class="nav-link${active}">${link.label}</a>`;
            })
            .join('');

        return `
            <div class="nav-left">
                <a href="${basePath}/sync/graph-viewer" class="nav-brand">🔮 MCP Orchestrator</a>
                ${links}
            </div>
            <div class="nav-right">
                <span class="nav-user">${state.name}</span>
                <button class="nav-logout" id="nav-logout-btn" aria-label="Logout">Logout</button>
            </div>`;
    }

    function injectStyles() {
        if (document.getElementById('mcp-nav-styles')) return;
        const style = document.createElement('style');
        style.id = 'mcp-nav-styles';
        style.textContent = `
            #mcp-nav {
                position: fixed;
                top: 0; left: 0; right: 0;
                height: 48px;
                background: #16213e;
                border-bottom: 1px solid #30363d;
                display: flex;
                align-items: center;
                justify-content: space-between;
                padding: 0 20px;
                z-index: 9999;
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            }
            #mcp-nav .nav-left, #mcp-nav .nav-right {
                display: flex;
                align-items: center;
                gap: 16px;
            }
            #mcp-nav .nav-brand {
                font-weight: 700;
                font-size: 0.95rem;
                color: #eaeaea;
                text-decoration: none;
                margin-right: 12px;
            }
            #mcp-nav .nav-link {
                color: #8892b0;
                text-decoration: none;
                font-size: 0.85rem;
                padding: 6px 12px;
                border-radius: 6px;
                transition: background 0.2s, color 0.2s;
            }
            #mcp-nav .nav-link:hover { background: #0f3460; color: #eaeaea; }
            #mcp-nav .nav-link.active { background: #0f3460; color: #e94560; font-weight: 600; }
            #mcp-nav .nav-user {
                color: #8892b0;
                font-size: 0.85rem;
            }
            #mcp-nav .nav-logout {
                background: none;
                border: 1px solid #8892b0;
                color: #8892b0;
                padding: 4px 12px;
                border-radius: 6px;
                font-size: 0.8rem;
                cursor: pointer;
                transition: border-color 0.2s, color 0.2s;
            }
            #mcp-nav .nav-logout:hover { border-color: #e94560; color: #e94560; }
            body { padding-top: 48px !important; }
        `;
        document.head.appendChild(style);
    }

    function bindLogout() {
        const btn = document.getElementById('nav-logout-btn');
        if (btn) {
            btn.addEventListener('click', function() {
                localStorage.removeItem('auth_token');
                localStorage.removeItem('token_expires');
                localStorage.removeItem('user_info');
                window.location.href = basePath + '/login';
            });
        }
    }

    // Run after DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', createNavBar);
    } else {
        createNavBar();
    }
})();
