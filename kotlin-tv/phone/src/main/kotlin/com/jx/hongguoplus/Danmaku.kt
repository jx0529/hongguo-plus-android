// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hongguotv.core.Danmaku

/** 弹幕显示偏好（行数 / 透明度 / 稀疏度）。 */
data class DanmakuStyle(
    val rows: Int = 3,
    val alpha: Float = 0.9f,
    val density: Float = 1f
) {
    companion object {
        private const val FILE = "hongguoplus_danmaku"

        fun load(context: Context): DanmakuStyle {
            val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return DanmakuStyle(
                rows = sp.getInt("rows", 3).coerceIn(1, 6),
                alpha = sp.getFloat("alpha", 0.9f).coerceIn(0.2f, 1f),
                density = sp.getFloat("density", 1f).coerceIn(0.4f, 1.6f)
            )
        }

        fun save(context: Context, style: DanmakuStyle) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putInt("rows", style.rows)
                .putFloat("alpha", style.alpha)
                .putFloat("density", style.density)
                .apply()
        }
    }
}

private data class Scheduled(
    val layout: TextLayoutResult,
    val startMs: Long,
    val durationMs: Long,
    val lane: Int,
    val color: Color
)

/**
 * 高性能弹幕层：
 * 1. 全部弹幕用一块 Canvas 直接 drawText，不再每帧创建 Text 组件、不再每帧重新布局；
 * 2. 轨道与出发时间在进入时一次性排好，同一瞬间不会铺满全屏，晚到的排队依次滑出；
 * 3. 每帧只做平移，且只触发重绘、不触发重组。
 */
@Composable
fun DanmakuOverlay(
    items: List<Danmaku>,
    positionMs: Long,
    playing: Boolean,
    style: DanmakuStyle,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val textStyle = remember {
        TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
    val density = LocalDensity.current

    // 单调累加的帧时钟：只被 draw 读取，因此每帧仅重绘
    val frame = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0) frame.value += (now - last) / 1_000_000f
                last = now
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val laneHeightPx = with(density) { 30.dp.toPx() }
        val speed = widthPx * 0.22f

        val scheduled = remember(items, widthPx, style.rows, style.density, style.alpha) {
            schedule(items, measurer, textStyle, style, widthPx, speed, laneHeightPx)
        }

        val anchorPos = remember { FloatArray(1) }
        val anchorFrame = remember { FloatArray(1) }
        val anchored = remember { booleanArrayOf(false) }

        Canvas(Modifier.fillMaxSize()) {
            if (!playing) {
                anchored[0] = false
                anchorPos[0] = positionMs.toFloat()
                anchorFrame[0] = frame.value
            } else {
                val projected = anchorPos[0] + (frame.value - anchorFrame[0])
                if (!anchored[0] || kotlin.math.abs(projected - positionMs) > 400f) {
                    anchorPos[0] = positionMs.toFloat()
                    anchorFrame[0] = frame.value
                    anchored[0] = true
                }
            }
            val now = anchorPos[0] + (frame.value - anchorFrame[0])
            val topOffset = laneHeightPx * 0.5f
            for (s in scheduled) {
                val p = (now - s.startMs) / s.durationMs
                if (p < 0f || p > 1f) continue
                val w = s.layout.size.width.toFloat()
                val x = widthPx - (widthPx + w) * p
                if (x + w < 0f || x > widthPx) continue
                drawText(
                    textLayoutResult = s.layout,
                    topLeft = Offset(x, topOffset + s.lane * laneHeightPx),
                    color = s.color
                )
            }
        }
    }
}

/**
 * 轨道排布：按弹幕时间排序，逐条挑一个「最后一条的尾巴已进屏」的轨道；
 * 没有可用轨道就延后出发（排队），所以不会一股脑全冒出来。
 */
private fun schedule(
    items: List<Danmaku>,
    measurer: TextMeasurer,
    textStyle: TextStyle,
    style: DanmakuStyle,
    widthPx: Float,
    speed: Float,
    laneHeightPx: Float
): List<Scheduled> {
    if (widthPx <= 0f || items.isEmpty() || speed <= 1f) return emptyList()

    val rows = style.rows.coerceIn(1, 6)
    val densityFactor = style.density.coerceIn(0.4f, 1.6f)
    val baseGapMs = when {
        densityFactor <= 0.6f -> 1400L
        densityFactor <= 1.1f -> 700L
        else -> 260L
    }
    val gapPx = widthPx * 0.08f * densityFactor

    val laneFree = LongArray(rows) { Long.MIN_VALUE / 2 }
    val out = ArrayList<Scheduled>(items.size)

    items.sortedBy { it.offsetMs }.forEach { d ->
        val text = d.text.trim()
        if (text.isEmpty()) return@forEach
        val layout = measurer.measure(
            text = text,
            style = textStyle,
            maxLines = 1,
            softWrap = false
        )
        val w = layout.size.width.toFloat()
        val duration = (((widthPx + w) / speed) * 1000f).toLong().coerceAtLeast(2800L)

        var freeLane = -1
        for (lane in 0 until rows) {
            if (laneFree[lane] <= d.offsetMs) { freeLane = lane; break }
        }
        val start: Long
        val lane: Int
        if (freeLane >= 0) {
            start = d.offsetMs
            lane = freeLane
        } else {
            var earliestLane = 0
            for (l in 1 until rows) if (laneFree[l] < laneFree[earliestLane]) earliestLane = l
            lane = earliestLane
            start = laneFree[lane] + baseGapMs
        }

        val tailInMs = (((w + gapPx) / speed) * 1000f).toLong()
        laneFree[lane] = start + tailInMs

        val color = (d.color?.let { Color(it) } ?: Color.White)
            .copy(alpha = style.alpha.coerceIn(0.2f, 1f))
        out.add(Scheduled(layout, start, duration, lane, color))
    }
    return out
}
