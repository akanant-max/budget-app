package com.argentum.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(this)
        Repo.applyRecurring()
        Reminders.schedule(this)

        setContent {
            ArgentumTheme {
                val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                var screen by remember { mutableStateOf("home") }
                if (Repo.lockOn && !unlocked) {
                    LaunchedEffect(Unit) { prompt() }
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Button(onClick = { prompt() }) { Text("Unlock Argentum") }
                    }
                } else when (screen) {
                    "ledger" -> { androidx.activity.compose.BackHandler { screen = "home" }; LedgerScreen { screen = "home" } }
                    "report" -> { androidx.activity.compose.BackHandler { screen = "home" }; ReportScreen { screen = "home" } }
                    "settings" -> { androidx.activity.compose.BackHandler { screen = "home" }
                        SettingsScreen({ screen = "home" }, { unlocked = true }) }
                    else -> HomeScreen({ screen = "ledger" }, { screen = "report" }, { screen = "settings" })
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        unlocked = false
    }

    private fun prompt() {
        val auth = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlocked = true // no lock set on the phone: don't lock the user out
            return
        }
        val p = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
            })
        p.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Argentum")
                .setAllowedAuthenticators(auth).build()
        )
    }
}
