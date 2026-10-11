package io.github.jqssun.airplay.ui

import io.github.jqssun.airplay.ui.theme.AirRelayColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.service.VideoPlaybackInfo
import io.github.jqssun.airplay.viewmodel.DebugInfo
import java.util.Locale

/** Non-interactive: metrics must never steal the remote's focus or control playback. */
@Composable
fun StreamMetrics(input: DebugInfo, remote: Boolean, media: VideoPlaybackInfo, modifier: Modifier = Modifier) {
    val rows = if (remote) {
        listOfNotNull(
            stringResource(if (media.live) R.string.metrics_live else R.string.metrics_video),
            if (media.videoWidth > 0 && media.videoHeight > 0) "${media.videoWidth} × ${media.videoHeight}" else null,
            media.videoCodec.takeIf { it.isNotBlank() },
            if (media.sourceFrameRate > 0f) stringResource(R.string.metrics_source_fps,
                String.format(Locale.ROOT, "%.1f", media.sourceFrameRate)) else null,
            if (media.nominalBitrate > 0) stringResource(R.string.metrics_track_bitrate,
                String.format(Locale.ROOT, "%.1f", media.nominalBitrate / 1_000_000.0)) else null,
            stringResource(R.string.metrics_buffer, String.format(Locale.ROOT, "%.1f", media.bufferedMs / 1000.0)),
            media.audioCodec.takeIf { it.isNotBlank() },
        )
    } else {
        listOfNotNull(stringResource(R.string.metrics_mirror), input.videoRes.takeIf { it.isNotBlank() },
            input.videoCodec.takeIf { it.isNotBlank() },
            stringResource(R.string.metrics_input_fps, input.videoFps),
            stringResource(R.string.metrics_input_bitrate, input.bitrateStr),
            stringResource(R.string.metrics_dropped, input.droppedFrames),
            input.audioCodec.takeIf { it.isNotBlank() })
    }
    Column(modifier.widthIn(max = 330.dp).background(AirRelayColors.Panel.copy(alpha = .86f), RoundedCornerShape(10.dp))
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEach { Text(it, color = AirRelayColors.Text, style = MaterialTheme.typography.bodySmall) }
    }
}
