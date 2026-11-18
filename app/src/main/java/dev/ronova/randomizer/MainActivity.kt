package dev.ronova.randomizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.ronova.randomizer.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RandomizerApp
        setContent {
            RandomizerTheme {
                CompositionLocalProvider(LocalPhotos provides app.photos) {
                    AppNav()
                }
            }
        }
    }
}

@Composable
private fun AppNav() {
    val vm: MainVm = viewModel()
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "main") {
        composable("main") { MainScreen(vm, nav) }
        composable("spin") { SpinScreen(vm, nav) }
        composable("final") { FinalScreen(vm, nav) }
        composable("editor") { EditorScreen(vm, nav) }
        composable("history") { HistoryScreen(vm, nav) }
        composable("transfer") { TransferScreen(vm, nav) }
    }
}
