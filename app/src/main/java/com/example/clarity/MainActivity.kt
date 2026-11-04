package com.example.clarity

import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.rememberNavController
import com.example.clarity.data.sync.BudgetSignals
import com.example.clarity.presentation.auth.MainAuthViewModel
import com.example.clarity.presentation.common.BiometricPromptManager
import com.example.clarity.presentation.navigation.Screen
import com.example.clarity.presentation.navigation.SetupNavGraph
import com.example.clarity.presentation.theme.ClarityTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var budgetSignals: BudgetSignals
    @Inject lateinit var firebaseAuth: FirebaseAuth

    private val mainAuthViewModel: MainAuthViewModel by viewModels()
    private var budgetLaunch by mutableStateOf<Pair<String, String>?>(null)

    private fun readBudgetLaunch(value: Intent?) {
        val owner = value?.getStringExtra("budgetOwner")
        val id = value?.getStringExtra("budgetId")
        budgetLaunch = if (owner != null && owner == firebaseAuth.currentUser?.uid &&
            id != null && id.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])_[0-9a-f]{64}"))) owner to id else null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readBudgetLaunch(intent)
    }

    override fun onResume() {
        super.onResume()
        budgetSignals.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        readBudgetLaunch(intent)

        setContent {
            ClarityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val lifecycleOwner = LocalLifecycleOwner.current
                    val isBiometricEnabled by mainAuthViewModel.isBiometricEnabled.collectAsState()
                    val isUnlocked by mainAuthViewModel.isUnlocked.collectAsState()
                    val currentUser = firebaseAuth.currentUser

                    // Automatically lock app when minimized (ON_STOP)
                    DisposableEffect(lifecycleOwner, isBiometricEnabled) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_STOP && isBiometricEnabled) {
                                mainAuthViewModel.lockApp()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    val shouldShowLock = currentUser != null && isBiometricEnabled && !isUnlocked

                    if (shouldShowLock) {
                        LaunchedEffect(isBiometricEnabled) {
                            if (!isBiometricEnabled) {
                                mainAuthViewModel.unlockApp()
                                return@LaunchedEffect
                            }

                            if (!BiometricPromptManager.isBiometricAvailable(this@MainActivity)) {
                                mainAuthViewModel.lockApp()
                                return@LaunchedEffect
                            }

                            BiometricPromptManager.showBiometricPrompt(
                                activity = this@MainActivity,
                                onSuccess = { mainAuthViewModel.unlockApp() },
                                onError = { mainAuthViewModel.lockApp() }
                            )
                        }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Text(
                                    text = "Clarity is Locked",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    if (!isBiometricEnabled) {
                                        mainAuthViewModel.unlockApp()
                                        return@Button
                                    }
                                    if (!BiometricPromptManager.isBiometricAvailable(this@MainActivity)) {
                                        mainAuthViewModel.lockApp()
                                        return@Button
                                    }
                                    BiometricPromptManager.showBiometricPrompt(
                                        activity = this@MainActivity,
                                        onSuccess = { mainAuthViewModel.unlockApp() },
                                        onError = { mainAuthViewModel.lockApp() }
                                    )
                                }) {
                                    Text("Unlock with Biometrics")
                                }
                            }
                        }
                    } else {
                        // Regular App NavGraph Navigation
                        val navController = rememberNavController()
                        val startDestination = if (currentUser != null) {
                            Screen.Main.route
                        } else {
                            Screen.Login.route
                        }
                        SetupNavGraph(
                            navController = navController,
                            startDestination = startDestination
                        )
                        LaunchedEffect(budgetLaunch) {
                            budgetLaunch?.let { (owner, id) ->
                                if (currentUser?.uid == owner) navController.navigate(Screen.Budgets.createRoute(id)) { launchSingleTop = true }
                                budgetLaunch = null
                                intent.removeExtra("budgetOwner")
                                intent.removeExtra("budgetId")
                            }
                        }
                    }
                }
            }
        }
    }
}