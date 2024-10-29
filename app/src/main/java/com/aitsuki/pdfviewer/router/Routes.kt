package com.aitsuki.pdfviewer.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.aitsuki.pdfviewer.screen.HomeScreen
import com.aitsuki.pdfviewer.screen.PdfScreen
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("NavController not found!")
}

@Composable
fun AppRouter() {
    val controller = rememberNavController()
    CompositionLocalProvider(LocalNavController provides controller) {
        NavHost(controller, startDestination = Routes.Home::class) {
            composable<Routes.Home> { HomeScreen() }
            composable<Routes.Pdf> { navBackStackEntry ->
                val route = navBackStackEntry.toRoute<Routes.Pdf>()
                PdfScreen(route.path)
            }
        }
    }
}

object Routes {

    @Serializable
    object Home

    @Serializable
    class Pdf(
        /**
         * PDF filepath or download url
         */
        @SerialName("path") val path: String,
    )
}