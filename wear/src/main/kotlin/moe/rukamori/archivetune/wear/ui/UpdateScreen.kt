/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import moe.rukamori.archivetune.wear.R
import moe.rukamori.archivetune.wear.UpdateState
import moe.rukamori.archivetune.wear.UpdateViewModel
import moe.rukamori.archivetune.wear.WearUpdater

@Composable
fun UpdateScreen(viewModel: UpdateViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val autoUpdate by viewModel.autoUpdate.collectAsStateWithLifecycle()
    val installing by viewModel.installing.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val busy =
        state is UpdateState.Checking ||
            state is UpdateState.Downloading ||
            installing == WearUpdater.InstallState.Installing

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.updates)) } }
            item {
                Text(
                    text = stringResource(R.string.version_name, viewModel.version),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = viewModel::check,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(painterResource(R.drawable.sync), contentDescription = null)
                        }
                    },
                    secondaryLabel =
                        when {
                            installing == WearUpdater.InstallState.Installing ->
                                ({ Text(stringResource(R.string.installing)) })
                            installing == WearUpdater.InstallState.Failed || state == UpdateState.Failed ->
                                ({ Text(stringResource(R.string.update_failed)) })
                            state == UpdateState.Checking -> ({ Text(stringResource(R.string.checking)) })
                            state == UpdateState.UpToDate -> ({ Text(stringResource(R.string.up_to_date)) })
                            state is UpdateState.Downloading ->
                                ({ Text(stringResource(R.string.downloading_percent, (state as UpdateState.Downloading).percent)) })
                            else -> null
                        },
                ) {
                    Text(stringResource(R.string.check_for_updates))
                }
            }
            (state as? UpdateState.Available)?.let { available ->
                item {
                    Button(
                        onClick = {
                            if (viewModel.canInstall()) {
                                viewModel.install(available.release)
                            } else {
                                openInstallPermission(context)
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(painterResource(R.drawable.add), contentDescription = null) },
                        secondaryLabel =
                            if (viewModel.canInstall()) null else ({ Text(stringResource(R.string.allow_installs)) }),
                    ) {
                        Text(stringResource(R.string.install_update, available.release.tag))
                    }
                }
            }
            item {
                SwitchButton(
                    checked = autoUpdate,
                    onCheckedChange = { enabled ->
                        viewModel.setAutoUpdate(enabled)
                        if (enabled && !viewModel.canInstall()) openInstallPermission(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.auto_update)) },
                    secondaryLabel = { Text(stringResource(R.string.auto_update_hint)) },
                )
            }
        }
    }
}

/** Android gates sideloading per app; this is the screen where the user grants it. */
private fun openInstallPermission(context: Context) {
    val intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
