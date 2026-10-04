/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import moe.rukamori.archivetune.wear.ListState
import moe.rukamori.archivetune.wear.MediaEntry
import moe.rukamori.archivetune.wear.R

/** A row for one library entry: a folder to open or a song to play. */
@Composable
fun MediaEntryButton(
    entry: MediaEntry,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        icon = { Icon(painterResource(icon), contentDescription = null) },
        secondaryLabel =
            entry.subtitle.takeIf { it.isNotBlank() }?.let { subtitle ->
                { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
    ) {
        Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The body of a list that comes from the phone: a spinner, a retry row, an empty note, or [items]. */
fun ScalingLazyListScope.mediaEntries(
    state: ListState?,
    onRetry: () -> Unit,
    @DrawableRes icon: (MediaEntry) -> Int,
    onClick: (MediaEntry) -> Unit,
    showEmpty: Boolean = true,
) {
    when (state) {
        null, ListState.Loading -> {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        ListState.Failed -> {
            item {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(),
                    secondaryLabel = { Text(stringResource(R.string.tap_to_retry)) },
                ) {
                    Text(stringResource(R.string.load_failed))
                }
            }
        }

        is ListState.Loaded -> {
            if (state.items.isEmpty() && showEmpty) {
                item {
                    Text(
                        text = stringResource(R.string.nothing_here),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    )
                }
            }
            items(state.items, key = { it.id }) { entry ->
                MediaEntryButton(entry = entry, icon = icon(entry), onClick = { onClick(entry) })
            }
        }
    }
}

/**
 * Launches the watch's speech input — which also offers its keyboard — and hands back what was
 * said. Returns the function that opens it.
 */
@Composable
fun rememberSpeechInput(onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val prompt = stringResource(R.string.voice_search_prompt)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val query =
                result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.trim()
            if (result.resultCode == Activity.RESULT_OK && !query.isNullOrEmpty()) onResult(query)
        }
    return {
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
