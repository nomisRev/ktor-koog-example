package org.jetbrains.demo.agent.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlin.math.ceil

@LLMDescription("Math tools: use when you need to perform calculations")
object MathTools : ToolSet {

    @Tool
    @LLMDescription(description = "add two numbers")
    fun add(a: Double, b: Double) = a + b

    @Tool
    @LLMDescription(description = "subtract the second number from the first")
    fun subtract(a: Double, b: Double) = a - b

    @Tool
    @LLMDescription(description = "multiply two numbers")
    fun multiply(a: Double, b: Double) = a * b

    @Tool
    @LLMDescription(description = "divide the first number by the second")
    fun divide(a: Double, b: Double): String =
        if (b == 0.0) "Cannot divide by zero" else ("" + a / b)

    @Tool
    @LLMDescription(description = "find the mean of this list of numbers")
    fun mean(numbers: List<Double>): Double =
        if (numbers.isEmpty()) 0.0 else numbers.sum() / numbers.size

    @Tool
    @LLMDescription(description = "find the minimum value in a list of numbers")
    fun min(numbers: List<Double>): Double =
        numbers.minOrNull() ?: Double.NaN

    @Tool
    @LLMDescription(description = "find the maximum value in a list of numbers")
    fun max(numbers: List<Double>): Double =
        numbers.maxOrNull() ?: Double.NaN

    @Tool
    @LLMDescription(description = "round down to the nearest integer")
    fun floor(number: Double): Double = kotlin.math.floor(number)

    @Tool
    @LLMDescription(description = "round up to the nearest integer")
    fun ceiling(number: Double): Double = ceil(number)

    @Tool
    @LLMDescription(description = "round to the nearest integer")
    fun round(number: Double): Double = kotlin.math.round(number)
}
