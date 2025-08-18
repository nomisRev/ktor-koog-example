package org.jetbrains.demo.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.TransportType
import org.jetbrains.demo.Traveler

class JourneyPlannerViewModel(
    base: Logger
) : ViewModel() {
    private val logger = base.withTag("JourneyPlannerViewModel")

    private val _state = MutableStateFlow(EmptyJourneyForm())
    val state: StateFlow<JourneyForm> = _state.asStateFlow()

    fun updateFromCity(value: String) = _state.update { it.copy(fromCity = value) }
    fun updateToCity(value: String) = _state.update { it.copy(toCity = value) }
    fun updateTransport(value: TransportType) = _state.update { it.copy(transport = value) }
    fun updateDetails(value: String?) = _state.update { it.copy(details = value) }

    fun setStartDate(date: LocalDate) = _state.update { form ->
        val t = form.startDate.time
        form.copy(startDate = LocalDateTime(date, t))
    }

    fun setEndDate(date: LocalDate) = _state.update { form ->
        val t = form.endDate.time
        form.copy(endDate = LocalDateTime(date, t))
    }

    fun addTraveler() = _state.update { form ->
        val newTraveler = Traveler(id = randomId(), name = "", about = "")
        form.copy(travelers = (form.travelers + newTraveler).toImmutableList())
    }

    fun removeTraveler(id: String) = _state.update { form ->
        form.copy(travelers = form.travelers.filterNot { it.id == id }.toImmutableList())
    }

    fun updateTravelerName(id: String, name: String) = _state.update { form ->
        val updated = form.travelers.map { t ->
            if (t.id == id) t.copy(name = name) else t
        }.toImmutableList()
        form.copy(travelers = updated)
    }

    fun submit(onResult: (JourneyForm) -> Unit) {
        viewModelScope.launch {
            val form = _state.value
            logger.d("Submitting journey: $form")
            onResult(form)
        }
    }
}

private fun randomId(): String = List(8) { (('a'..'z') + ('0'..'9')).random() }.joinToString("")
