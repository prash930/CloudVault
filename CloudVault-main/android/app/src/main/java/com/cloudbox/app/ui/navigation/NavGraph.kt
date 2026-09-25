package com.cloudbox.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cloudbox.app.data.local.TokenManager
import com.cloudbox.app.ui.screens.auth.ForgotPasswordScreen
import com.cloudbox.app.ui.screens.auth.LoginScreen
import com.cloudbox.app.ui.screens.auth.RegisterScreen
import com.cloudbox.app.ui.screens.home.HomeScreen
import com.cloudbox.app.ui.screens.home.HomeViewModel
import com.cloudbox.app.ui.screens.home.UploadQueueScreen
import com.cloudbox.app.ui.screens.preview.FilePreviewScreen
import com.cloudbox.app.ui.screens.splash.SplashScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val startDestination = when {
        TokenManager.isLoggedIn() -> "home"
        !TokenManager.hasSeenOnboarding() -> "splash"
        else -> "login"
    }

    @Composable
    fun homeViewModel(): HomeViewModel {
        val homeEntry = navController.getBackStackEntry("home")
        return viewModel<HomeViewModel>(viewModelStoreOwner = homeEntry)
    }

    fun navigateHomeClearing() {
        navController.navigate("home") {
            popUpTo(navController.graph.startDestinationId) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("splash") {
            SplashScreen(
                onGetStarted = {
                    navController.navigate("register") { popUpTo("splash") { inclusive = true } }
                },
                onLogin = {
                    navController.navigate("login") { popUpTo("splash") { inclusive = true } }
                }
            )
        }
        composable("login") {
            LoginScreen(
                onLoginSuccess = { navigateHomeClearing() },
                onNavigateToRegister = { navController.navigate("register") },
                onNavigateToForgotPassword = { navController.navigate("forgot_password") }
            )
        }
        composable("register") {
            RegisterScreen(
                onNavigateBack = { navController.popBackStack() },
                onRegisterSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("forgot_password") {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("home") {
            HomeScreen(
                onLogout = {
                    TokenManager.clearAll()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onOpenUploadQueue = { navController.navigate("upload_queue") },
                onOpenPreview = { fileId -> navController.navigate("preview/$fileId") },
                viewModel = homeViewModel()
            )
        }
        composable("upload_queue") {
            UploadQueueScreen(
                onBack = { navController.popBackStack() },
                viewModel = homeViewModel()
            )
        }
        composable("preview/{fileId}") { entry ->
            val fileId = entry.arguments?.getString("fileId")?.toIntOrNull() ?: -1
            FilePreviewScreen(
                fileId = fileId,
                onBack = { navController.popBackStack() },
                viewModel = homeViewModel()
            )
        }
    }
}