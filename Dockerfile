# ============================================================
# Dockerfile for MCP Orchestration Server
# Expects pre-built JAR from Maven local build:
#   mvn package -pl orchestrator-server -am -Dmaven.test.skip=true
# Includes Node.js (npx) and Python/uv (uvx) for MCP servers
# ============================================================

FROM eclipse-temurin:21-jre

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    gnupg \
    && rm -rf /var/lib/apt/lists/*

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
