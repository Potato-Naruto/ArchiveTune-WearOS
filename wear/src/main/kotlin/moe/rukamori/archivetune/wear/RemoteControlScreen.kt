/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import kotlinx.coroutines.launch

/**
 * The whole watch UI: a cross of five buttons, which is the arrangement that keeps every target
 * full-size inside a round display.
 */
@Composable
fun RemoteControlScreen(messagingClient: WearMessagingClient) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun send(
        path: String,
        text: String? = null,
    ) {
        scope.launch {
            val sent = if (text == null) messagingClient.send(path) else messagingClient.send(path, text)
            if (!sent) Toast.makeText(context, R.string.phone_unreachable, Toast.LENGTH_SHORT).show()
        }
    }

    val voiceSearchLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val query =
                result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.trim()
            if (result.resultCode == Activity.RESULT_OK && !query.isNullOrEmpty()) {
                send(WearMessagingClient.PATH_SEARCH_VOICE, query)
            }
        }
    val voiceSearchPrompt = stringResource(R.string.voice_search_prompt)

    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledIconButton(onClick = { send(WearMessagingClient.PATH_PLAY) }) {
                ButtonIcon(R.drawable.play, R.string.play)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(onClick = { send(WearMessagingClient.PATH_SKIP_PREV) }) {
                    ButtonIcon(R.drawable.skip_previous, R.string.skip_previous)
                }
                FilledTonalIconButton(
                    onClick = {
                        val intent =
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                ).putExtra(RecognizerIntent.EXTRA_PROMPT, voiceSearchPrompt)
                        try {
                            voiceSearchLauncher.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast
                                .makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                ) {
                    ButtonIcon(R.drawable.mic, R.string.voice_search)
                }
                FilledTonalIconButton(onClick = { send(WearMessagingClient.PATH_SKIP_NEXT) }) {
                    ButtonIcon(R.drawable.skip_next, R.string.skip_next)
                }
            }
            FilledIconButton(onClick = { send(WearMessagingClient.PATH_PAUSE) }) {
                ButtonIcon(R.drawable.pause, R.string.pause)
            }
        }
    }
}

@Composable
private fun ButtonIcon(
    @DrawableRes icon: Int,
    @StringRes description: Int,
) {
    Icon(painter = painterResource(icon), contentDescription = stringResource(description))
}
