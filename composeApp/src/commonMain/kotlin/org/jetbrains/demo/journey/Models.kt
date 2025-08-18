package org.jetbrains.demo.journey

import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.TransportType
import org.jetbrains.demo.Traveler
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun EmptyJourneyForm(): JourneyForm {
    val now = Clock.System.now()
    return JourneyForm(
        fromCity = "Antwerp",
        toCity = "Brussel",
        transport = TransportType.Train,
        startDate = (now + 2.days).toLocalDateTime(TimeZone.currentSystemDefault()),
        endDate = (now + 7.days).toLocalDateTime(TimeZone.currentSystemDefault()),
        travelers = persistentListOf(Traveler(id = "initial", name = "Simon", about = "")),
        details = null,
    )
}
