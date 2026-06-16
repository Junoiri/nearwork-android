package com.example.nearworkthesis.feature

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.nearworkthesis.app.LocalActiveProfileStore
import com.example.nearworkthesis.app.LocalDemoRepository
import com.example.nearworkthesis.app.LocalNotificationScheduler
import com.example.nearworkthesis.app.LocalProfileRepository
import com.example.nearworkthesis.app.LocalSettingsStore
import com.example.nearworkthesis.core.ui.UiTestTags
import com.example.nearworkthesis.core.ui.theme.NearworkTheme
import com.example.nearworkthesis.testutil.FakeActiveProfileStore
import com.example.nearworkthesis.testutil.FakeDemoRepository
import com.example.nearworkthesis.testutil.FakeNotificationScheduler
import com.example.nearworkthesis.testutil.FakeProfileRepository
import com.example.nearworkthesis.testutil.FakeSettingsStore
import org.junit.Rule
import org.junit.Test

class SettingsScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun postImportToggle_marksSettingsDirty_andEnablesApply() {
        val settingsStore = FakeSettingsStore()

        setSettingsContent(settingsStore = settingsStore)

        composeRule.onNodeWithTag(UiTestTags.SettingsScreen).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.SettingsGeneralCategory).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.SettingsPostImportNotificationSwitch).assertIsOff()

        composeRule.onNodeWithTag(UiTestTags.SettingsPostImportNotificationSwitch).performClick()

        composeRule.onNodeWithTag(UiTestTags.SettingsPostImportNotificationSwitch).assertIsOn()
        composeRule.onNodeWithTag(UiTestTags.SettingsApplyButton).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun invalidThresholdOrdering_disablesApply_andShowsValidationMessage() {
        setSettingsContent(settingsStore = FakeSettingsStore())

        composeRule.onNodeWithTag(UiTestTags.SettingsCloseDistanceField)
            .performTextReplacement("20")
        composeRule.onNodeWithTag(UiTestTags.SettingsExtremeCloseField)
            .performTextReplacement("20")

        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(UiTestTags.SettingsThresholdOrderingError)
            .assertCountEquals(1)
    }

    @Test
    fun applyButton_persistsDraft_andClearsDirtyState() {
        val settingsStore = FakeSettingsStore()

        setSettingsContent(settingsStore = settingsStore)

        composeRule.onNodeWithText("Replace with new").performClick()
        composeRule.onNodeWithTag(UiTestTags.SettingsApplyButton).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            settingsStore.duplicateResolutionPolicyValue == com.example.nearworkthesis.domain.DuplicateResolutionPolicy.REPLACE_WITH_NEW
        }

        check(settingsStore.duplicateResolutionPolicyValue == com.example.nearworkthesis.domain.DuplicateResolutionPolicy.REPLACE_WITH_NEW)
        composeRule.onAllNodesWithTag(UiTestTags.SettingsApplyButton).assertCountEquals(0)
        composeRule.onNodeWithText("Replace with new").assertIsDisplayed()
    }

    private fun setSettingsContent(settingsStore: FakeSettingsStore) {
        composeRule.setContent {
            NearworkTheme {
                CompositionLocalProvider(
                    LocalSettingsStore provides settingsStore,
                    LocalNotificationScheduler provides FakeNotificationScheduler(),
                    LocalDemoRepository provides FakeDemoRepository(),
                    LocalProfileRepository provides FakeProfileRepository(),
                    LocalActiveProfileStore provides FakeActiveProfileStore()
                ) {
                    SettingsScreen(openNotificationsSection = true)
                }
            }
        }
    }
}
