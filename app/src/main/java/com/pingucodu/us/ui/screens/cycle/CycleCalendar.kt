package com.pingucodu.us.ui.screens.cycle

import com.pingucodu.us.data.network.CycleLogDto
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private const val PERIOD_LEN = 5L

/** One cell in the month grid. Null slots (returned as leading padding) render as blank space. */
data class CycleDay(
    val date: LocalDate,
    val hasLog: Boolean,
    val isFertile: Boolean,
    val isPredictedPeriod: Boolean,
    val isToday: Boolean,
)

data class CycleBar(val lengthDays: Int, val monthLabel: String)

data class CycleStats(
    val avgCycleLength: Int?,
    val avgPeriodLength: Int?,
    val recentCycles: List<CycleBar>,
)

private fun loggedDatesOf(logs: List<CycleLogDto>): List<LocalDate> =
    logs.mapNotNull { runCatching { LocalDate.parse(it.logDate, ISO) }.getOrNull() }.distinct().sorted()

/** A "cycle start" is the first logged day after a gap of more than one day from the previous log. */
fun cycleStartsFrom(logs: List<CycleLogDto>): List<LocalDate> {
    val dates = loggedDatesOf(logs)
    if (dates.isEmpty()) return emptyList()
    val starts = mutableListOf(dates.first())
    for (i in 1 until dates.size) {
        if (ChronoUnit.DAYS.between(dates[i - 1], dates[i]) > 1) starts.add(dates[i])
    }
    return starts
}

fun computeCycleStats(logs: List<CycleLogDto>): CycleStats {
    val loggedDates = loggedDatesOf(logs)
    val starts = cycleStartsFrom(logs)

    val avgCycle = if (starts.size >= 2) {
        starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }.average().roundToInt()
    } else null

    val avgPeriod = if (starts.isNotEmpty()) {
        starts.map { start ->
            var len = 1
            var d = start
            while (loggedDates.contains(d.plusDays(1))) {
                d = d.plusDays(1)
                len++
            }
            len
        }.average().roundToInt()
    } else null

    val bars = if (starts.size >= 2) {
        starts.zipWithNext { a, b ->
            CycleBar(
                lengthDays = ChronoUnit.DAYS.between(a, b).toInt(),
                monthLabel = a.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).lowercase(),
            )
        }.takeLast(6)
    } else emptyList()

    return CycleStats(avgCycle, avgPeriod, bars)
}

/**
 * Leading `null`s pad the first week to start on Sunday; the caller renders those as blank cells.
 * Fertile/predicted-period windows are estimated from [predictedNextDate] (ovulation ~12-17 days
 * before the next period), since the API only hands back a single predicted date, not a full model.
 */
fun buildCalendarDays(
    month: YearMonth,
    logs: List<CycleLogDto>,
    predictedNextDate: String?,
    today: LocalDate,
): List<CycleDay?> {
    val loggedDates = loggedDatesOf(logs).toSet()
    val predictedStart = predictedNextDate?.let { runCatching { LocalDate.parse(it, ISO) }.getOrNull() }
    val fertileRange = predictedStart?.let { it.minusDays(17)..it.minusDays(12) }
    val periodRange = predictedStart?.let { it..it.plusDays(PERIOD_LEN - 1) }

    val leading = month.atDay(1).dayOfWeek.value % 7
    val days = (1..month.lengthOfMonth()).map { dayOfMonth ->
        val date = month.atDay(dayOfMonth)
        CycleDay(
            date = date,
            hasLog = loggedDates.contains(date),
            isFertile = fertileRange?.contains(date) == true,
            isPredictedPeriod = periodRange?.contains(date) == true && date.isAfter(today),
            isToday = date == today,
        )
    }
    return List<CycleDay?>(leading) { null } + days
}
