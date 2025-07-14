package com.example

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import org.testcontainers.containers.DockerMcpGatewayContainer

suspend fun DockerMcpGatewayContainer.toToolRegistry(): ToolRegistry =
    McpToolRegistryProvider.fromSseTransport(endpoint)

suspend fun McpToolRegistryProvider.fromSseTransport(url: String) =
    fromTransport(McpToolRegistryProvider.defaultSseTransport(url))
