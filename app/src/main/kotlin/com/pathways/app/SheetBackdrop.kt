package com.pathways.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * The page behind the sheet, with the walked pathway's sigil laid into its
 * top corner like an ink seal on paper: washed almost to grey, faint, and
 * fading downward into the page so the header text sits on it.
 *
 * The owner liked how the goal mockup blended art into the app
 * (2026-10-17); this is that blend with the 22 sigils the app already has.
 * Owner-supplied scene art can replace the sigil later.
 *
 * The caller's Scaffold and top bar must be transparent, or they paint over
 * the seal. A mortal has no pathway and gets the bare page.
 */
@Composable
internal fun SheetBackdrop(pathway: String?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val art = pathway?.let(::pathwayArt)
        if (art != null) {
            val wash = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.2f) }) }
            Image(
                painterResource(art),
                contentDescription = null,
                colorFilter = wash,
                alpha = 0.22f,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 48.dp, y = (-32).dp)
                    .size(260.dp)
                    // Offscreen, so DstIn fades the seal itself and not the
                    // page under it.
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            // Gone by the pool strip, which pins opaque over it.
                            Brush.verticalGradient(0.2f to Color.Black, 0.75f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }
        content()
    }
}
