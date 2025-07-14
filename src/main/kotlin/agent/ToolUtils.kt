package com.example.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.reflect.ToolFromCallable
import ai.koog.agents.mcp.McpTool
import kotlinx.serialization.json.JsonObject

fun <A> Tool.Args.fromCallable(name: String, transform: (Any?) -> A): A? =
    (this as? ToolFromCallable.VarArgs)?.args
        ?.firstNotNullOf { if (it.key.name == name) transform(it.value) else null }

fun Tool.Args.mcpArguments(): JsonObject? =
    (this as? McpTool.Args)?.arguments