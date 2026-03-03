package com.coinlab.app.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinlab.app.R
import com.coinlab.app.ui.theme.*
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    throw IllegalStateException("No Activity found in context")
}

private const val WEB_CLIENT_ID = "484421142982-7clmnm7k38bqrjihi475dlu97ud97k1r.apps.googleusercontent.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.resetState()
        contentVisible = true
    }

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            onRegisterSuccess()
        }
    }

    // Google Sign-Up launcher
    fun launchGoogleSignUp() {
        coroutineScope.launch {
            try {
                val activity = context.findActivity()
                val credentialManager = CredentialManager.create(activity)

                val signInOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()

                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential

                // GetSignInWithGoogleOption returns CustomCredential
                // Try to extract GoogleIdTokenCredential from any credential type
                val googleCredential = try {
                    GoogleIdTokenCredential.createFrom(credential.data)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    android.util.Log.e("RegisterScreen", "Failed to parse credential type=${credential.type}", e)
                    viewModel.onGoogleSignInError("Google kimlik bilgisi okunamadı: ${e.message}")
                    return@launch
                }

                val idToken = googleCredential.idToken
                val email = googleCredential.id
                val displayName = googleCredential.displayName ?: ""
                val photoUrl = googleCredential.profilePictureUri?.toString() ?: ""

                viewModel.signInWithGoogle(
                    idToken = idToken,
                    googleEmail = email,
                    googleDisplayName = displayName,
                    googlePhotoUrl = photoUrl
                )

            } catch (e: GetCredentialCancellationException) {
                // User cancelled — do nothing
            } catch (e: NoCredentialException) {
                viewModel.onGoogleSignInError(
                    "Bu cihazda Google hesabı bulunamadı. Lütfen önce Ayarlar'dan bir Google hesabı ekleyin."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("RegisterScreen", "Google sign-up error", e)
                viewModel.onGoogleSignInError(
                    "Google kaydı başarısız: ${e.localizedMessage ?: "Bilinmeyen hata"}"
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF050510),
                        Color(0xFF0A0A1A),
                        Color(0xFF0D0D25)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Header
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -30 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                Color.Black,
                                shape = RoundedCornerShape(18.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = "CoinLab",
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "CoinLab'a Katıl",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Text(
                        text = "Google hesabınla hemen başla",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Register Form
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(600, delayMillis = 200)) + slideInVertically(tween(600, delayMillis = 200)) { 40 }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A2E).copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_google),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Google ile Kayıt Ol",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Google hesabınızla hızlıca kayıt olun",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Remember Me
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onRememberMeChange(!uiState.rememberMe) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = uiState.rememberMe,
                                onCheckedChange = viewModel::onRememberMeChange,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = CoinLabGreen,
                                    checkmarkColor = Color.Black
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Beni Hatırla",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Otomatik giriş için işaretleyin",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }

                        // Error
                        if (uiState.generalError != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = uiState.generalError ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Google Sign-Up button
                        Button(
                            onClick = { launchGoogleSignUp() },
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CoinLabGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.ic_google),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Google ile Kayıt Ol",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Login link
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(600, delayMillis = 400))
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Zaten hesabın var mı? ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Giriş Yap",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = CoinLabGreen,
                        modifier = Modifier.clickable { onNavigateToLogin() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
