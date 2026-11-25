package ir.pinbom.app.ui.components.fx

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ir.pinbom.app.ui.theme.AuroraViolet

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · aurora
 *  A static, two-lobed glow painted once behind a screen: brand red leaking in
 *  from the top corner, a violet breath on the opposite side. Drawn in a single
 *  drawBehind (no layers, no recomposition), so it costs nothing while the
 *  feed scrolls over it — but it is what makes the whole app stop looking flat.
 * ──────────────────────────────────────────────────────────────────────────── */
fun Modifier.auroraGlow(primary: Color, dark: Boolean): Modifier = drawBehind {
    val strength = if (dark) 0.24f else 0.13f
    val reach = size.width * 1.05f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(primary.copy(alpha = strength), Color.Transparent),
            center = Offset(size.width * 0.92f, -size.height * 0.03f),
            radius = reach,
        ),
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(AuroraViolet.copy(alpha = strength * 0.7f), Color.Transparent),
            center = Offset(size.width * 0.04f, size.height * 0.26f),
            radius = reach * 0.8f,
        ),
    )
}
