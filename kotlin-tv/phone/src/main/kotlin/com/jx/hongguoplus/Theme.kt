// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

object PlusColors {
    val Bg = Color(0xFF0B0B0D)
    val Surface = Color(0xFF16161A)
    val SurfaceHigh = Color(0xFF1F1F25)
    val Accent = Color(0xFFFF3B4E)
    val Gold = Color(0xFFFFB648)
    val Text = Color(0xFFF5F5F7)
    val SubText = Color(0xFF9A9AA2)
    val Divider = Color(0xFF26262E)
}

@Composable
fun PlusTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = PlusColors.Accent,
        onPrimary = Color.White,
        background = PlusColors.Bg,
        surface = PlusColors.Surface,
        onSurface = PlusColors.Text,
        surfaceVariant = PlusColors.SurfaceHigh,
        onSurfaceVariant = PlusColors.SubText
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
