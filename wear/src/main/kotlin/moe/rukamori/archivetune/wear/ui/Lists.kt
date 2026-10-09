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
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

/** How a list from the phone is ordered on the watch. [DEFAULT] keeps the phone's own order. */
enum class SortOrder(@StringRes val label: Int) {
    DEFAULT(R.string.sort_default),
    TITLE_ASC(R.string.sort_title_asc),
    TITLE_DESC(R.string.sort_title_desc),
    ARTIST(R.string.sort_artist),
    ;

    fun next(): SortOrder = entries[(ordinal + 1) % entries.size]

    // Folders stay above songs so a sorted playlist doesn't bury its sub-folders.
    fun apply(items: List<MediaEntry>): List<MediaEntry> =
        when (this) {
            DEFAULT -> items
            TITLE_ASC -> items.sortedWith(compareBy<MediaEntry> { !it.browsable }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            TITLE_DESC -> items.sortedWith(compareBy<MediaEntry> { !it.browsable }.thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
            ARTIST -> items.sortedWith(compareBy<MediaEntry> { !it.browsable }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.subtitle }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
}

/** Sort mode that survives rotation and process death, keyed per list. */
@Composable
fun rememberSortOrder(key: String): Pair<SortOrder, () -> Unit> {
    var order by rememberSaveable(key) { mutableStateOf(SortOrder.DEFAULT) }
    return order to { order = order.next() }
}

/** One row that shows the current sort and cycles to the next on tap. */
fun ScalingLazyListScope.sortButton(
    order: SortOrder,
    onCycle: () -> Unit,
) {
    item {
        Button(
            onClick = onCycle,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(),
            icon = { Icon(painterResource(R.drawable.sort), contentDescription = null) },
            secondaryLabel = { Text(stringResource(order.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        ) {
            Text(stringResource(R.string.sort_by))
        }
    }
}

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
