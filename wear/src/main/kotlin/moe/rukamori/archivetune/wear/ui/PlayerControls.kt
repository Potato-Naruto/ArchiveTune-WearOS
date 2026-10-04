/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val HOLD_BEFORE_SEEK_MS = 1_000L
private const val SEEK_REPEAT_MS = 600L

// A crown reports a stream of small movements; this is how far it turns for one volume step.
private const val ROTARY_PIXELS_PER_STEP = 48f

// Present on watches whose rotary input clicks through fixed detents — Samsung's rotating bezel.
private const val FEATURE_LOW_RES_ROTARY = "android.hardware.rotaryencoder.lowres"

private const val WAVE_COUNT = 11
private const val WAVE_PERIOD_MS = 2_400f

/**
 * Turns crown and bezel rotation into volume steps and takes focus whenever its screen is the
 * active one, which rotary events need to arrive at all.
 */
@Composable
fun Modifier.volumeRotary(
    volume: Int,
    maxVolume: Int,
    onVolumeChange: (Int) -> Unit,
): Modifier {
    val context = LocalContext.current
    val view = LocalView.current
    // A bezel detent is one deliberate click, so it is always exactly one step, whatever distance
    // the system reports for it.
    val stepPerEvent = remember { context.packageManager.hasSystemFeature(FEATURE_LOW_RES_ROTARY) }
    val currentVolume by rememberUpdatedState(volume)
    val currentMax by rememberUpdatedState(maxVolume)
    val currentOnChange by rememberUpdatedState(onVolumeChange)
    var accumulated by remember { mutableFloatStateOf(0f) }
    return this
        .onRotaryScrollEvent { event ->
            val pixels = event.verticalScrollPixels
            val steps =
                if (stepPerEvent) {
                    if (pixels > 0f) 1 else if (pixels < 0f) -1 else 0
                } else {
                    accumulated += pixels
                    (accumulated / ROTARY_PIXELS_PER_STEP).toInt().also { accumulated -= it * ROTARY_PIXELS_PER_STEP }
                }
            val target = (currentVolume + steps).coerceIn(0, currentMax)
            if (target != currentVolume) {
                // One tick per change, and none at either end of the range, so the wrist can tell
                // the volume has stopped moving.
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                    } else {
                        HapticFeedbackConstants.CLOCK_TICK
                    },
                )
                currentOnChange(target)
            }
            true
        }.requestFocusOnHierarchyActive()
        .focusable()
}

/**
 * Playback progress as a ring whose played part is a wave that drifts while music is playing and
 * holds still when it is not; the unplayed part stays a plain thin arc.
 */
@Composable
fun WavyProgressRing(
    progress: Float,
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        val startPhase = phase
        val startMs = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                phase = startPhase + (now - startMs) / WAVE_PERIOD_MS * (2f * PI.toFloat())
            }
        }
    }
    Canvas(modifier) {
        val strokeWidth = 3.dp.toPx()
        val amplitude = 1.8.dp.toPx()
        val radius = size.minDimension / 2f - strokeWidth / 2f - amplitude
        val sweep = 360f * progress.coerceIn(0f, 1f)

        // Leaves a small gap either side of the played part so the two never touch.
        val gap = 7f
        val trackStart = -90f + sweep + gap
        val trackSweep = 360f - sweep - gap * 2f
        if (trackSweep > 0f) {
            drawArc(
                color = trackColor,
                startAngle = trackStart,
                sweepAngle = trackSweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size =
                    androidx.compose.ui.geometry
                        .Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokeWidth * 0.6f, cap = StrokeCap.Round),
            )
        }

        if (sweep > 0.5f) {
            val path = Path()
            var degrees = 0f
            while (true) {
                val angle = Math.toRadians((degrees - 90f).toDouble()).toFloat()
                val wave = sin(Math.toRadians((degrees * WAVE_COUNT).toDouble()).toFloat() - phase)
                val r = radius + amplitude * wave
                val x = center.x + r * cos(angle)
                val y = center.y + r * sin(angle)
                if (degrees == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                if (degrees >= sweep) break
                degrees = (degrees + 2f).coerceAtMost(sweep)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * Previous / next. A tap skips; keeping it pressed for a second starts seeking instead, one
 * [onSeekStep] at a time for as long as it is held.
 */
@Composable
fun SkipButton(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    onClick: () -> Unit,
    onSeekStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnSeekStep by rememberUpdatedState(onSeekStep)
    val label = stringResource(description)
    var pressed by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f) else Color.Transparent,
                ).semantics {
                    role = Role.Button
                    contentDescription = label
                    onClick {
                        currentOnClick()
                        true
                    }
                }.pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        pressed = true
                        var finished = false
                        val up =
                            withTimeoutOrNull(HOLD_BEFORE_SEEK_MS) {
                                waitForUpOrCancellation().also { finished = true }
                            }
                        if (finished) {
                            if (up != null) currentOnClick()
                        } else {
                            do {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnSeekStep()
                                val released =
                                    withTimeoutOrNull(SEEK_REPEAT_MS) {
                                        waitForUpOrCancellation()
                                        true
                                    }
                            } while (released == null)
                        }
                        pressed = false
                    }
                },
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(26.dp),
        )
    }
}
