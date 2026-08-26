package com.aus.gemini01.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Single source of truth for spatial design tokens. Screens reference these
 * instead of inventing per-file dp values, so the spacing rhythm stays
 * consistent across feed, reader, chat and settings.
 *
 * Scale: 4dp base grid (4/8/12/16/20/24/32/48).
 */
object Dimens {

    // Spacing scale
    val spaceXXS = 2.dp
    val spaceXS = 4.dp
    val spaceS = 8.dp
    val spaceM = 12.dp
    val spaceL = 16.dp
    val spaceXL = 20.dp
    val spaceXXL = 24.dp
    val spaceXXXL = 32.dp
    val spaceHuge = 48.dp

    // Screen-edge gutters
    val gutterCompact = 12.dp   // dense lists (category chips row)
    val gutterDefault = 16.dp   // standard screen padding
    val gutterReading = 20.dp   // reader / long-form content

    // Corner radii — one rhythm for cards, chips, dialogs, inputs
    val radiusXS = 6.dp
    val radiusS = 8.dp
    val radiusM = 12.dp
    val radiusL = 16.dp
    val radiusXL = 20.dp
    val radiusXXL = 28.dp        // pills, search field, chat composer
    val radiusFull = 999.dp

    // Touch targets (Material minimum 48dp; icons may render smaller)
    val touchTarget = 48.dp
    val iconSizeXS = 14.dp
    val iconSizeS = 18.dp
    val iconSizeM = 22.dp
    val iconSizeL = 28.dp
    val iconSizeXL = 36.dp

    // Content
    val articleImageAspectRatio = 16f / 9f   // responsive image area
    val readingMeasureMax = 640.dp           // max comfortable line length for reader view

    // Component specifics
    val cardVerticalSpacing = 14.dp
    val chipHeight = 38.dp
    val dividerInset = 12.dp
    val searchBarHeight = 52.dp
    val headerIconSize = 24.dp
}
