/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import moe.rukamori.archivetune.wear.Decor
import moe.rukamori.archivetune.wear.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Everything a decorated theme shows is drawn here from shapes: there are no image assets, so the
// stickers stay sharp on any watch and add nothing to the APK.

private val Skin = Color(0xFFFFDFC4)
private val Blush = Color(0x66FF7A8A)
private val Ink = Color(0xFF2A2230)

/** The gradient a decorated theme shows behind the player when there is no album cover. */
fun Decor.backdrop(): Brush? =
    when (this) {
        Decor.NONE -> null
        Decor.SAKURA -> Brush.verticalGradient(listOf(Color(0xFF3A1A2B), Color(0xFF6B2F4C), Color(0xFF2A1420)))
        Decor.NARUTO -> Brush.verticalGradient(listOf(Color(0xFF16233F), Color(0xFF6A3408), Color(0xFF2B1608)))
        Decor.SASUKE -> Brush.verticalGradient(listOf(Color(0xFF0B0D22), Color(0xFF2A1E55), Color(0xFF12102A)))
        Decor.SHIKAMARU -> Brush.verticalGradient(listOf(Color(0xFF1F4868), Color(0xFF4F86AC), Color(0xFF2C4727)))
        Decor.SUNSET -> Brush.verticalGradient(listOf(Color(0xFF3B1E54), Color(0xFFB8405E), Color(0xFFE98A4B)))
        Decor.NIGHT_SKY -> Brush.verticalGradient(listOf(Color(0xFF050818), Color(0xFF131A40), Color(0xFF232C5C)))
        Decor.COASTAL -> Brush.verticalGradient(listOf(Color(0xFF0B3340), Color(0xFF1A6E7A), Color(0xFF0D3F48)))
    }

/**
 * Small motifs set around the edge of the round player. They keep clear of the left side (the
 * volume bar), the top (the clock) and the bottom (the page dots).
 */
@Composable
fun RimStickers(
    decor: Decor,
    modifier: Modifier = Modifier,
) {
    if (decor == Decor.NONE) return
    Canvas(modifier.fillMaxSize()) {
        val radius = size.minDimension / 2f
        val unit = 1.dp.toPx()
        RIM_ANGLES.forEachIndexed { index, degrees ->
            val angle = degrees * PI.toFloat() / 180f
            val at = Offset(center.x + radius * 0.88f * cos(angle), center.y + radius * 0.88f * sin(angle))
            drawMotif(decor, index, at, unit * if (index % 2 == 0) 8f else 6f)
        }
    }
}

// Degrees clockwise from three o'clock.
private val RIM_ANGLES = listOf(-62f, -38f, -14f, 14f, 38f, 62f, 116f, -118f)

/**
 * The picture at the top of the library: the theme's figure with its stickers either side. The
 * figures are original drawings in a chibi style, not copies of any existing artwork.
 */
@Composable
fun ThemeBanner(
    decor: Decor,
    modifier: Modifier = Modifier,
) {
    if (decor == Decor.NONE) return
    if (decor == Decor.SHIKAMARU) {
        // His line goes where the other themes start their picture, with the nap underneath it.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.shikamaru_quote),
                // The list pins the banner's bottom edge, so padding cannot move this; the offset
                // drops it clear of the clock drawn over the top of the list.
                modifier = Modifier.offset(y = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BannerCanvas(decor, modifier)
        }
    } else {
        BannerCanvas(decor, modifier)
    }
}

@Composable
private fun BannerCanvas(
    decor: Decor,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val unit = size.height / 56f
        val middle = Offset(size.width / 2f, size.height / 2f)
        when (decor) {
            Decor.SAKURA -> chibi(middle, size.height, ChibiLook.Blossom)
            Decor.NARUTO -> chibi(middle, size.height, ChibiLook.Fox)
            Decor.SASUKE -> chibi(middle, size.height, ChibiLook.Storm)
            Decor.SHIKAMARU -> nappingChibi(middle, size.height)
            Decor.SUNSET -> sun(middle, unit * 20f)
            Decor.NIGHT_SKY -> moon(middle, unit * 18f, Color(0xFFFFE9A8))
            Decor.COASTAL -> shell(middle, unit * 18f)
            Decor.NONE -> Unit
        }
        val offsets = listOf(-66f to -8f, -44f to 12f, 44f to -12f, 66f to 8f)
        offsets.forEachIndexed { index, (dx, dy) ->
            drawMotif(decor, index, Offset(middle.x + dx * unit, middle.y + dy * unit), unit * if (index % 2 == 0) 9f else 7f)
        }
    }
}

private fun DrawScope.drawMotif(
    decor: Decor,
    index: Int,
    at: Offset,
    size: Float,
) {
    when (decor) {
        Decor.SAKURA ->
            if (index % 3 == 2) petal(at, size, index * 47f, Color(0xFFFFD1DC)) else blossom(at, size, index * 23f)

        Decor.NARUTO ->
            when (index % 3) {
                0 -> swirl(at, size, Color(0xFFFF8A1F))
                1 -> leaf(at, size, index * 40f, Color(0xFF7BC96F))
                else -> throwingStar(at, size, Color(0xFFB8C4D6))
            }

        Decor.SASUKE ->
            when (index % 3) {
                0 -> bolt(at, size, Color(0xFF9FB4FF))
                1 -> sparkle(at, size * 0.8f, Color(0xFFB388FF))
                else -> throwingStar(at, size, Color(0xFFB8C4D6))
            }

        Decor.SHIKAMARU ->
            when (index % 3) {
                0 -> cloud(at, size, Color(0xE6F4F8FB))
                1 -> leaf(at, size * 0.8f, index * 40f, Color(0xFF8FBF6A))
                else -> shogiPiece(at, size * 0.9f)
            }

        Decor.SUNSET ->
            if (index % 2 == 0) cloud(at, size, Color(0xCCFFD9C2)) else sparkle(at, size * 0.7f, Color(0xFFFFE08A))

        Decor.NIGHT_SKY ->
            if (index == 1) moon(at, size, Color(0xFFFFE9A8)) else sparkle(at, size * (0.5f + (index % 3) * 0.2f), Color.White)

        Decor.COASTAL ->
            when (index % 3) {
                0 -> wave(at, size, Color(0xFFBFF3EC))
                1 -> shell(at, size * 0.8f)
                else -> sparkle(at, size * 0.6f, Color(0xFFF6D9A8))
            }

        Decor.NONE -> Unit
    }
}

private fun DrawScope.petal(
    at: Offset,
    size: Float,
    degrees: Float,
    color: Color,
) {
    rotate(degrees, at) {
        drawOval(color, topLeft = Offset(at.x - size * 0.28f, at.y - size * 0.6f), size = Size(size * 0.56f, size * 1.2f))
    }
}

private fun DrawScope.blossom(
    at: Offset,
    size: Float,
    degrees: Float,
) {
    repeat(5) { i ->
        rotate(degrees + i * 72f, at) {
            drawOval(
                Color(0xFFFFB7C5),
                topLeft = Offset(at.x - size * 0.3f, at.y - size),
                size = Size(size * 0.6f, size),
            )
        }
    }
    drawCircle(Color(0xFFFFE08A), radius = size * 0.2f, center = at)
}

private fun DrawScope.leaf(
    at: Offset,
    size: Float,
    degrees: Float,
    color: Color,
) {
    rotate(degrees, at) {
        val path =
            Path().apply {
                moveTo(at.x, at.y - size)
                quadraticTo(at.x + size * 0.8f, at.y, at.x, at.y + size)
                quadraticTo(at.x - size * 0.8f, at.y, at.x, at.y - size)
                close()
            }
        drawPath(path, color)
        drawLine(Color(0x66000000), Offset(at.x, at.y - size * 0.7f), Offset(at.x, at.y + size * 0.7f), size * 0.1f)
    }
}

private fun DrawScope.swirl(
    at: Offset,
    size: Float,
    color: Color,
) {
    val path = Path()
    var step = 0
    while (step <= 60) {
        val angle = step / 60f * 4.5f * PI.toFloat()
        val r = size * step / 60f
        val x = at.x + r * cos(angle)
        val y = at.y + r * sin(angle)
        if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        step++
    }
    drawPath(path, color, style = Stroke(width = size * 0.22f, cap = StrokeCap.Round))
}

private fun DrawScope.throwingStar(
    at: Offset,
    size: Float,
    color: Color,
) {
    val path = Path()
    repeat(8) { i ->
        val angle = i * PI.toFloat() / 4f
        val r = if (i % 2 == 0) size else size * 0.32f
        val x = at.x + r * cos(angle)
        val y = at.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
    drawCircle(Ink, radius = size * 0.16f, center = at)
}

private fun DrawScope.sparkle(
    at: Offset,
    size: Float,
    color: Color,
) {
    val path =
        Path().apply {
            moveTo(at.x, at.y - size)
            quadraticTo(at.x, at.y, at.x + size, at.y)
            quadraticTo(at.x, at.y, at.x, at.y + size)
            quadraticTo(at.x, at.y, at.x - size, at.y)
            quadraticTo(at.x, at.y, at.x, at.y - size)
            close()
        }
    drawPath(path, color)
}

private fun DrawScope.bolt(
    at: Offset,
    size: Float,
    color: Color,
) {
    val path =
        Path().apply {
            moveTo(at.x + size * 0.25f, at.y - size)
            lineTo(at.x - size * 0.55f, at.y + size * 0.15f)
            lineTo(at.x - size * 0.05f, at.y + size * 0.15f)
            lineTo(at.x - size * 0.3f, at.y + size)
            lineTo(at.x + size * 0.55f, at.y - size * 0.2f)
            lineTo(at.x + size * 0.05f, at.y - size * 0.2f)
            close()
        }
    drawPath(path, color)
}

private fun DrawScope.moon(
    at: Offset,
    size: Float,
    color: Color,
) {
    val full = Path().apply { addOval(Rect(at, size)) }
    val bite = Path().apply { addOval(Rect(Offset(at.x + size * 0.45f, at.y - size * 0.25f), size * 0.9f)) }
    drawPath(Path.combine(PathOperation.Difference, full, bite), color)
}

private fun DrawScope.sun(
    at: Offset,
    size: Float,
) {
    repeat(10) { i ->
        rotate(i * 36f, at) {
            drawLine(
                Color(0xFFFFD56B),
                Offset(at.x, at.y - size * 1.05f),
                Offset(at.x, at.y - size * 1.3f),
                size * 0.1f,
                StrokeCap.Round,
            )
        }
    }
    drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFFF8A5B)), at.y - size, at.y + size), size, at)
}

private fun DrawScope.cloud(
    at: Offset,
    size: Float,
    color: Color,
) {
    drawCircle(color, size * 0.55f, Offset(at.x - size * 0.6f, at.y + size * 0.1f))
    drawCircle(color, size * 0.75f, Offset(at.x, at.y - size * 0.15f))
    drawCircle(color, size * 0.55f, Offset(at.x + size * 0.65f, at.y + size * 0.1f))
}

private fun DrawScope.wave(
    at: Offset,
    size: Float,
    color: Color,
) {
    val path =
        Path().apply {
            moveTo(at.x - size, at.y)
            quadraticTo(at.x - size * 0.5f, at.y - size * 0.8f, at.x, at.y)
            quadraticTo(at.x + size * 0.5f, at.y + size * 0.8f, at.x + size, at.y)
        }
    drawPath(path, color, style = Stroke(width = size * 0.22f, cap = StrokeCap.Round))
}

private fun DrawScope.shell(
    at: Offset,
    size: Float,
) {
    val base = Offset(at.x, at.y + size * 0.7f)
    val fan =
        Path().apply {
            moveTo(base.x, base.y)
            lineTo(at.x - size, at.y - size * 0.1f)
            quadraticTo(at.x, at.y - size * 1.5f, at.x + size, at.y - size * 0.1f)
            close()
        }
    drawPath(fan, Color(0xFFF6D9A8))
    for (i in -2..2) {
        drawLine(
            Color(0x99C79A62),
            base,
            Offset(at.x + i * size * 0.4f, at.y - size * (0.75f - 0.12f * i * i)),
            size * 0.06f,
        )
    }
}

private fun DrawScope.shogiPiece(
    at: Offset,
    size: Float,
) {
    val tile =
        Path().apply {
            moveTo(at.x, at.y - size)
            lineTo(at.x + size * 0.7f, at.y - size * 0.45f)
            lineTo(at.x + size * 0.85f, at.y + size)
            lineTo(at.x - size * 0.85f, at.y + size)
            lineTo(at.x - size * 0.7f, at.y - size * 0.45f)
            close()
        }
    drawPath(tile, Color(0xFFE9CF9A))
    drawLine(Color(0xFF6B4A22), Offset(at.x, at.y - size * 0.35f), Offset(at.x, at.y + size * 0.55f), size * 0.16f, StrokeCap.Round)
    drawLine(Color(0xFF6B4A22), Offset(at.x - size * 0.35f, at.y), Offset(at.x + size * 0.35f, at.y), size * 0.16f, StrokeCap.Round)
}

/**
 * The lazy one, flat on his back on the grass watching the clouds: an arm for a pillow, one knee
 * up, eyes shut, hair tied in a spiky tuft. Drawn across [center] rather than standing on it.
 */
private fun DrawScope.nappingChibi(
    center: Offset,
    height: Float,
) {
    val u = height / 56f
    val hair = Color(0xFF2B2320)
    val vest = Color(0xFF6F8F52)
    val cloth = Color(0xFF3A3F45)
    val head = Offset(center.x - 13f * u, center.y + 3f * u)
    val headRadius = 11f * u

    cloud(Offset(center.x + 13f * u, center.y - 11f * u), 6f * u, Color(0xE6F4F8FB))

    drawOval(Color(0xFF3F6B34), Offset(center.x - 36f * u, center.y + 11f * u), Size(72f * u, 16f * u))

    // The arm under his head, then legs and torso, so the head overlaps all three.
    drawLine(Skin, Offset(center.x, center.y + 9f * u), Offset(center.x - 25f * u, center.y + 11f * u), 4.5f * u, StrokeCap.Round)
    drawLine(cloth, Offset(center.x + 12f * u, center.y + 12f * u), Offset(center.x + 30f * u, center.y + 13f * u), 5.5f * u, StrokeCap.Round)
    val bentLeg =
        Path().apply {
            moveTo(center.x + 13f * u, center.y + 10f * u)
            lineTo(center.x + 21f * u, center.y - 1f * u)
            lineTo(center.x + 27f * u, center.y + 12f * u)
        }
    drawPath(bentLeg, cloth, style = Stroke(width = 5.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(Skin, 2.6f * u, Offset(center.x + 31f * u, center.y + 12.5f * u))
    drawCircle(Skin, 2.6f * u, Offset(center.x + 28f * u, center.y + 13f * u))
    drawRoundRect(vest, Offset(center.x - 6f * u, center.y + 4f * u), Size(22f * u, 11f * u), CornerRadius(5f * u))
    drawRect(cloth, Offset(center.x + 4f * u, center.y + 4f * u), Size(2f * u, 11f * u))

    rotate(-24f, head) {
        repeat(5) { i ->
            val angle = (-118f + i * 14f) * PI.toFloat() / 180f
            val tip = Offset(head.x + headRadius * 1.75f * cos(angle), head.y + headRadius * 1.75f * sin(angle))
            val left = Offset(head.x + headRadius * cos(angle - 0.2f), head.y + headRadius * sin(angle - 0.2f))
            val right = Offset(head.x + headRadius * cos(angle + 0.2f), head.y + headRadius * sin(angle + 0.2f))
            drawPath(
                Path().apply {
                    moveTo(left.x, left.y)
                    lineTo(tip.x, tip.y)
                    lineTo(right.x, right.y)
                    close()
                },
                hair,
            )
        }
        drawCircle(hair, headRadius * 1.05f, Offset(head.x, head.y - 1.2f * u))
        drawCircle(Skin, headRadius, head)
        // Hair pulled back off the forehead.
        drawArc(hair, 180f, 180f, true, Offset(head.x - headRadius, head.y - headRadius), Size(headRadius * 2f, headRadius * 1.1f))
        for (side in listOf(-1f, 1f)) {
            drawArc(
                Ink,
                startAngle = 10f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(head.x + side * 4.5f * u - 2f * u, head.y + 0.5f * u),
                size = Size(4f * u, 2.6f * u),
                style = Stroke(width = 0.8f * u, cap = StrokeCap.Round),
            )
            drawCircle(Blush, 1.9f * u, Offset(head.x + side * 7.5f * u, head.y + 5.5f * u))
        }
        drawLine(Ink, Offset(head.x - 1.6f * u, head.y + 6.5f * u), Offset(head.x + 1.6f * u, head.y + 6.2f * u), 0.7f * u, StrokeCap.Round)
    }
}

private enum class ChibiLook(
    val hair: Color,
    val outfit: Color,
    val trim: Color,
    val eyes: Color,
) {
    Blossom(hair = Color(0xFFFF9EB8), outfit = Color(0xFFD9486E), trim = Color(0xFFFFF1F4), eyes = Color(0xFF3FA37A)),
    Fox(hair = Color(0xFFFFD23F), outfit = Color(0xFFFF8A1F), trim = Color(0xFF26355E), eyes = Color(0xFF2E7BE0)),
    Storm(hair = Color(0xFF1D2140), outfit = Color(0xFF34407A), trim = Color(0xFFE8EAF6), eyes = Color(0xFF14162B)),
}

/** A big-headed little figure: head roughly half its height, standing on [center]'s baseline. */
private fun DrawScope.chibi(
    center: Offset,
    height: Float,
    look: ChibiLook,
) {
    val u = height / 56f
    val head = Offset(center.x, center.y - 7f * u)
    val headRadius = 15f * u

    // Body first, so the head overlaps the collar.
    drawRoundRect(
        look.outfit,
        topLeft = Offset(center.x - 9f * u, center.y + 7f * u),
        size = Size(18f * u, 15f * u),
        cornerRadius = CornerRadius(6f * u),
    )
    drawRoundRect(look.outfit, Offset(center.x - 14f * u, center.y + 9f * u), Size(6f * u, 10f * u), CornerRadius(3f * u))
    drawRoundRect(look.outfit, Offset(center.x + 8f * u, center.y + 9f * u), Size(6f * u, 10f * u), CornerRadius(3f * u))
    drawCircle(Skin, 2.6f * u, Offset(center.x - 11f * u, center.y + 19.5f * u))
    drawCircle(Skin, 2.6f * u, Offset(center.x + 11f * u, center.y + 19.5f * u))
    drawRoundRect(look.trim, Offset(center.x - 7f * u, center.y + 20f * u), Size(6f * u, 6f * u), CornerRadius(2f * u))
    drawRoundRect(look.trim, Offset(center.x + 1f * u, center.y + 20f * u), Size(6f * u, 6f * u), CornerRadius(2f * u))
    drawRect(look.trim, Offset(center.x - 1f * u, center.y + 8f * u), Size(2f * u, 13f * u))

    // Hair behind the face.
    when (look) {
        ChibiLook.Blossom -> {
            drawCircle(look.hair, headRadius * 1.12f, Offset(head.x, head.y - 1f * u))
            drawRoundRect(look.hair, Offset(head.x - 17f * u, head.y), Size(7f * u, 20f * u), CornerRadius(3.5f * u))
            drawRoundRect(look.hair, Offset(head.x + 10f * u, head.y), Size(7f * u, 20f * u), CornerRadius(3.5f * u))
        }

        ChibiLook.Fox, ChibiLook.Storm -> {
            val spikes = if (look == ChibiLook.Fox) 9 else 7
            repeat(spikes) { i ->
                val degrees = -170f + i * (160f / (spikes - 1))
                val angle = degrees * PI.toFloat() / 180f
                val tip = Offset(head.x + headRadius * 1.6f * cos(angle), head.y + headRadius * 1.6f * sin(angle))
                val left = Offset(head.x + headRadius * cos(angle - 0.32f), head.y + headRadius * sin(angle - 0.32f))
                val right = Offset(head.x + headRadius * cos(angle + 0.32f), head.y + headRadius * sin(angle + 0.32f))
                drawPath(
                    Path().apply {
                        moveTo(left.x, left.y)
                        lineTo(tip.x, tip.y)
                        lineTo(right.x, right.y)
                        close()
                    },
                    look.hair,
                )
            }
            drawCircle(look.hair, headRadius * 1.04f, Offset(head.x, head.y - 1.5f * u))
        }
    }

    drawCircle(Skin, headRadius, head)

    // Fringe.
    when (look) {
        ChibiLook.Blossom -> {
            drawArc(look.hair, 180f, 180f, true, Offset(head.x - headRadius, head.y - headRadius), Size(headRadius * 2f, headRadius * 1.5f))
            blossom(Offset(head.x + 10f * u, head.y - 10f * u), 4.5f * u, 0f)
        }

        ChibiLook.Fox -> {
            // A plain cloth band with a blank plate.
            drawRoundRect(look.trim, Offset(head.x - headRadius, head.y - 10f * u), Size(headRadius * 2f, 5.5f * u), CornerRadius(2f * u))
            drawRoundRect(Color(0xFFC9D1DE), Offset(head.x - 6f * u, head.y - 9.5f * u), Size(12f * u, 4.5f * u), CornerRadius(1.5f * u))
        }

        ChibiLook.Storm -> {
            val fringe =
                Path().apply {
                    moveTo(head.x - headRadius, head.y - 4f * u)
                    lineTo(head.x - 8f * u, head.y + 4f * u)
                    lineTo(head.x - 3f * u, head.y - 6f * u)
                    lineTo(head.x + 3f * u, head.y - 6f * u)
                    lineTo(head.x + 8f * u, head.y + 4f * u)
                    lineTo(head.x + headRadius, head.y - 4f * u)
                    lineTo(head.x + headRadius * 0.8f, head.y - headRadius)
                    lineTo(head.x - headRadius * 0.8f, head.y - headRadius)
                    close()
                }
            drawPath(fringe, look.hair)
        }
    }

    // Face.
    for (side in listOf(-1f, 1f)) {
        val eye = Offset(head.x + side * 5.5f * u, head.y + 2.5f * u)
        drawOval(look.eyes, Offset(eye.x - 2f * u, eye.y - 2.6f * u), Size(4f * u, 5.2f * u))
        drawCircle(Color.White, 0.9f * u, Offset(eye.x + 0.6f * u, eye.y - 1.1f * u))
        drawCircle(Blush, 2.4f * u, Offset(head.x + side * 9.5f * u, head.y + 7f * u))
        if (look == ChibiLook.Fox) {
            for (line in -1..1) {
                val y = head.y + 7f * u + line * 1.6f * u
                drawLine(Ink, Offset(head.x + side * 8f * u, y), Offset(head.x + side * 12.5f * u, y + line * 0.6f * u), 0.5f * u)
            }
        }
    }
    drawArc(
        Ink,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(head.x - 2.5f * u, head.y + 6f * u),
        size = Size(5f * u, 3.5f * u),
        style = Stroke(width = 0.7f * u, cap = StrokeCap.Round),
    )
}
