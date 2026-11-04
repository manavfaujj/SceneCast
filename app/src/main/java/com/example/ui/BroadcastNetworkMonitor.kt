package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.monitoring.DeviceDiagnosticsState
import com.example.monitoring.StreamHealthGrade
import com.example.streaming.StreamConnectionStatus
import com.example.streaming.StreamEngineTelemetry
import com.example.ui.theme.StudioAccentSky
import com.example.ui.theme.StudioAccentViolet
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardBg
import com.example.ui.theme.StudioElevatedBg
import com.example.ui.theme.StudioLiveRed
import com.example.ui.theme.StudioSuccessGreen
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioWarningAmber

/**
 * Real-time Broadcast Network Monitor Component for SceneCast Live.
 * Displays Bitrate (kbps/Mbps), Frame Rate (FPS), and Packet Loss Percentage during a broadcast
 * to help broadcasters identify network bottlenecks, buffer drops, and stability issues.
 */
@Composable
fun BroadcastNetworkMonitorCard(
    telemetry: StreamEngineTelemetry,
    diagnostics: DeviceDiagnosticsState,
    modifier: Modifier = Modifier
) {
    val isLive = telemetry.isStreaming && telemetry.status == StreamConnectionStatus.LIVE

    // Evaluate health state for color coding
    val bitrateTarget = telemetry.targetBitrateKbps.coerceAtLeast(1000)
    val bitrateRatio = if (isLive) (telemetry.currentUploadKbps.toFloat() / bitrateTarget).coerceIn(0f, 1.5f) else 1f
    val bitrateColor by animateColorAsState(
        targetValue = when {
            !isLive -> StudioTextSecondary
            bitrateRatio >= 0.85f -> StudioSuccessGreen
            bitrateRatio >= 0.60f -> StudioWarningAmber
            else -> StudioLiveRed
        },
        label = "bitrateColor"
    )

    val fpsRatio = if (isLive && telemetry.targetFps > 0) {
        (telemetry.encodedFps.toFloat() / telemetry.targetFps).coerceIn(0f, 1.2f)
    } else 1f
    val fpsColor by animateColorAsState(
        targetValue = when {
            !isLive -> StudioTextSecondary
            fpsRatio >= 0.90f -> StudioSuccessGreen
            fpsRatio >= 0.70f -> StudioWarningAmber
            else -> StudioLiveRed
        },
        label = "fpsColor"
    )

    val lossPct = telemetry.packetLossPercent
    val packetLossColor by animateColorAsState(
        targetValue = when {
            !isLive -> StudioTextSecondary
            lossPct <= 1.0f -> StudioSuccessGreen
            lossPct <= 3.5f -> StudioWarningAmber
            else -> StudioLiveRed
        },
        label = "lossColor"
    )

    val stabilityStatusText = when {
        !isLive -> "STANDBY"
        lossPct <= 1.0f && bitrateRatio >= 0.85f -> "STABLE"
        lossPct <= 3.5f || bitrateRatio >= 0.60f -> "CONGESTED"
        else -> "PACKET LOSS"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulseLive")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    StudioCard(
        backgroundColor = StudioCardBg,
        borderColor = if (isLive && (lossPct > 3.5f || bitrateRatio < 0.60f)) StudioLiveRed else StudioBorder,
        modifier = modifier
            .fillMaxWidth()
            .testTag("broadcast_network_monitor_card")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header: Section Title + Live / Standby Status Badge + Duration
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLive) StudioLiveRed.copy(alpha = pulseAlpha) else StudioTextMuted
                            )
                    )
                    Text(
                        text = if (isLive) "LIVE NETWORK MONITOR" else "NETWORK BROADCAST MONITOR",
                        color = StudioTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        maxLines = 1
                    )
                }

                // Stability Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(packetLossColor.copy(alpha = 0.15f))
                        .border(1.dp, packetLossColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = if (lossPct <= 1.0f && isLive) Icons.Default.CheckCircle else if (isLive) Icons.Default.Warning else Icons.Default.NetworkCheck,
                        contentDescription = null,
                        tint = packetLossColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = stabilityStatusText,
                        color = packetLossColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }

            // Primary 3 Metrics Grid: Bitrate | Frame Rate | Packet Loss
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Bitrate Tile
                NetworkMetricTile(
                    title = "BITRATE",
                    value = if (isLive) formatBitrate(telemetry.currentUploadKbps) else "${telemetry.targetBitrateKbps}k",
                    unit = if (telemetry.currentUploadKbps >= 1000) "Mbps" else "kbps",
                    subtitle = "Target: ${telemetry.targetBitrateKbps}k",
                    progress = if (isLive) (bitrateRatio / 1.1f).coerceIn(0f, 1f) else 1f,
                    accentColor = bitrateColor,
                    icon = Icons.Default.Speed,
                    modifier = Modifier.weight(1f),
                    testTag = "monitor_metric_bitrate"
                )

                // 2. Frame Rate Tile
                NetworkMetricTile(
                    title = "FRAME RATE",
                    value = if (isLive) "${telemetry.encodedFps}" else "${telemetry.targetFps}",
                    unit = "FPS",
                    subtitle = "Target: ${telemetry.targetFps} fps",
                    progress = if (isLive) fpsRatio.coerceIn(0f, 1f) else 1f,
                    accentColor = fpsColor,
                    icon = Icons.Default.Videocam,
                    modifier = Modifier.weight(1f),
                    testTag = "monitor_metric_fps"
                )

                // 3. Packet Loss Tile
                NetworkMetricTile(
                    title = "PACKET LOSS",
                    value = if (isLive) String.format("%.1f%%", lossPct) else "0.0%",
                    unit = "Loss",
                    subtitle = "${telemetry.droppedFrames} dropped",
                    progress = if (isLive) (lossPct / 10f).coerceIn(0f, 1f) else 0f,
                    accentColor = packetLossColor,
                    icon = Icons.Default.SignalCellularAlt,
                    modifier = Modifier.weight(1f),
                    testTag = "monitor_metric_packet_loss"
                )
            }

            // Secondary Telemetry Bar: Uplink Bandwidth + Network Interface + Session Duration
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(StudioElevatedBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (diagnostics.networkTypeLabel.contains("Wi-Fi")) Icons.Default.Wifi else Icons.Default.SignalCellularAlt,
                        contentDescription = null,
                        tint = StudioAccentSky,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${diagnostics.networkTypeLabel} • Uplink ~${diagnostics.estimatedUplinkKbps / 1000} Mbps",
                        color = StudioTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = StudioTextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formatDurationSec(telemetry.streamDurationSec),
                        color = StudioTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }

            // Warning Notice if network packet loss or congestion is detected
            AnimatedVisibility(
                visible = isLive && (lossPct > 2.5f || bitrateRatio < 0.65f),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(StudioLiveRed.copy(alpha = 0.12f))
                        .border(1.dp, StudioLiveRed.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = StudioLiveRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (lossPct > 3.5f) {
                            "Network congestion: ${String.format("%.1f%%", lossPct)} packet loss detected. Switch to 720p or lower bitrate."
                        } else {
                            "Upload bitrate dipping below target. Check your Wi-Fi/cellular connection."
                        },
                        color = StudioLiveRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Metric Tile Component for Bitrate, FPS, and Packet Loss
 */
@Composable
private fun NetworkMetricTile(
    title: String,
    value: String,
    unit: String,
    subtitle: String,
    progress: Float,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(StudioElevatedBg)
            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(testTag)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    color = StudioTextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    maxLines = 1
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(12.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = value,
                    color = StudioTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    text = unit,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 2.dp),
                    maxLines = 1
                )
            }

            // Progress Bar / Health Gauge
            LinearProgressIndicator(
                progress = { progress },
                color = accentColor,
                trackColor = StudioBorder.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
            )

            Text(
                text = subtitle,
                color = StudioTextSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact HUD pill showing real-time Bitrate, FPS, and Packet Loss for overlays
 */
@Composable
fun BroadcastCompactNetworkPill(
    telemetry: StreamEngineTelemetry,
    modifier: Modifier = Modifier
) {
    val isLive = telemetry.isStreaming && telemetry.status == StreamConnectionStatus.LIVE
    val lossPct = telemetry.packetLossPercent
    val statusColor = when {
        !isLive -> StudioTextMuted
        lossPct <= 1.0f -> StudioSuccessGreen
        lossPct <= 3.5f -> StudioWarningAmber
        else -> StudioLiveRed
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(StudioElevatedBg.copy(alpha = 0.92f))
            .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("broadcast_compact_network_pill")
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(statusColor)
        )

        // Bitrate
        Text(
            text = if (isLive) "${formatBitrate(telemetry.currentUploadKbps)} Mbps" else "${telemetry.targetBitrateKbps}k",
            color = StudioTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )

        Text(text = "•", color = StudioTextMuted, fontSize = 10.sp)

        // FPS
        Text(
            text = "${if (isLive) telemetry.encodedFps else telemetry.targetFps} FPS",
            color = StudioTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )

        Text(text = "•", color = StudioTextMuted, fontSize = 10.sp)

        // Loss
        Text(
            text = if (isLive) String.format("%.1f%% Loss", lossPct) else "0.0% Loss",
            color = statusColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

private fun formatBitrate(kbps: Int): String {
    return if (kbps >= 1000) {
        String.format("%.1f", kbps / 1000f)
    } else {
        "$kbps"
    }
}
