package com.coinlab.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.coinlab.app.data.preferences.AuthPreferences
import com.coinlab.app.data.preferences.UserPreferences
import com.coinlab.app.navigation.CoinLabNavHost
import com.coinlab.app.ui.theme.CoinLabTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var authPreferences: AuthPreferences

    private var isAuthenticated by mutableStateOf(false)
    private var biometricRequired by mutableStateOf(false)
    private var autoLogin by mutableStateOf(false)
    private var deepLinkCoinId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check biometric preference (use IO dispatcher to avoid ANR)
        val biometricEnabled = runBlocking(kotlinx.coroutines.Dispatchers.IO) { userPreferences.biometricEnabled.first() }
        biometricRequired = biometricEnabled

        // Check auto-login ("Beni Hatırla")
        autoLogin = runBlocking(kotlinx.coroutines.Dispatchers.IO) { authPreferences.shouldAutoLogin() }

        // Handle deep link
        handleIntent(intent)

        if (biometricEnabled) {
            checkBiometric()
        } else {
            isAuthenticated = true
        }

        setContent {
            val themeMode by userPreferences.themeMode.collectAsState(initial = "system")
            val currency by userPreferences.currency.collectAsState(initial = "TRY")

            CoinLabTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isAuthenticated || !biometricRequired) {
                        CoinLabNavHost(deepLinkCoinId = deepLinkCoinId, autoLogin = autoLogin)
                    } else {
                        // Lock screen
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔒 CoinLab",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            // Handle coinlabtr.com/coin/{id} deep links
            if (uri.host == "coinlabtr.com" && uri.pathSegments.size >= 2) {
                if (uri.pathSegments[0] == "coin") {
                    deepLinkCoinId = uri.pathSegments[1]
                }
            }
        }
        // Handle navigation from notifications
        intent?.getStringExtra("navigate_to")?.let { target ->
            when (target) {
                "portfolio" -> { /* handled in nav host */ }
                "news" -> { /* handled in nav host */ }
            }
        }
    }

    private fun checkBiometric() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> showBiometricPrompt()
            else -> {
                // Device doesn't support biometric, allow access
                isAuthenticated = true
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isAuthenticated = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        finish()
                    }
                }

                override fun onAuthenticationFailed() {
                    // Keep showing prompt
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("CoinLab")
            .setSubtitle("Uygulamaya erişmek için kimliğinizi doğrulayın")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
