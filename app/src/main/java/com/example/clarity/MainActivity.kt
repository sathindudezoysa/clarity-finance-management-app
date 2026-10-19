package com.example.clarity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.example.clarity.presentation.navigation.Screen
import com.example.clarity.presentation.navigation.SetupNavGraph
import com.example.clarity.presentation.theme.ClarityTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.content.Intent
import androidx.compose.runtime.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var budgetSignals: com.example.clarity.data.sync.BudgetSignals
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

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readBudgetLaunch(intent)
        setContent {
            ClarityTheme {
                val navController = rememberNavController()
                val startDestination = if (firebaseAuth.currentUser != null) {
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
                        if (firebaseAuth.currentUser?.uid == owner) navController.navigate(Screen.Budgets.createRoute(id)) { launchSingleTop = true }
                        budgetLaunch = null
                        intent.removeExtra("budgetOwner")
                        intent.removeExtra("budgetId")
                    }
                }
            }
        }
    }
}
