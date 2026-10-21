package com.example.clarity.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

enum class BottomNavDestination(
    val label: String,
    val icon: ImageVector,
    val routePattern: String,
    val navigateRoute: String
) {
    HOME(
        label = "Home",
        icon = Icons.Outlined.Home,
        routePattern = Screen.Main.route,
        navigateRoute = Screen.Main.route
    ),
    ACCOUNTS(
        label = "Accounts",
        icon = Icons.Outlined.AccountBox,
        routePattern = Screen.AccountList.route,
        navigateRoute = Screen.AccountList.route
    ),
    BUDGETS(
        label = "Budgets",
        icon = Icons.Outlined.DateRange,
        routePattern = Screen.Budgets.route,
        navigateRoute = Screen.Budgets.createRoute()
    ),
    GOALS(
        label = "Goals",
        icon = Icons.Outlined.Star,
        routePattern = Screen.Goals.route,
        navigateRoute = Screen.Goals.route
    )
}

@Composable
fun ClarityBottomNavBar(
    navController: NavHostController,
    currentDestination: BottomNavDestination
) {
    val colors = MaterialTheme.colorScheme

    NavigationBar(
        containerColor = colors.surface,
        contentColor = colors.onSurface
    ) {
        BottomNavDestination.values().forEach { item ->
            val selected = item == currentDestination
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.navigateRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(text = item.label)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onSecondaryContainer,
                    selectedTextColor = colors.onSurface,
                    indicatorColor = colors.secondaryContainer,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant
                )
            )
        }
    }
}
