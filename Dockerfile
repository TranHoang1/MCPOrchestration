# ============================================================
# Dockerfile for MCP Orchestration Server
# Expects pre-built JAR from Maven local build:
#   mvn package -pl orchestrator-server -am -Dmaven.test.skip=true
# Includes Node.js (npx) and Python/uv (uvx) for MCP servers
# ============================================================

FROM eclipse-temurin:21-jre

WORKDIR /app

# Install system dependencies + draw.io runtime deps (Electron/headless)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    gnupg \
    xvfb \
    libgbm1 libnss3 libatk1.0-0 libatk-bridge2.0-0 libcups2 \
    libdrm2 libxcomposite1 libxdamage1 libxrandr2 libpango-1.0-0 \
    libcairo2 libasound2t64 libxshmfence1 libgtk-3-0 libdbus-glib-1-2 \
    libxss1 libxtst6 xdg-utils \
    && rm -rf /var/lib/apt/lists/*

# Install draw.io Desktop (headless export via xvfb-run)
ARG DRAWIO_VERSION=26.0.16
RUN ARCH=$(dpkg --print-architecture) \
    && curl -fsSL "https://github.com/jgraph/drawio-desktop/releases/download/v${DRAWIO_VERSION}/drawio-${ARCH}-${DRAWIO_VERSION}.deb" \
    -o /tmp/drawio.deb \
    && dpkg -i /tmp/drawio.deb || apt-get update && apt-get install -f -y --no-install-recommends \
    && rm -f /tmp/drawio.deb \
    && rm -rf /var/lib/apt/lists/* \
    && which drawio

# Install Node.js 22 LTS (for npx)
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/* \
    && node --version && npm --version && npx --version

# Install Python 3 and uv (for uvx)
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/* \
    && curl -LsSf https://astral.sh/uv/install.sh | sh \
    && ln -s /root/.local/bin/uv /usr/local/bin/uv \
    && ln -s /root/.local/bin/uvx /usr/local/bin/uvx \
    && uv --version && uvx --version

# Copy the pre-built fat JAR from Maven target
COPY orchestrator-server/target/mcp-orchestrator-all.jar app.jar

# Create directories
RUN mkdir -p /app/tmp/mcp-file-proxy /app/config

# Expose the server port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD bash -c 'echo > /dev/tcp/localhost/8080' || exit 1

# Run the application
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]
CMD ["--config", "/app/config/mcp.json"]
