package com.orchestrator.mcp.protocol.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * JSON-RPC 2.0 request message.
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonObject? = null
)

/**
 * JSON-RPC 2.0 response message.
 * Per JSON-RPC spec: include "result" on success,
 * "error" on failure — never both.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val result: JsonElement? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val error: JsonRpcError? = null
)

/**
 * JSON-RPC 2.0 error object.
 */
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val data: JsonElement? = null
)
