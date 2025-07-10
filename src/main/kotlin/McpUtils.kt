package com.example

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.McpToolRegistryProvider.fromTransport
import org.testcontainers.containers.DockerMcpGatewayContainer

suspend fun DockerMcpGatewayContainer.toToolRegistry(): ToolRegistry =
    McpToolRegistryProvider.fromSseTransport(endpoint, "mcp-gateway", "1.0.0")

suspend fun McpToolRegistryProvider.fromSseTransport(url: String, name: String, version: String) =
    fromTransport(
        transport = McpToolRegistryProvider.defaultSseTransport(url),
        name = name,
        version = version
    )
