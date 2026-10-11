@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.clouddesk.storymaker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.effect.BitmapOverlay
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings

/**
 * Draws the current Story line over each video frame.
 * The bitmap is full-frame (1080x1920, transparent) so positioning is just Canvas drawing,
 * and one bitmap per line is cached so it is uploaded to the GPU only when the line changes.
 */
class StoryOverlay(
    private val lines: List<StoryLine>,
    private val template: TextTemplate,
) : BitmapOverlay() {

    private val cache = HashMap<Int, Bitmap>()
    private val blank: Bitmap by lazy { Bitmap.createBitmap(StorySpec.WIDTH, StorySpec.HEIGHT, Bitmap.Config.ARGB_8888) }

    private fun activeIndex(timeMs: Long) = lines.indexOfFirst { timeMs >= it.startMs && timeMs < it.endMs }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val i = activeIndex(presentationTimeUs / 1000)
        if (i < 0) return blank
        return cache.getOrPut(i) {
            Bitmap.createBitmap(StorySpec.WIDTH, StorySpec.HEIGHT, Bitmap.Config.ARGB_8888).also {
                drawStoryText(Canvas(it), lines[i].text, template)
            }
        }
    }

    /** 300 ms fade in and out for each line. */
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val t = presentationTimeUs / 1000
        val line = lines.getOrNull(activeIndex(t))
        val alpha = if (line == null) 0f else {
            val fade = 300f
            minOf(1f, (t - line.startMs) / fade, (line.endMs - t) / fade).coerceIn(0f, 1f)
        }
        return StaticOverlaySettings.Builder().setAlphaScale(alpha).build()
    }

    override fun release() {
        super.release()
        cache.values.forEach { it.recycle() }
        cache.clear()
    }
}

/** Draws one line of Story text onto a 1080x1920 canvas, inside Instagram's safe zone. */
fun drawStoryText(canvas: Canvas, text: String, template: TextTemplate) {
    val sideMargin = 110
    val maxWidth = StorySpec.WIDTH - sideMargin * 2

    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = when (template) {
            TextTemplate.BOLD_CENTER -> 78f
            TextTemplate.LOWER_CAPTION -> 60f
            TextTemplate.MINIMAL_TOP -> 66f
            TextTemplate.MEME -> 76f
        }
        if (template == TextTemplate.MINIMAL_TOP) setShadowLayer(14f, 0f, 4f, Color.argb(200, 0, 0, 0))
    }

    // Shrink long lines so they fit in 5 rows, but never below a size that's readable on a phone.
    var layout = buildLayout(text, paint, maxWidth)
    while (layout.lineCount > 5 && paint.textSize > 50f) {
        paint.textSize -= 4f
        layout = buildLayout(text, paint, maxWidth)
    }

    val textHeight = layout.height
    val top = when (template) {
        TextTemplate.BOLD_CENTER -> (StorySpec.HEIGHT - textHeight) / 2f
        TextTemplate.LOWER_CAPTION -> StorySpec.SAFE_BOTTOM - 60f - textHeight
        TextTemplate.MINIMAL_TOP -> StorySpec.SAFE_TOP + 60f
        TextTemplate.MEME -> StorySpec.HEIGHT * 0.40f - textHeight / 2f
    }

    val pad = 34f
    var widest = 0f
    for (l in 0 until layout.lineCount) widest = maxOf(widest, layout.getLineWidth(l))
    val box = RectF(
        (StorySpec.WIDTH - widest) / 2f - pad, top - pad * 0.7f,
        (StorySpec.WIDTH + widest) / 2f + pad, top + textHeight + pad * 0.7f,
    )
    when (template) {
        TextTemplate.BOLD_CENTER -> canvas.drawRoundRect(box, 28f, 28f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) })
        TextTemplate.LOWER_CAPTION -> canvas.drawRoundRect(box, 18f, 18f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 90, 70) })
        // Soft dark band so white text stays readable on light footage (paper, sky, walls).
        TextTemplate.MINIMAL_TOP -> {
            val y0 = top - 140f
            val y1 = top + textHeight + 160f
            val band = Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, y0, 0f, y1,
                    intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(150, 0, 0, 0), Color.argb(150, 0, 0, 0), Color.argb(0, 0, 0, 0)),
                    floatArrayOf(0f, 0.3f, 0.7f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, y0, StorySpec.WIDTH.toFloat(), y1, band)
        }
        TextTemplate.MEME -> Unit   // the outline below does the job
    }

    canvas.save()
    canvas.translate(sideMargin.toFloat(), top)
    if (template == TextTemplate.MEME) {
        // Thick black outline first, then the white text on top — the classic meme / Gen Z caption look.
        val outline = TextPaint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = paint.textSize * 0.16f
            strokeJoin = Paint.Join.ROUND
            color = Color.BLACK
        }
        buildLayout(text, outline, maxWidth).draw(canvas)
    }
    layout.draw(canvas)
    canvas.restore()
}

private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(0f, 1.1f)
        .build()
