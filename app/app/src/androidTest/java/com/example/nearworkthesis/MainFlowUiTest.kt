package com.example.nearworkthesis

import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.nearworkthesis.app.MainActivity
import com.example.nearworkthesis.core.ui.UiTestTags
import com.example.nearworkthesis.testutil.mainActivityUiTestIntent
import com.example.nearworkthesis.testutil.waitForTag
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainFlowUiTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun launchActivity() {
        scenario = ActivityScenario.launch(mainActivityUiTestIntent())
    }

    @After
    fun closeActivity() {
        scenario?.close()
    }

    @Test
    fun launch_withUiTestIntent_showsHome() {
        composeRule.waitForTag(UiTestTags.HomeScreen)
        composeRule.onNodeWithTag(UiTestTags.HomeScreen).assertIsDisplayed()
    }

    @Test
    fun homeImportAction_opensImportScreen() {
        composeRule.waitForTag(UiTestTags.HomeScreen)

        composeRule.onNodeWithTag(UiTestTags.HomeOpenImportButton).performClick()

        composeRule.waitForTag(UiTestTags.ImportScreen)

        composeRule.onNodeWithTag(UiTestTags.ImportScreen).assertIsDisplayed()
    }

    @Test
    fun bottomNavigation_switchesBetweenHomeWeeklyAndHistory() {
        composeRule.waitForTag(UiTestTags.HomeScreen)

        composeRule.onNodeWithTag(UiTestTags.MainNavWeekly).performClick()
        composeRule.waitForTag(UiTestTags.WeeklyScreen)
        composeRule.onNodeWithTag(UiTestTags.WeeklyScreen).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.MainNavHistory).performClick()
        composeRule.waitForTag(UiTestTags.HistoryScreen)
        composeRule.onNodeWithTag(UiTestTags.HistoryScreen).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.MainNavHome).performClick()
        composeRule.waitForTag(UiTestTags.HomeScreen)
        composeRule.onNodeWithTag(UiTestTags.HomeScreen).assertIsDisplayed()
    }

    @Test
    fun importBackNavigation_returnsHome() {
        composeRule.waitForTag(UiTestTags.HomeScreen)
        composeRule.onNodeWithTag(UiTestTags.HomeOpenImportButton).performClick()
        composeRule.waitForTag(UiTestTags.ImportScreen)

        scenario?.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.waitForTag(UiTestTags.HomeScreen)
        composeRule.onNodeWithTag(UiTestTags.HomeScreen).assertIsDisplayed()
    }
}
