package org.jetbrains.demo.ui

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig

object Logger {
    private val baseLogger: Logger = Logger(config = StaticConfig(minSeverity = Severity.Debug))
    val app = baseLogger.withTag("App")
}