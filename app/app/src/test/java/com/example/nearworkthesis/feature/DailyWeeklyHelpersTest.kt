package com.example.nearworkthesis.feature

import java.time.LocalDate
import com.example.nearworkthesis.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyWeeklyHelpersTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun dailyHelpers_parseSelectedDate_andCalculateLowerBound() {
        assertEquals(LocalDate.parse("2026-06-10"), invokeDailyParse("2026-06-10"))
        assertNull(invokeDailyParse("bad-date"))
        assertNull(invokeDailyParse(null))

        val expectedRecent = listOf(
            LocalDate.now().minusDays(3).toString(),
            LocalDate.now().minusDays(1).toString()
        ).first()
        assertEquals(
            LocalDate.parse(expectedRecent),
            invokeDailyLowerBound(
                listOf(
                    "bad-date",
                    LocalDate.now().minusDays(20).toString(),
                    LocalDate.now().plusDays(1).toString(),
                    expectedRecent,
                    LocalDate.now().minusDays(1).toString()
                )
            )
        )
        assertEquals(LocalDate.now().minusDays(7), invokeDailyLowerBound(emptyList()))
    }

    @Test
    fun weeklyHelpers_parseDays_andBuildRanges() = runTest {
        val viewModel = WeeklyViewModel(
            measurementRepository = FakeImportMeasurementRepository(),
            activeProfileStore = FakeActiveProfileStoreForImport(1L),
            nearworkRiskScoreCalculator = com.example.nearworkthesis.domain.analysis.NearworkRiskScoreCalculator()
        )

        assertEquals(LocalDate.parse("2026-06-12"), invokeWeeklyParse(viewModel, "2026-06-12"))
        assertNull(invokeWeeklyParse(viewModel, "bad"))

        val days = listOf(
            LocalDate.parse("2026-06-01"),
            LocalDate.parse("2026-06-03"),
            LocalDate.parse("2026-06-08"),
            LocalDate.parse("2026-06-14")
        )
        val ranges = invokeWeeklyRanges(viewModel, days, LocalDate.parse("2026-06-14"), maxWeeks = 4)

        assertEquals(
            listOf(
                WeekRange(LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-14")),
                WeekRange(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-07"))
            ),
            ranges
        )
        assertEquals(emptyList<WeekRange>(), invokeWeeklyRanges(viewModel, emptyList(), LocalDate.parse("2026-06-14"), maxWeeks = 4))
        assertEquals(emptyList<WeekRange>(), invokeWeeklyRanges(viewModel, days, LocalDate.parse("2026-06-14"), maxWeeks = 0))
        assertEquals(
            emptyList<WeekRange>(),
            invokeWeeklyRanges(
                viewModel,
                listOf(LocalDate.parse("2026-05-01")),
                LocalDate.parse("2026-04-20"),
                maxWeeks = 4
            )
        )
    }

    private fun invokeDailyParse(value: String?): LocalDate? {
        val method = Class.forName("com.example.nearworkthesis.feature.DailyViewModelKt")
            .getDeclaredMethod("parseSelectedDate", String::class.java)
        method.isAccessible = true
        return method.invoke(null, value) as LocalDate?
    }

    private fun invokeDailyLowerBound(availableDays: List<String>): LocalDate {
        val method = Class.forName("com.example.nearworkthesis.feature.DailyViewModelKt")
            .getDeclaredMethod("calculateLowerBound", List::class.java)
        method.isAccessible = true
        return method.invoke(null, availableDays) as LocalDate
    }

    private fun invokeWeeklyParse(viewModel: WeeklyViewModel, day: String): LocalDate? {
        val method = WeeklyViewModel::class.java.getDeclaredMethod("parseDayOrNull", String::class.java)
        method.isAccessible = true
        return method.invoke(viewModel, day) as LocalDate?
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeWeeklyRanges(
        viewModel: WeeklyViewModel,
        availableDays: List<LocalDate>,
        latestDay: LocalDate,
        maxWeeks: Int
    ): List<WeekRange> {
        val method = WeeklyViewModel::class.java.getDeclaredMethod(
            "buildWeekRanges",
            List::class.java,
            LocalDate::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(viewModel, availableDays, latestDay, maxWeeks) as List<WeekRange>
    }
}
