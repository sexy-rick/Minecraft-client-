package dev.empyreal.core.hud

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class HudView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val dp = density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 13f * dp
        setShadowLayer(3f * dp, 0f, 1f * dp, 0xCC000000.toInt())
    }
    private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x7A0E1310
    }
    private val pillFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6141A17.toInt() }
    private val pillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x998CD6A9.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }
    private val panelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2141A17.toInt() }
    private val panelStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4D8CD6A9
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
    }
    private val rowFillOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x268CD6A9 }
    private val rowFillOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x12FFFFFF }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8CD6A9.toInt() }
    private val accentOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E1310.toInt() }
    private val thumbOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
    private val rowText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * dp
    }
    private val chipText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 11.5f * dp
        isFakeBoldText = true
    }
    private val titleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15f * dp
        isFakeBoldText = true
    }
    private val hintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3FFFFFF.toInt()
        textSize = 11f * dp
    }
    private val closeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 14f * dp
        isFakeBoldText = true
    }
    private val crosshair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt()
        strokeWidth = 2f * dp
        style = Paint.Style.STROKE
    }

    private val pill = RectF()
    private val panel = RectF()
    private val closeRect = RectF()
    private val listViewport = RectF()
    private val labelBadge = RectF()
    private val rows = mutableListOf<Pair<RectF, HudModuleWire>>()
    private val actionChips = mutableListOf<Pair<RectF, String>>()

    private var scrollY = 0f
    private var maxScrollY = 0f

    private var pillTouchActive = false
    private var pillDragging = false
    private var downX = 0f
    private var downY = 0f
    private var pillStartX = 0f
    private var pillStartY = 0f

    private var panelScrolling = false
    private var lastTouchY = 0f

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        HudState.layout.forEach { (id, widget) ->
            if (!widget.visible) return@forEach
            val cx = widget.x * w
            val cy = widget.y * h
            if (id == "crosshair") {
                drawCrosshair(canvas, cx, cy, widget.scale)
            } else {
                val value = HudState.texts[id] ?: return@forEach
                if (value.isNotBlank()) drawLabel(canvas, value, cx, cy, widget.scale, w, h)
            }
        }

        if (!HudState.menuOpen) {
            drawPill(canvas, w, h)
            return
        }
        drawPanel(canvas, w, h)
    }

    private fun drawLabel(
        canvas: Canvas,
        value: String,
        cx: Float,
        cy: Float,
        scale: Float,
        w: Float,
        h: Float,
    ) {
        label.textSize = 13f * dp * scale.coerceIn(0.5f, 3f)
        val textWidth = label.measureText(value)
        val textHeight = label.descent() - label.ascent()
        val padX = 7f * dp * scale.coerceIn(0.7f, 1.6f)
        val padY = 3.5f * dp * scale.coerceIn(0.7f, 1.6f)
        val halfW = textWidth / 2f + padX
        val halfH = textHeight / 2f + padY
        val margin = 4f * dp

        val safeCx = if (halfW * 2f >= w - margin * 2f) {
            w / 2f
        } else {
            cx.coerceIn(margin + halfW, w - margin - halfW)
        }
        val safeCy = if (halfH * 2f >= h - margin * 2f) {
            h / 2f
        } else {
            cy.coerceIn(margin + halfH, h - margin - halfH)
        }

        labelBadge.set(safeCx - halfW, safeCy - halfH, safeCx + halfW, safeCy + halfH)
        canvas.drawRoundRect(labelBadge, 7f * dp, 7f * dp, labelBg)
        val baseline = safeCy - (label.descent() + label.ascent()) / 2f
        canvas.drawText(value, safeCx, baseline, label)
    }

    private fun drawCrosshair(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        val s = scale.coerceIn(0.4f, 4f)
        crosshair.strokeWidth = max(1.5f * dp, 1.8f * dp * min(s, 1.6f))
        val gap = 3f * dp * s
        val arm = 7f * dp * s
        canvas.drawLine(cx - gap - arm, cy, cx - gap, cy, crosshair)
        canvas.drawLine(cx + gap, cy, cx + gap + arm, cy, crosshair)
        canvas.drawLine(cx, cy - gap - arm, cx, cy - gap, crosshair)
        canvas.drawLine(cx, cy + gap, cx, cy + gap + arm, crosshair)
    }

    private fun drawPill(canvas: Canvas, w: Float, h: Float) {
        val size = 42f * dp
        val margin = 8f * dp + size / 2f
        val cx = (HudState.menuX * w).coerceIn(margin, (w - margin).coerceAtLeast(margin))
        val cy = (HudState.menuY * h).coerceIn(margin, (h - margin).coerceAtLeast(margin))
        pill.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        canvas.drawRoundRect(pill, 14f * dp, 14f * dp, pillFill)
        canvas.drawRoundRect(pill, 14f * dp, 14f * dp, pillStroke)
        label.textSize = 16f * dp
        val y = cy - (label.descent() + label.ascent()) / 2f
        canvas.drawText("E", cx, y, label)
    }

    private fun drawPanel(canvas: Canvas, w: Float, h: Float) {
        val panelWidth = min(310f * dp, w - 24f * dp)
        val rowHeight = 42f * dp
        val header = 52f * dp
        val speedrunOn = HudState.isEnabled("speedrun")
        val footerHeight = if (speedrunOn) 44f * dp else 0f
        val contentHeight = HudState.modules.size * rowHeight
        val desiredHeight = header + contentHeight + footerHeight + 12f * dp
        val panelHeight = min(desiredHeight, h - 20f * dp)

        val pillCx = HudState.menuX * w
        val pillCy = HudState.menuY * h
        var left = pillCx - panelWidth - 12f * dp
        if (left < 12f * dp) left = pillCx + 12f * dp
        if (left + panelWidth > w - 12f * dp) left = (w - panelWidth - 12f * dp).coerceAtLeast(12f * dp)
        var top = pillCy - panelHeight / 2f
        top = top.coerceIn(10f * dp, (h - panelHeight - 10f * dp).coerceAtLeast(10f * dp))

        panel.set(left, top, left + panelWidth, top + panelHeight)
        canvas.drawRoundRect(panel, 18f * dp, 18f * dp, panelFill)
        canvas.drawRoundRect(panel, 18f * dp, 18f * dp, panelStroke)

        titleText.textAlign = Paint.Align.LEFT
        hintText.textAlign = Paint.Align.LEFT
        val activeCount = HudState.modules.count { it.enabled }
        canvas.drawText("Empyreal", left + 16f * dp, top + 24f * dp, titleText)
        canvas.drawText(
            "$activeCount active · tap to toggle, drag to scroll",
            left + 16f * dp,
            top + 41f * dp,
            hintText,
        )

        closeRect.set(panel.right - 40f * dp, top + 8f * dp, panel.right - 8f * dp, top + 40f * dp)
        canvas.drawRoundRect(closeRect, 10f * dp, 10f * dp, rowFillOff)
        val closeBaseline = closeRect.centerY() - (closeText.descent() + closeText.ascent()) / 2f
        canvas.drawText("✕", closeRect.centerX(), closeBaseline, closeText)

        val listBottom = panel.bottom - footerHeight - 8f * dp
        listViewport.set(left + 6f * dp, top + header, panel.right - 6f * dp, listBottom)

        val viewportHeight = (listViewport.height()).coerceAtLeast(1f)
        maxScrollY = max(0f, contentHeight - viewportHeight)
        scrollY = scrollY.coerceIn(0f, maxScrollY)

        rows.clear()
        val checkpoint = canvas.save()
        canvas.clipRect(listViewport)
        var y = listViewport.top - scrollY
        HudState.modules.forEach { module ->
            val rowTop = y
            val rowBottom = y + rowHeight - 5f * dp
            if (rowBottom >= listViewport.top && rowTop <= listViewport.bottom) {
                val row = RectF(left + 10f * dp, rowTop, panel.right - 10f * dp, rowBottom)
                val hitRect = RectF(
                    row.left,
                    max(row.top, listViewport.top),
                    row.right,
                    min(row.bottom, listViewport.bottom),
                )
                if (hitRect.height() > 4f * dp) {
                    rows += hitRect to module
                }
                canvas.drawRoundRect(row, 11f * dp, 11f * dp, if (module.enabled) rowFillOn else rowFillOff)
                rowText.textAlign = Paint.Align.LEFT
                val textY = row.centerY() - (rowText.descent() + rowText.ascent()) / 2f
                canvas.drawText(module.name, row.left + 12f * dp, textY, rowText)

                val track = RectF(
                    row.right - 44f * dp,
                    row.centerY() - 10f * dp,
                    row.right - 10f * dp,
                    row.centerY() + 10f * dp,
                )
                canvas.drawRoundRect(track, 10f * dp, 10f * dp, if (module.enabled) accent else accentOff)
                val thumbCx = if (module.enabled) track.right - 10f * dp else track.left + 10f * dp
                canvas.drawCircle(
                    thumbCx,
                    track.centerY(),
                    7f * dp,
                    if (module.enabled) thumbPaint else thumbOffPaint,
                )
            }
            y += rowHeight
        }
        canvas.restoreToCount(checkpoint)

        actionChips.clear()
        if (speedrunOn) {
            val barTop = listBottom + 4f * dp
            val barBottom = panel.bottom - 8f * dp
            val gap = 6f * dp
            val usableW = panel.width() - 20f * dp - gap * 2f
            val chipW = usableW / 3f
            val labels = listOf(
                (if (HudState.speedrunRunning) "Pause" else "Start") to "toggle",
                "Split" to "split",
                "Reset" to "reset",
            )
            labels.forEachIndexed { idx, (text, act) ->
                val cl = left + 10f * dp + idx * (chipW + gap)
                val rect = RectF(cl, barTop, cl + chipW, barBottom)
                actionChips += rect to act
                val paint = if (idx == 0 && !HudState.speedrunRunning) accent else rowFillOn
                canvas.drawRoundRect(rect, 10f * dp, 10f * dp, paint)
                chipText.color = if (idx == 0 && !HudState.speedrunRunning) 0xFF0E1310.toInt() else Color.WHITE
                val cy = rect.centerY() - (chipText.descent() + chipText.ascent()) / 2f
                canvas.drawText(text, rect.centerX(), cy, chipText)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return false

        if (!HudState.menuOpen) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (pill.contains(event.x, event.y)) {
                        pillTouchActive = true
                        pillDragging = false
                        downX = event.x
                        downY = event.y
                        pillStartX = HudState.menuX * w
                        pillStartY = HudState.menuY * h
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!pillTouchActive) return false
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (!pillDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        pillDragging = true
                    }
                    if (pillDragging) {
                        HudState.moveMenu((pillStartX + dx) / w, (pillStartY + dy) / h, persist = false)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!pillTouchActive) return false
                    pillTouchActive = false
                    if (pillDragging) {
                        pillDragging = false
                        HudState.moveMenu(HudState.menuX, HudState.menuY, persist = true)
                    } else {
                        HudState.menuOpen = true
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (!pillTouchActive) return false
                    pillTouchActive = false
                    pillDragging = false
                    invalidate()
                    return true
                }
            }
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastTouchY = event.y
                panelScrolling = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val totalDy = event.y - downY
                if (!panelScrolling && abs(totalDy) > touchSlop && maxScrollY > 0f && listViewport.contains(downX, downY)) {
                    panelScrolling = true
                }
                if (panelScrolling) {
                    val deltaY = lastTouchY - event.y
                    scrollY = (scrollY + deltaY).coerceIn(0f, maxScrollY)
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (panelScrolling) {
                    panelScrolling = false
                    return true
                }
                if (closeRect.contains(event.x, event.y) || !panel.contains(event.x, event.y)) {
                    HudState.menuOpen = false
                    invalidate()
                    return true
                }
                val actionHit = actionChips.firstOrNull { it.first.contains(event.x, event.y) }
                if (actionHit != null) {
                    HudState.speedrunAction(actionHit.second)
                    invalidate()
                    return true
                }
                val hit = rows.firstOrNull { it.first.contains(event.x, event.y) }
                if (hit != null) {
                    HudState.toggle(hit.second.id)
                    invalidate()
                    return true
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                panelScrolling = false
                HudState.menuOpen = false
                invalidate()
                return true
            }
        }
        return true
    }
}
