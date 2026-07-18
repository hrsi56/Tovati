package com.yv.bbttracker.ui

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yv.bbttracker.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CopyClarityInstrumentedTest {
    @Test
    fun hebrewLabels_distinguishActionsMissingDataAndRecordedZero() {
        val baseContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val hebrewContext = baseContext.createConfigurationContext(
            Configuration(baseContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag("he-IL"))
            },
        )

        assertEquals("רישום", hebrewContext.getString(R.string.pain_relief_none))
        assertFalse(hebrewContext.getString(R.string.pain_relief_none).contains("לא לקחתי"))
        assertTrue(hebrewContext.getString(R.string.pain_relief_supporting).contains("0 פירושו שלא לקחת"))
        assertEquals(
            "טרם נרשמה מדידת טמפרטורה",
            hebrewContext.getString(R.string.measurement_not_recorded),
        )
        assertEquals("מרקם הנוזל", hebrewContext.getString(R.string.mucus_appearance))
        assertEquals(
            "יום הביוץ שהוערך בדיעבד",
            hebrewContext.getString(R.string.diary_retrospective_ovulation_day),
        )
    }
}
