package com.jmprieur.drawinggrid

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class DrawingGridScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun initialStateOffersPhotoPicker() {
        composeRule.setContent {
            DrawingGridTheme {
                DrawingGridScreen(null, GridSettings(), {}, {})
            }
        }

        composeRule.onNodeWithTag("choose_photo").assertIsDisplayed()
    }
}
