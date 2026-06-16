package com.example.nearworkthesis.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nearworkthesis.core.ui.UiTestTags
import com.example.nearworkthesis.core.ui.theme.NearworkTheme
import org.junit.Rule
import org.junit.Test

class ImportStatusCardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyHowfarState_rendersPrimaryAction_andInvokesCallback() {
        var lastAction: ImportHowfarPrimaryAction? = null

        composeRule.setContent {
            NearworkTheme {
                HowfarStatusCard(
                    howfar = ImportHowfarUiModel(
                        statusTitle = "HowFar: Ready",
                        statusSubtitle = "Storage: HOWFAR",
                        primaryAction = ImportHowfarPrimaryAction.ImportFromDevice,
                        primaryActionEnabled = true
                    ),
                    isImporting = false,
                    onRefresh = {},
                    onPrimaryAction = { lastAction = it }
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.ImportHowfarStatusCard).assertIsDisplayed()
        composeRule.onNodeWithText("Ready").assertIsDisplayed()
        composeRule.onNodeWithText("Import from device").performClick()

        check(lastAction == ImportHowfarPrimaryAction.ImportFromDevice)
    }

    @Test
    fun importingStatus_showsBusyState_andDisablesPrimaryAction() {
        composeRule.setContent {
            NearworkTheme {
                HowfarStatusCard(
                    howfar = ImportHowfarUiModel(
                        statusTitle = "HowFar: Ready",
                        statusSubtitle = "Storage: HOWFAR",
                        primaryAction = ImportHowfarPrimaryAction.ImportFromDevice,
                        primaryActionEnabled = true
                    ),
                    isImporting = true,
                    onRefresh = {},
                    onPrimaryAction = {}
                )
            }
        }

        composeRule.onNodeWithText("Busy").assertIsDisplayed()
        composeRule.onNodeWithText("Import from device").assertIsNotEnabled()
    }

    @Test
    fun statusRow_withProgress_rendersImportMessage() {
        composeRule.setContent {
            NearworkTheme {
                StatusRow(
                    text = "Importing HowFar device...",
                    showProgress = true
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.ImportImportingStatus).assertIsDisplayed()
        composeRule.onNodeWithText("Importing HowFar device...").assertIsDisplayed()
    }
}
