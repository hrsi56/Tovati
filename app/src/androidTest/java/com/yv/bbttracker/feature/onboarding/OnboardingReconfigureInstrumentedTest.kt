package com.yv.bbttracker.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yv.bbttracker.R
import com.yv.bbttracker.domain.model.AppSettings
import com.yv.bbttracker.domain.repository.SettingsRepository
import com.yv.bbttracker.notification.ReminderScheduler
import com.yv.bbttracker.ui.theme.BbtTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingReconfigureInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reconfigureStartsAtBeginningAndShowsPreviouslySuppliedLengths() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = AppSettings(
            onboardingCompleted = true,
            acceptedDisclaimerVersion = 1,
            typicalCycleLengthDays = 31,
            typicalMenstruationLengthDays = 6,
        )
        val viewModel = OnboardingViewModel(
            settingsRepository = FakeSettingsRepository(settings),
            reminderScheduler = ReminderScheduler(context),
            initialSettings = settings,
            reconfigure = true,
        )
        val continueLabel = context.getString(R.string.continue_label)

        composeRule.setContent {
            BbtTheme(darkTheme = false) {
                OnboardingScreen(
                    viewModel = viewModel,
                    onDismiss = {},
                )
            }
        }

        assertEquals(0, viewModel.state.value.page)
        repeat(4) {
            composeRule.onNodeWithText(continueLabel).performClick()
        }
        assertEquals(4, viewModel.state.value.page)
        composeRule.onNodeWithText("31").assertIsDisplayed()

        composeRule.onNodeWithText(continueLabel).performClick()
        assertEquals(5, viewModel.state.value.page)
        composeRule.onNodeWithText("6").assertIsDisplayed()
    }

    private class FakeSettingsRepository(initial: AppSettings) : SettingsRepository {
        private val values = MutableStateFlow(initial)

        override val settings: Flow<AppSettings> = values

        override suspend fun getSettings(): AppSettings = values.value

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            values.value = transform(values.value)
        }
    }
}
