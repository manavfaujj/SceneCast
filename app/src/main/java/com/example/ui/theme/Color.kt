package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// SceneCast Live Professional Dark Mobile Studio Palette (Section 2)
val StudioMainBg = Color(0xFF0B0D12)        // Main background: #0B0D12
val StudioSecondaryBg = Color(0xFF12151D)   // Secondary background: #12151D
val StudioCardBg = Color(0xFF191E29)        // Card background: #191E29
val StudioElevatedBg = Color(0xFF202635)    // Elevated surface: #202635
val StudioBorder = Color(0xFF2A3040)        // Border: #2A3040

val StudioTextPrimary = Color(0xFFF5F7FA)   // Primary text: #F5F7FA
val StudioTextSecondary = Color(0xFF929BAC) // Secondary text: #929BAC
val StudioTextMuted = Color(0xFF647087)     // Muted metadata text

val StudioAccentViolet = Color(0xFF8B5CF6)  // Primary Accent: #8B5CF6
val StudioAccentSky = Color(0xFF38BDF8)     // Secondary Accent: #38BDF8
val StudioLiveRed = Color(0xFFEF4444)       // LIVE indicator: #EF4444
val StudioSuccessGreen = Color(0xFF22C55E)  // Success: #22C55E
val StudioWarningAmber = Color(0xFFF59E0B)  // Warning: #F59E0B

// Backward-compatible semantic aliases used across compositor & UI modules
val StudioCharcoal = StudioMainBg
val StudioSurface = StudioSecondaryBg
val StudioSurfaceElevated = StudioCardBg
val StudioSurfaceHigh = StudioElevatedBg
val StudioOutline = StudioBorder

val BroadcastCyan = StudioAccentSky
val BroadcastCyanDim = Color(0xFF0284C7)
val LiveCrimson = StudioLiveRed
val LiveCrimsonDark = Color(0xFF991B1B)
val SignalEmerald = StudioSuccessGreen
val SignalAmber = StudioWarningAmber
val RoyalViolet = StudioAccentViolet

val TextPrimary = StudioTextPrimary
val TextSecondary = StudioTextSecondary
val TextMuted = StudioTextMuted
