package com.pathways.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.provider.Settings
import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import com.pathways.model.Outcome
import com.pathways.model.RollResult
import kotlin.math.hypot
import kotlin.math.sin

/** The four flourishes a verdict can play. */
internal enum class Tier {
    CRITICAL_SUCCESS, SUCCESS, FAILURE, CRITICAL_FAILURE;

    companion object {
        /**
         * Read off what the card SHOWS, never the dice alone: a critical-only
         * divination demotes a success, and "Held the focus" is a verdict the
         * dice did not print. A critical plays only where both agree.
         */
        fun of(passed: Boolean, roll: RollResult): Tier = when {
            passed && roll.outcome == Outcome.CRITICAL_SUCCESS -> CRITICAL_SUCCESS
            passed -> SUCCESS
            roll.outcome == Outcome.CRITICAL_FAILURE -> CRITICAL_FAILURE
            else -> FAILURE
        }
    }
}

/** Which roll a verdict belongs to: each kind gets its own sound and motion. */
internal enum class RollKind { DIVINATION }

/**
 * The sound for each kind and tier. All four are from Kenney's packs
 * (kenney.nl), each licensed CC0 by the pack's own License.txt, checked
 * 2026-10-17: divination_critical_success is Impact Sounds'
 * impactBell_heavy_001, _success Interface Sounds' glass_004, _failure its
 * glitch_002, _critical_failure its error_007.
 */
@RawRes
internal fun soundFor(kind: RollKind, tier: Tier): Int = when (kind) {
    RollKind.DIVINATION -> when (tier) {
        Tier.CRITICAL_SUCCESS -> R.raw.divination_critical_success
        Tier.SUCCESS -> R.raw.divination_success
        Tier.FAILURE -> R.raw.divination_failure
        Tier.CRITICAL_FAILURE -> R.raw.divination_critical_failure
    }
}

/**
 * Every verdict sound, loaded once at start: SoundPool loads asynchronously,
 * so a pool made when a dialog opens would be silent on its first roll.
 */
internal class RollSounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = RollKind.entries.flatMap { kind ->
        Tier.entries.map { tier -> (kind to tier) to pool.load(context, soundFor(kind, tier), 1) }
    }.toMap()

    fun play(kind: RollKind, tier: Tier) {
        ids[kind to tier]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun release() = pool.release()
}

/** Null when sounds are off in the theme picker, or outside [App]. */
internal val LocalRollSounds = staticCompositionLocalOf<RollSounds?> { null }

/**
 * The verdict's flourish: a Canvas behind the card's text, sized to the card
 * and clipped by it, so it adds no height (6ia) and no control moves. A Canvas
 * takes no touches. Plays once per card shown, and again only if a push changes the
 * tier. The system's "remove animations" turns the motion off; the theme
 * picker's switch turns the sound off.
 */
@Composable
internal fun BoxScope.RollFlourish(kind: RollKind, tier: Tier, modifier: Modifier = Modifier) {
    val sounds = LocalRollSounds.current
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val progress = remember(tier) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(kind, tier) {
        sounds?.play(kind, tier)
        if (animate) progress.animateTo(1f, tween(DURATION_MS, easing = FastOutSlowInEasing))
    }
    val color = when (tier) {
        Tier.CRITICAL_SUCCESS -> MaterialTheme.colorScheme.tertiary
        Tier.SUCCESS -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Canvas(modifier.matchParentSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        drawRipple(tier, t, color)
    }
}

/**
 * Divination's motion: a scrying ripple. Rings spread out of the card on a
 * success, three of them on a critical; on a failure they close in on the
 * verdict, and a critical failure's rings shiver as they close.
 */
private fun DrawScope.drawRipple(tier: Tier, t: Float, color: Color) {
    val outward = tier == Tier.CRITICAL_SUCCESS || tier == Tier.SUCCESS
    val rings = when (tier) {
        Tier.CRITICAL_SUCCESS -> 3
        Tier.CRITICAL_FAILURE -> 2
        else -> 1
    }
    val reach = hypot(size.width, size.height) / 2
    val shiver = if (tier == Tier.CRITICAL_FAILURE) sin(t * 60f) * 6f * (1 - t) else 0f
    repeat(rings) { i ->
        // Staggered: each ring starts a little after the one before it.
        val local = ((t - i * STAGGER) / (1 - STAGGER * (rings - 1))).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) return@repeat
        val r = reach * (if (outward) local else 1 - local)
        drawCircle(
            color = color.copy(alpha = (1 - local) * 0.8f),
            radius = r.coerceAtLeast(1f),
            center = Offset(center.x + shiver, center.y),
            style = Stroke(width = 4f + 6f * (1 - local)),
        )
    }
}

private const val DURATION_MS = 900
private const val STAGGER = 0.18f
