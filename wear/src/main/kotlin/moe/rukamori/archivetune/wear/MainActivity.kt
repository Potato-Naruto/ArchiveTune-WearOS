/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import moe.rukamori.archivetune.wear.ui.BrowseScreen
import moe.rukamori.archivetune.wear.ui.HomeScreen
import moe.rukamori.archivetune.wear.ui.SearchScreen
import moe.rukamori.archivetune.wear.ui.ThemeScreen
import moe.rukamori.archivetune.wear.ui.VolumeScreen

class MainActivity : ComponentActivity() {
    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            ArchiveTuneWearTheme(theme) {
                AppScaffold {
                    val navController = rememberSwipeDismissableNavController()
                    val backToPlayer = { navController.popBackStack(ROUTE_HOME, inclusive = false) }
                    SwipeDismissableNavHost(navController = navController, startDestination = ROUTE_HOME) {
                        composable(ROUTE_HOME) {
                            HomeScreen(
                                viewModel = viewModel,
                                onOpenVolume = { navController.navigate(ROUTE_VOLUME) },
                                onOpenThemes = { navController.navigate(ROUTE_THEMES) },
                                onOpenSearch = { query -> navController.navigate("search/${Uri.encode(query)}") },
                                onBrowse = { entry -> navController.navigate(browseRoute(entry)) },
                            )
                        }
                        composable(
                            "browse/{id}/{title}/{playable}",
                            arguments =
                                listOf(
                                    navArgument("id") { type = NavType.StringType },
                                    navArgument("title") { type = NavType.StringType },
                                    navArgument("playable") { type = NavType.BoolType },
                                ),
                        ) { entry ->
                            val arguments = requireNotNull(entry.arguments)
                            BrowseScreen(
                                viewModel = viewModel,
                                parentId = arguments.getString("id").orEmpty(),
                                title = arguments.getString("title").orEmpty(),
                                playable = arguments.getBoolean("playable"),
                                onBrowse = { child -> navController.navigate(browseRoute(child)) },
                                onPlayed = { backToPlayer() },
                            )
                        }
                        composable("search/{query}") { entry ->
                            SearchScreen(
                                viewModel = viewModel,
                                query = entry.arguments?.getString("query").orEmpty(),
                                onBrowse = { child -> navController.navigate(browseRoute(child)) },
                                onPlayed = { backToPlayer() },
                            )
                        }
                        composable(ROUTE_VOLUME) { VolumeScreen(viewModel) }
                        composable(ROUTE_THEMES) { ThemeScreen(viewModel) }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    override fun onStop() {
        viewModel.stop()
        super.onStop()
    }

    private fun browseRoute(entry: MediaEntry): String =
        "browse/${Uri.encode(entry.id)}/${Uri.encode(entry.title.ifEmpty { " " })}/${entry.playable}"

    private companion object {
        const val ROUTE_HOME = "home"
        const val ROUTE_VOLUME = "volume"
        const val ROUTE_THEMES = "themes"
    }
}
