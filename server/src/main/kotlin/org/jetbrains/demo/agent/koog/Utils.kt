package org.jetbrains.demo.agent.koog

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry

fun ToolRegistry.descriptors(): List<ToolDescriptor> =
    tools.map { it.descriptor }