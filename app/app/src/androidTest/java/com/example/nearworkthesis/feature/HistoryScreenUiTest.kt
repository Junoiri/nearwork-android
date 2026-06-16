package com.example.nearworkthesis.feature

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nearworkthesis.app.LocalActiveProfileStore
import com.example.nearworkthesis.app.LocalProfileRepository
import com.example.nearworkthesis.core.ui.UiTestTags
import com.example.nearworkthesis.core.ui.theme.NearworkTheme
import com.example.nearworkthesis.domain.model.MonthDaySummary
import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import com.example.nearworkthesis.testutil.FakeActiveProfileStore
import com.example.nearworkthesis.testutil.FakeMeasurementRepository
import com.example.nearworkthesis.testutil.FakeProfileRepository
import org.junit.Rule
import org.junit.Test

class HistoryScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun listMode_showsHistoryDay_andSelectsIt() {
        var selectedDay: String? = null

        setHistoryContent(
            onSelectDay = { selectedDay = it },
            onInspectDay = {}
        )

        composeRule.onNodeWithTag(UiTestTags.HistoryScreen).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.HistoryListModeButton).performClick()
        composeRule.onNodeWithText("120 samples").assertIsDisplayed().performClick()

        check(selectedDay == "2026-06-08")
    }

    @Test
    fun calendarInspectAction_usesSelectedDay() {
        var inspectedDay: String? = null

        setHistoryContent(
            onSelectDay = {},
            onInspectDay = { inspectedDay = it }
        )

        composeRule.onNodeWithTag(UiTestTags.HistoryScreen).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Inspect raw vs processed")[1].performClick()

        check(inspectedDay == "2026-06-08")
    }

    private fun setHistoryContent(
        onSelectDay: (String) -> Unit,
        onInspectDay: (String) -> Unit
    ) {
        composeRule.setContent {
            NearworkTheme {
                CompositionLocalProvider(
                    LocalProfileRepository provides FakeProfileRepository(),
                    LocalActiveProfileStore provides FakeActiveProfileStore(),
                ) {
                    HistoryScreen(
                        measurementRepository = fakeHistoryRepository(),
                        onSelectDay = onSelectDay,
                        onInspectDay = onInspectDay,
                        onGoToImport = {}
                    )
                }
            }
        }
    }

    private fun fakeHistoryRepository(): FakeMeasurementRepository {
        return FakeMeasurementRepository(
            availableDays = listOf("2026-06-08", "2026-06-07"),
            weeklySummaries = listOf(
                WeeklyDaySummary(
                    day = "2026-06-08",
                    sampleCount = 120,
                    avgDistanceCm = 62.5,
                    avgLux = 320.0,
                    diopterHoursTotal = 4.8,
                    nrs = 21.4,
                    lowLightMinutes = 18,
                    firstTimestampIso = "2026-06-08T08:00:00",
                    lastTimestampIso = "2026-06-08T11:15:00"
                ),
                WeeklyDaySummary(
                    day = "2026-06-07",
                    sampleCount = 85,
                    avgDistanceCm = 70.0,
                    avgLux = 540.0,
                    diopterHoursTotal = 3.1,
                    nrs = 12.0,
                    lowLightMinutes = 0,
                    firstTimestampIso = "2026-06-07T09:05:00",
                    lastTimestampIso = "2026-06-07T10:40:00"
                )
            ),
            monthSummaries = listOf(
                MonthDaySummary(
                    day = "2026-06-08",
                    sampleCount = 120,
                    diopterHoursTotal = 4.8,
                    nrs = 21.4,
                    lowLightMinutes = 18
                ),
                MonthDaySummary(
                    day = "2026-06-07",
                    sampleCount = 85,
                    diopterHoursTotal = 3.1,
                    nrs = 12.0,
                    lowLightMinutes = 0
                )
            )
        )
    }
}
