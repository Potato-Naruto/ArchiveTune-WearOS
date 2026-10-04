/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
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
import moe.rukamori.archivetune.wear.ui.SettingsScreen
import moe.rukamori.archivetune.wear.ui.ThemeScreen
import moe.rukamori.archivetune.wear.ui.VolumeScreen

class MainActivity : ComponentActivity() {
    private val viewModel: RemoteViewModel by viewModels()

    // Only asked so the track can stay on the watch face after the app is left; the app works
    // the same without it.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            playFromSearch(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
            LaunchedEffect(keepScreenOn) {
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            ArchiveTuneWearTheme(theme) {
                AppScaffold {
                    val navController = rememberSwipeDismissableNavController()
                    val backToPlayer = { navController.popBackStack(ROUTE_HOME, inclusive = false) }
                    SwipeDismissableNavHost(navController = navController, startDestination = ROUTE_HOME) {
                        composable(ROUTE_HOME) {
                            HomeScreen(
                                viewModel = viewModel,
                                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
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
                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                viewModel = viewModel,
                                onOpenThemes = { navController.navigate(ROUTE_THEMES) },
                                onOpenVolume = { navController.navigate(ROUTE_VOLUME) },
                            )
                        }
                        composable(ROUTE_VOLUME) { VolumeScreen(viewModel) }
                        composable(ROUTE_THEMES) { ThemeScreen(viewModel) }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        playFromSearch(intent)
    }

    /**
     * "Play … on ArchiveTune" from a voice assistant arrives as this intent. The watch plays
     * nothing itself, so the query goes to the phone like any other search.
     */
    private fun playFromSearch(intent: Intent?) {
        if (intent?.action != ACTION_PLAY_FROM_SEARCH) return
        val query = intent.getStringExtra(SearchManager.QUERY)?.trim().orEmpty()
        if (query.isNotEmpty()) viewModel.playSearch(query)
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
        const val ACTION_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH"
        const val ROUTE_HOME = "home"
        const val ROUTE_SETTINGS = "settings"
        const val ROUTE_VOLUME = "volume"
        const val ROUTE_THEMES = "themes"
    }
}
