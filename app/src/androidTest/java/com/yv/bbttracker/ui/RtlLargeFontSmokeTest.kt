package com.yv.bbttracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yv.bbttracker.ui.components.InfoCard
import com.yv.bbttracker.ui.components.LabelValueRow
import com.yv.bbttracker.ui.theme.BbtTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RtlLargeFontSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun commonComponents_renderHebrewRightToLeftAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density = deviceDensity, fontScale = 2f),
            ) {
                BbtTheme(darkTheme = false) {
                    Surface {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .testTag(ROOT_TAG),
                        ) {
                            InfoCard(
                                title = "מעקב חום השחר",
                                body = "הנתונים נשמרים במכשיר בלבד",
                                supporting = "אין כאן קביעה רפואית",
                                modifier = Modifier.testTag(CARD_TAG),
                            )
                            LabelValueRow(
                                label = "טמפרטורה",
                                value = "36.65 °C",
                                modifier = Modifier.testTag(ROW_TAG),
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag(ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(CARD_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ROW_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("מעקב חום השחר").assertIsDisplayed()
        composeRule.onNodeWithText("הנתונים נשמרים במכשיר בלבד").assertIsDisplayed()
        composeRule.onNodeWithText("אין כאן קביעה רפואית").assertIsDisplayed()
        composeRule.onNodeWithText("טמפרטורה").assertIsDisplayed()
        composeRule.onNodeWithText("36.65 °C").assertIsDisplayed()

        composeRule.waitForIdle()
        val labelBounds = composeRule.onNodeWithText("טמפרטורה").fetchSemanticsNode().boundsInRoot
        val valueBounds = composeRule.onNodeWithText("36.65 °C").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The first row item should be laid out on the right in RTL",
            labelBounds.left > valueBounds.left,
        )
    }

    private companion object {
        const val ROOT_TAG = "rtl-large-font-root"
        const val CARD_TAG = "rtl-large-font-card"
        const val ROW_TAG = "rtl-large-font-row"
    }
}
