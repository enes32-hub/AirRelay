package io.github.jqssun.airplay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.jqssun.airplay.ui.theme.LocalReceiverPalette

/** Static decorative vector scene: no bitmap allocation, network asset, blur or perpetual animation. */
@Composable
fun ReceiverScene(modifier: Modifier = Modifier) {
    val colors = LocalReceiverPalette.current
    Canvas(modifier.clipToBounds()) {
        val factor = minOf(size.width / 440f, size.height / 200f)
        withTransform({
            translate((size.width - 440f * factor) / 2f, (size.height - 200f * factor) / 2f)
            scale(factor, factor, Offset.Zero)
        }) {
            drawRoundRect(colors.Border, Offset(104f, 12f), Size(287f, 161f), CornerRadius(16f))
            drawRoundRect(colors.Background, Offset(106f, 14f), Size(283f, 157f), CornerRadius(14f))
            drawRoundRect(colors.Elevated, Offset(113f, 21f), Size(269f, 142f), CornerRadius(10f))
            drawCircle(colors.Accent.copy(alpha = .22f), 49f, Offset(286f, 88f))
            drawCircle(colors.Muted.copy(alpha = .17f), 34f, Offset(322f, 111f))
            drawRoundRect(colors.Accent.copy(alpha = .72f), Offset(190f, 78f), Size(38f, 27f), CornerRadius(6f), style = Stroke(3f))
            drawRoundRect(colors.Text.copy(alpha = .9f), Offset(204f, 88f), Size(38f, 27f), CornerRadius(6f), style = Stroke(3f))
            drawRoundRect(colors.Border, Offset(221f, 174f), Size(51f, 4f), CornerRadius(2f))
            drawRoundRect(colors.Elevated, Offset(205f, 181f), Size(82f, 4f), CornerRadius(2f))
            drawRoundRect(colors.Border, Offset(54f, 55f), Size(81f, 139f), CornerRadius(19f))
            drawRoundRect(colors.Background, Offset(57f, 58f), Size(75f, 133f), CornerRadius(16f))
            drawRoundRect(colors.AccentSoft, Offset(63f, 69f), Size(63f, 111f), CornerRadius(10f))
            drawRoundRect(colors.Background, Offset(82f, 64f), Size(26f, 6f), CornerRadius(3f))
            drawCircle(colors.Accent.copy(alpha = .25f), 24f, Offset(105f, 125f))
            drawRoundRect(colors.Text, Offset(78f, 109f), Size(23f, 17f), CornerRadius(4f), style = Stroke(2f))
            drawRoundRect(colors.Accent, Offset(87f, 116f), Size(23f, 17f), CornerRadius(4f), style = Stroke(2f))
            drawRoundRect(colors.Text.copy(alpha = .65f), Offset(84f, 183f), Size(23f, 3f), CornerRadius(2f))
        }
    }
}
