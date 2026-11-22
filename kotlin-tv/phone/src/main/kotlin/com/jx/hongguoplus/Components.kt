// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hongguotv.core.Series

@Composable
fun PosterCard(
    series: Series,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(modifier = modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .clip(RoundedCornerShape(10.dp))
                .background(PlusColors.SurfaceHigh)
        ) {
            AsyncImage(
                model = series.cover,
                contentDescription = series.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 底部渐变，让角标文字在浅色封面上也清楚
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x99000000.toInt()))
                        )
                    )
            )
            val badge = subtitle ?: series.badge
            if (!badge.isNullOrBlank()) {
                Text(
                    text = badge,
                    color = Color(0xFFFFD24D),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 4.dp, end = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = series.title,
            color = PlusColors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            lineHeight = 17.sp,
            overflow = TextOverflow.Ellipsis
        )
        if (!series.tags.isBlank()) {
            Text(
                text = series.tags,
                color = PlusColors.SubText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun LoadingView(text: String = "加载中…", modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = PlusColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(text, color = PlusColors.SubText, fontSize = 12.sp)
        }
    }
}

@Composable
fun ErrorView(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = PlusColors.SubText,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            if (onRetry != null) {
                Spacer(Modifier.height(12.dp))
                Pill(text = "重试", selected = true, onClick = onRetry)
            }
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) PlusColors.Accent else PlusColors.SurfaceHigh)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else PlusColors.SubText,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun ChipRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
    ) {
        items(options.size) { index ->
            Pill(text = options[index], selected = index == selected) { onSelect(index) }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = PlusColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, color = PlusColors.SubText, fontSize = 12.sp)
        }
    }
}
