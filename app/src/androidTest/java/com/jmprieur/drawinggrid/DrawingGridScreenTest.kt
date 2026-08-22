package com.jmprieur.drawinggrid

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class DrawingGridScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun initialStateOffersPhotoPicker() {
        composeRule.setContent {
            DrawingGridTheme {
                DrawingGridScreen(null, GridSettings(), {}, {}, {})
            }
        }

        composeRule.onNodeWithTag("choose_photo").assertIsDisplayed()
    }

    @Test
    fun photoStateOffersGridExport() {
        var saveClicked = false
        composeRule.setContent {
            DrawingGridTheme {
                DrawingGridScreen("content://photo", GridSettings(), {}, { saveClicked = true }, {})
            }
        }

        composeRule.onNodeWithTag("save_grid").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assert(saveClicked) }
    }
}
