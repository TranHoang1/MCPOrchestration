package com.orchestrator.mcp

/**
 * Wrapper entry point for MCP stdio transport.
 *
 * kotlin-logging (oshai) prints "initializing... active logger factory: ..."
 * to stdout via println() during its first class load. The MCP SDK triggers
 * this load before our Application class initializes. Since MCP stdio transport
 * uses stdout exclusively for JSON-RPC messages, this stray output corrupts
 * the protocol handshake.
 *
 * This wrapper redirects System.out to System.err before loading ANY application
 * classes, forces kotlin-logging initialization, then restores stdout and
 * delegates to the real main via reflection (to avoid triggering class loading
 * of Application at compile time).
 */
object StdioMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val realStdout = System.out
        System.setOut(System.err)

        // Force kotlin-logging (oshai) initialization while stdout is redirected.
        // KotlinLogging prints its init message via println() to whatever System.out
        // points to at class-load time. We must trigger this BEFORE restoring stdout.
        Class.forName("io.github.oshai.kotlinlogging.KotlinLogging")

        // Restore stdout for MCP JSON-RPC transport
        System.setOut(realStdout)

        // Delegate to realMain via reflection to avoid compile-time class loading
        // of ApplicationKt (which has a top-level LoggerFactory.getLogger call).
        val appClass = Class.forName("com.orchestrator.mcp.ApplicationKt")
        val realMain = appClass.getMethod("realMain", Array<String>::class.java)
        realMain.invoke(null, args)
    }
}
