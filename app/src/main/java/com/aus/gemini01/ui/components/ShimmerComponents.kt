package com.aus.gemini01.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aus.gemini01.ui.theme.Dimens

fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1300,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslation"
    )

    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerLow

    val shimmerColors = androidx.compose.runtime.remember(baseColor, highlightColor) {
        listOf(
            baseColor.copy(alpha = 0.6f),
            highlightColor.copy(alpha = 0.95f),
            baseColor.copy(alpha = 0.6f)
        )
    }

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )
    background(brush)
}

@Composable
fun ArticleCardShimmer() {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.gutterDefault, vertical = Dimens.spaceS),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column {
            // Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(Dimens.articleImageAspectRatio)
                    .shimmerEffect()
            )
            Column(modifier = Modifier.padding(Dimens.spaceL)) {
                // Editorial meta
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(14.dp)
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.width(Dimens.spaceS))
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .height(14.dp)
                            .shimmerEffect()
                    )
                }
                Spacer(modifier = Modifier.height(Dimens.spaceM))
                // Title lines
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(20.dp)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(Dimens.spaceS))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(20.dp)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(Dimens.spaceM))
                // Description
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(Dimens.spaceXS))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(14.dp)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(Dimens.spaceL))
                // Action row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(36.dp)
                            .shimmerEffect()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(32.dp)
                                .shimmerEffect()
                        )
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(32.dp)
                                .shimmerEffect()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerList() {
    LazyColumn(
        contentPadding = PaddingValues(vertical = Dimens.spaceS)
    ) {
        items(4) {
            ArticleCardShimmer()
        }
    }
}

@Composable
fun ReaderViewShimmer(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.gutterReading, vertical = Dimens.spaceXXXL),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = Dimens.readingMeasureMax),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = Dimens.spaceL)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.iconSizeM)
                )
                Spacer(modifier = Modifier.width(Dimens.spaceS))
                Text(
                    text = "Gemini is formatting reader mode...",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spaceM))
            // Main title shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(28.dp)
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(Dimens.spaceS))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(28.dp)
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(Dimens.spaceXL))

            // Body paragraphs shimmer
            repeat(4) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(Dimens.spaceS))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(16.dp)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(Dimens.spaceS))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(16.dp)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(Dimens.spaceXL))
            }
        }
    }
}

