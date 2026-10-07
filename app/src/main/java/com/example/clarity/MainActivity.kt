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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            }
        }
    }
}