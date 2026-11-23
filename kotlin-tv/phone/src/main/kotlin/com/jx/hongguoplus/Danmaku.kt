// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hongguotv.core.Danmaku

/**
 * 轻量弹幕层：按播放进度把官方弹幕从右往左飘过屏幕。
 * 不做轨道排布算法，按 id 取模分 6 条轨道，够用且不吃性能。
 */
@Composable
fun DanmakuOverlay(items: List<Danmaku>, positionMs: Long, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = constraints.maxWidth
        val visible = items.filter { d ->
            val delta = positionMs - d.offsetMs
            delta in 0..8000
        }
        Layout(content = {
            visible.forEach { d ->
                Text(
                    d.text,
                    color = (d.color?.let { Color(it) } ?: Color.White).copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)) }
            layout(width, constraints.maxHeight) {
                visible.forEachIndexed { i, d ->
                    val p = ((positionMs - d.offsetMs).toFloat() / 8000f).coerceIn(0f, 1f)
                    var x = (width - (width + placeables[i].width) * p).toInt()
                    if (x > width) x = width
                    val lane = i % 6
                    val y = with(density) { (26 + lane * 30).dp.roundToPx() }
                    placeables[i].placeRelative(x, y)
                }
            }
        }
    }
}
