package com.yv.bbttracker.ui

import android.view.KeyEvent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yv.bbttracker.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityResultLauncherInstrumentedTest {
    @Test
    fun createDocumentLauncherWorksFromFragmentActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var launcher: ActivityResultLauncher<String>? = null

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val registeredLauncher = activity.activityResultRegistry.register(
                    "qa-create-document",
                    ActivityResultContracts.CreateDocument("text/plain"),
                ) {}
                launcher = registeredLauncher
                registeredLauncher.launch("qa-export.txt")
            }
            instrumentation.waitForIdleSync()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            launcher?.unregister()
        }
    }
}
