package com.yv.bbttracker

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yv.bbttracker.app.AppGraph
import com.yv.bbttracker.ui.theme.BbtTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val container get() = (application as BbtTrackerApplication).container
    private var isLocked by mutableStateOf(true)
    private var securityReady by mutableStateOf(false)
    private var backgroundedAtElapsed: Long? = null
    private var unlockedOnce = false
    private var promptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BbtTheme {
                AppGraph(
                    container = container,
                    locked = isLocked,
                    securityReady = securityReady,
                    onUnlock = ::promptUnlock,
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.settingsRepository.settings.collect { settings ->
                    if (settings.screenshotsBlocked) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val settings = container.settingsRepository.getSettings()
            val shouldLock = settings.biometricLockEnabled &&
                (!unlockedOnce || backgroundedAtElapsed == null ||
                    SystemClock.elapsedRealtime() - (backgroundedAtElapsed ?: 0L) > LOCK_AFTER_MILLIS)
            securityReady = true
            if (shouldLock) {
                isLocked = true
                promptUnlock()
            } else {
                isLocked = false
            }
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) backgroundedAtElapsed = SystemClock.elapsedRealtime()
        super.onStop()
    }

    private fun promptUnlock() {
        if (!securityReady || promptShowing) return
        promptShowing = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    isLocked = false
                    unlockedOnce = true
                    backgroundedAtElapsed = SystemClock.elapsedRealtime()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                    isLocked = true
                }

                override fun onAuthenticationFailed() {
                    isLocked = true
                }
            },
        )
        val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_title))
            .setSubtitle(getString(R.string.unlock_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    companion object {
        private const val LOCK_AFTER_MILLIS = 30_000L
    }
}
