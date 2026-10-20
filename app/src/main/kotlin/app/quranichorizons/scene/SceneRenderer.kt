package app.quranichorizons.scene

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.quranichorizons.data.ActorDef
import app.quranichorizons.data.SceneDef
import app.quranichorizons.data.SceneSpec
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private fun hex(c: String, alpha: Float = 1f): Color =
    Color(android.graphics.Color.parseColor(c)).copy(alpha = alpha.coerceIn(0f, 1f))

/** مشهد جاهز للرسم: تعريفه + حالة عناصره في لحظة معينة. */
data class SceneFrame(val scene: SceneDef, val state: SceneState)

private fun lerpF(a: Float, b: Float, u: Float) = a + (b - a) * u

// ============================ البيئة (سماء/نجوم/أفق) ============================

fun DrawScope.drawEnvironment(spec: SceneSpec, alpha: Float, t: Float) {
    val w = size.width
    val h = size.height
    drawRect(brush = Brush.verticalGradient(listOf(hex(spec.skyTop, alpha), hex(spec.skyBottom, alpha))))

    val rnd = Random(42)
    for (i in 0 until spec.stars) {
        val x = rnd.nextFloat() * w
        val y = rnd.nextFloat() * h * 0.5f
        val r = 0.6f + rnd.nextFloat() * 1.3f
        val phase = rnd.nextFloat() * (2f * PI.toFloat())
        val speed = 0.4f + rnd.nextFloat() * 0.8f
        val base = 0.3f + rnd.nextFloat() * 0.5f
        val tw = 1f - spec.twinkle * 0.5f * (1f + sin(t * speed + phase))
        drawCircle(Color.White.copy(alpha = (base * tw * alpha).coerceIn(0f, 1f)), r, Offset(x, y))
    }

    spec.glow?.let { g ->
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(hex(g.color, g.alpha * alpha), Color.Transparent),
                center = Offset(g.x * w, g.y * h),
                radius = g.r * w * (1f + 0.03f * sin(t * 0.6f)),
            ),
        )
    }

    if (spec.mist > 0f) {
        for (band in 0 until 3) {
            val y0 = h * (0.40f + band * 0.07f)
            val dx = sin(t * spec.drift * 0.3f + band) * 0.04f * w
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = spec.mist * 0.08f * alpha), Color.Transparent),
                    startY = y0, endY = y0 + h * 0.12f,
                ),
                topLeft = Offset(dx, y0),
                size = Size(w, h * 0.12f),
            )
        }
    }

    spec.ridges.forEachIndexed { idx, ridge ->
        val base = h * (1f - ridge.h)
        val p = Path()
        p.moveTo(0f, h)
        val steps = 24
        for (s in 0..steps) {
            val wobble = sin(s * 0.9f + idx * 2f) * h * 0.006f + sin(s * 0.37f + idx) * h * 0.009f
            p.lineTo(w * s / steps, base + wobble)
        }
        p.lineTo(w, h)
        p.close()
        drawPath(p, hex(ridge.color, alpha))
    }
}

// ============================ العناصر الحيّة ============================

private class Stage(val w: Float, val top: Float, val h: Float) {
    fun px(nx: Float, ny: Float) = Offset(nx * w, top + ny * h)
}

private fun DrawScope.stage(): Stage = Stage(size.width, size.height * 0.10f, size.height * 0.44f)

private fun DrawScope.drawHuman(foot: Offset, height: Float, alpha: Float) {
    val bodyW = height * 0.30f
    val headR = height * 0.12f
    val bodyTop = foot.y - height * 0.74f
    val fill = Color(0xFF050B20).copy(alpha = alpha)
    val rim = Color(0xFFD9A84F).copy(alpha = 0.55f * alpha)
    drawRoundRect(fill, Offset(foot.x - bodyW / 2f, bodyTop), Size(bodyW, height * 0.74f), CornerRadius(bodyW * 0.45f))
    drawCircle(fill, headR, Offset(foot.x, bodyTop - headR * 0.9f))
    drawRoundRect(rim, Offset(foot.x - bodyW / 2f, bodyTop), Size(bodyW, height * 0.74f), CornerRadius(bodyW * 0.45f), style = Stroke(1.4f))
    drawCircle(rim, headR, Offset(foot.x, bodyTop - headR * 0.9f), style = Stroke(1.4f))
}

private fun DrawScope.drawCrowd(a: ActorDef, st: Map<String, Float>, master: Float) {
    val sg = stage()
    val alpha = (st["a"] ?: 0f) * master
    if (alpha <= 0.01f) return
    val gather = st["gather"] ?: 0f
    val rows = st["rows"] ?: 0f
    val rnd = Random(a.seed.toLong())
    val pts = ArrayList<Triple<Float, Float, Float>>()
    for (i in 0 until a.n) {
        val sx = 0.08f + rnd.nextFloat() * 0.84f
        val sy = 0.62f + rnd.nextFloat() * 0.36f
        val row = i / 7
        val col = i % 7
        val gx = 0.5f + (col - 3) * 0.047f + (row % 2) * 0.02f
        val gy = 0.97f - 0.075f * row
        val rx = 0.5f + (col - 3) * 0.125f
        val ry = 0.97f - 0.085f * row
        val x = lerpF(lerpF(sx, gx, gather), rx, rows)
        val y = lerpF(lerpF(sy, gy, gather), ry, rows)
        pts.add(Triple(x, y, 0f))
    }
    val sorted = pts.sortedBy { it.second }
    val bind = (st["bind"] ?: 0f).coerceIn(0f, 1f)
    if (bind > 0.02f) {
        // خيوط تنزل من نقطة واحدة خارج الإطار إلى كل إنسان (التدبير والسلطان)
        val apex = sg.px(0.5f, -0.35f)
        for (p in sorted) {
            val foot = sg.px(p.first, p.second)
            val head = Offset(foot.x, foot.y - sg.h * 0.34f * 0.56f * (0.7f + 0.3f * p.second) * 0.95f)
            val end = Offset(lerpF(apex.x, head.x, bind), lerpF(apex.y, head.y, bind))
            drawLine(Color(0xFFF3D98F).copy(alpha = 0.45f * alpha), apex, end, strokeWidth = 1.6f)
            if (bind > 0.95f) drawCircle(Color(0xFFFFF1C2).copy(alpha = 0.9f * alpha), sg.w * 0.007f, head)
        }
    }
    for (p in sorted) {
        val foot = sg.px(p.first, p.second)
        val hgt = sg.h * 0.34f * 0.56f * (0.7f + 0.3f * p.second)
        drawHuman(foot, hgt, alpha)
    }
}

private fun DrawScope.drawFigure(a: ActorDef, st: Map<String, Float>, master: Float, t: Float) {
    val sg = stage()
    val alpha = (st["a"] ?: 0f) * master
    if (alpha <= 0.01f) return
    val foot = sg.px(a.x, a.y)
    val hgt = sg.h * 0.34f * a.s
    drawHuman(foot, hgt, alpha)
    val chest = st["chest"] ?: 0f
    if (chest > 0.02f) {
        val cx = foot.x
        val cy = foot.y - hgt * 0.45f
        val spread = hgt * 0.11f
        for (i in 0 until 8) {
            val ph = i * 1.7f
            val x = cx + sin(t * 1.3f + ph) * spread
            val y = cy + cos(t * 1.1f + ph * 1.3f) * spread * 1.6f
            drawCircle(Color(0xFF8F6BE8).copy(alpha = (0.9f * chest * alpha).coerceIn(0f, 1f)), hgt * 0.04f, Offset(x, y))
        }
    }
}

private fun DrawScope.drawLantern(a: ActorDef, st: Map<String, Float>, master: Float, t: Float) {
    val sg = stage()
    val alpha = (st["a"] ?: 0f) * master
    if (alpha <= 0.01f) return
    val g = (st["g"] ?: 0f).coerceIn(0f, 1f)
    val c = sg.px(a.x, a.y)
    val flicker = 1f + 0.04f * sin(t * 5f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFE9A8).copy(alpha = (0.35f + 0.5f * g) * alpha), Color.Transparent),
            center = c, radius = sg.w * (0.06f + 0.26f * g) * flicker,
        ),
        radius = sg.w * (0.06f + 0.26f * g) * flicker, center = c,
    )
    drawCircle(Color(0xFFFFF3C8).copy(alpha = alpha), sg.w * 0.013f, c)
}

private fun DrawScope.drawDome(a: ActorDef, st: Map<String, Float>, master: Float) {
    val sg = stage()
    val alpha = (st["a"] ?: 0f) * master
    val draw = (st["draw"] ?: 0f).coerceIn(0f, 1f)
    if (alpha <= 0.01f || draw <= 0.01f) return
    val c = sg.px(a.x, a.y)
    val rx = sg.w * 0.42f * a.s
    val ry = sg.h * 0.88f * a.s
    val tl = Offset(c.x - rx, c.y - ry)
    val sz = Size(rx * 2f, ry * 2f)
    drawArc(Color(0xFFD9A84F).copy(alpha = 0.06f * alpha * draw), 180f, 180f * draw, true, tl, sz)
    drawArc(Color(0xFFE9CD8E).copy(alpha = 0.16f * alpha), 180f, 180f * draw, false, tl, sz, style = Stroke(16f))
    drawArc(Color(0xFFF3D98F).copy(alpha = 0.9f * alpha), 180f, 180f * draw, false, tl, sz, style = Stroke(3.2f))
}

private fun DrawScope.drawStarOrder(a: ActorDef, st: Map<String, Float>, master: Float, t: Float) {
    val sg = stage()
    val alpha = (st["a"] ?: 0f) * master
    if (alpha <= 0.01f) return
    val order = (st["order"] ?: 0f).coerceIn(0f, 1f)
    val rnd = Random(a.seed.toLong())
    val counts = intArrayOf(8, 14, 18)
    val radii = floatArrayOf(0.13f, 0.25f, 0.37f)
    val cx = sg.w * 0.5f
    val cy = sg.top + sg.h * 0.30f
    var idx = 0
    for (ring in 0..2) {
        for (j in 0 until counts[ring]) {
            val scx = sg.w * (0.05f + rnd.nextFloat() * 0.9f)
            val scy = sg.top + sg.h * (0.02f + rnd.nextFloat() * 0.5f)
            val ang = (2.0 * PI * j / counts[ring] + ring * 0.4).toFloat()
            val ox = cx + cos(ang) * radii[ring] * sg.w
            val oy = cy + sin(ang) * radii[ring] * sg.w * 0.62f
            val x = lerpF(scx, ox, order)
            val y = lerpF(scy, oy, order)
            val tw = 0.85f + 0.15f * sin(t * 1.2f + idx)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFF1C2).copy(alpha = 0.55f * alpha * tw), Color.Transparent),
                    center = Offset(x, y), radius = sg.w * 0.03f,
                ),
                radius = sg.w * 0.03f, center = Offset(x, y),
            )
            drawCircle(Color(0xFFFFF8DD).copy(alpha = alpha * tw), sg.w * 0.006f, Offset(x, y))
            idx++
        }
    }
    if (order > 0.02f) {
        for (ring in 0..2) {
            drawOval(
                Color(0xFFE9CD8E).copy(alpha = 0.22f * order * alpha),
                Offset(cx - radii[ring] * sg.w, cy - radii[ring] * sg.w * 0.62f),
                Size(radii[ring] * sg.w * 2f, radii[ring] * sg.w * 1.24f),
                style = Stroke(1.4f),
            )
        }
    }
}

private fun DrawScope.drawBeam(a: ActorDef, st: Map<String, Float>, master: Float) {
    val sg = stage()
    val alpha = (st["a"] ?: 0f) * master
    val hh = (st["h"] ?: 0f).coerceIn(0f, 1f)
    if (alpha <= 0.01f) return
    val base = sg.px(a.x, 0.74f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFE9A8).copy(alpha = 0.55f * alpha), Color.Transparent),
            center = base, radius = sg.w * 0.16f,
        ),
        radius = sg.w * 0.16f, center = base,
    )
    if (hh <= 0.01f) return
    val height = sg.h * 0.74f * hh
    val topY = base.y - height
    val bw = sg.w * 0.07f
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0x00FFE9A8), Color(0xFFFFE9A8).copy(alpha = 0.75f * alpha)),
            startY = topY, endY = base.y,
        ),
        topLeft = Offset(base.x - bw / 2f, topY), size = Size(bw, height),
    )
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0x00FFFFFF), Color(0xFFFFFFFF).copy(alpha = 0.85f * alpha)),
            startY = topY, endY = base.y,
        ),
        topLeft = Offset(base.x - bw * 0.15f, topY), size = Size(bw * 0.3f, height),
    )
}

private fun DrawScope.drawWisps(a: ActorDef, st: Map<String, Float>, master: Float, t: Float) {
    val sg = stage()
    val alpha = (st["a"] ?: 0f) * master
    val r = (st["r"] ?: 0f).coerceIn(0f, 1f)
    if (alpha <= 0.01f) return
    val s = sg.px(a.sx, a.sy)
    val e = sg.px(a.tx, a.ty)
    val dx = e.x - s.x
    val dy = e.y - s.y
    val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val nx = -dy / len
    val ny = dx / len
    val n = a.n.coerceAtLeast(1)
    for (i in 0 until n) {
        val f = r * (i + 1f) / n
        val wob = sin(t * 2f + i * 1.3f) * sg.w * 0.018f * (0.4f + r)
        val c = Offset(s.x + dx * f + nx * wob, s.y + dy * f + ny * wob)
        val rad = sg.w * (0.012f + 0.02f * sin(PI.toFloat() * (i + 1f) / (n + 1f))) * (0.45f + 0.55f * r)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF8A68E0).copy(alpha = 0.8f * alpha), Color.Transparent),
                center = c, radius = rad * 2.2f,
            ),
            radius = rad * 2.2f, center = c,
        )
    }
}

private fun DrawScope.drawSmoke(a: ActorDef, st: Map<String, Float>, master: Float, t: Float) {
    val sg = stage()
    val alpha = (st["a"] ?: 0f) * master
    if (alpha <= 0.01f) return
    val c0 = sg.px(a.x, a.y)
    for (i in 0 until 7) {
        val ox = sin(t * 0.5f + i * 1.9f) * sg.w * 0.05f
        val oy = cos(t * 0.4f + i * 1.3f) * sg.w * 0.06f
        val c = Offset(c0.x + ox, c0.y + oy)
        val rad = sg.w * (0.075f + 0.02f * sin(i + t * 0.3f))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF6B58B0).copy(alpha = 0.5f * alpha), Color.Transparent),
                center = c, radius = rad,
            ),
            radius = rad, center = c,
        )
    }
}

private fun DrawScope.drawShade(st: Map<String, Float>, master: Float, t: Float) {
    val sg = stage()
    val alpha = (st["a"] ?: 0f) * master
    val r = (st["r"] ?: 0f).coerceIn(0f, 1f)
    if (alpha <= 0.01f) return
    // ظلال داكنة تزحف من جانبي الإطار نحو المركز (الشرور)
    for (side in intArrayOf(-1, 1)) {
        for (k in 0 until 4) {
            val edge = if (side < 0) -0.12f else 1.12f
            val nx = lerpF(edge, 0.5f, r * 0.72f) + sin(t * 0.8f + k * 1.7f + side) * 0.012f
            val ny = 0.58f + k * 0.1f
            val c = sg.px(nx, ny)
            val rad = sg.w * (0.15f + 0.02f * sin(t + k))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF5A1F44).copy(alpha = 0.85f * alpha), Color.Transparent),
                    center = c, radius = rad,
                ),
                radius = rad, center = c,
            )
        }
    }
}

private fun DrawScope.drawDimLights(a: ActorDef, st: Map<String, Float>, master: Float) {
    val sg = stage()
    val alpha = (st["a"] ?: 0f) * master
    if (alpha <= 0.01f) return
    val n = a.n.coerceAtLeast(2)
    for (i in 0 until n) {
        val c = sg.px(0.08f + 0.84f * i / (n - 1), 0.64f + 0.06f * sin(i * 2.1f))
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFB4C2E8).copy(alpha = 0.5f * alpha), Color.Transparent), center = c, radius = sg.w * 0.04f),
            radius = sg.w * 0.04f, center = c,
        )
        drawCircle(Color(0xFFD8E0F5).copy(alpha = 0.7f * alpha), sg.w * 0.005f, c)
    }
}

private fun DrawScope.drawImageActor(a: ActorDef, st: Map<String, Float>, master: Float, images: Map<String, ImageBitmap>) {
    val alpha = (st["a"] ?: 0f) * master
    if (alpha <= 0.01f) return
    val bmp = images[a.id] ?: return
    val z = (st["z"] ?: 0f).coerceIn(0f, 1f)
    // تُعرض الصورة بعرض الشاشة في منطقة المشهد العليا (دون قصّ جانبيها)، وتكبر ببطء شديد (حركة تجميلية فقط)
    val w = size.width
    val h0 = w * bmp.height / bmp.width
    val k = 1f + 0.04f * z
    val dw = w * k
    val dh = h0 * k
    val top = size.height * 0.07f
    drawImage(
        image = bmp,
        dstOffset = IntOffset(((w - dw) / 2f).toInt(), (top - (dh - h0) / 2f).toInt()),
        dstSize = IntSize(dw.toInt(), dh.toInt()),
        alpha = alpha.coerceIn(0f, 1f),
        filterQuality = FilterQuality.High,
    )
}

/** يذيب الحافة السفلية للصورة في خلفية الشاشة فلا يظهر حدّ قاطع فوق النص. */
private fun DrawScope.drawImageFeather(scene: SceneDef, state: SceneState, images: Map<String, ImageBitmap>) {
    var bottom = 0f
    var any = false
    for (a in scene.actors) {
        if (a.kind != "image") continue
        val bmp = images[a.id] ?: continue
        if ((state[a.id]?.get("a") ?: 0f) > 0.01f) {
            any = true
            bottom = maxOf(bottom, size.height * 0.07f + size.width * bmp.height / bmp.width)
        }
    }
    if (!any) return
    val bg = hex(scene.env.skyBottom)
    drawRect(
        brush = Brush.verticalGradient(listOf(Color.Transparent, bg), startY = bottom - size.height * 0.10f, endY = bottom + 1f),
        topLeft = Offset(0f, bottom - size.height * 0.10f), size = Size(size.width, size.height * 0.10f + 1f),
    )
    drawRect(bg, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
}

fun DrawScope.drawActors(scene: SceneDef, state: SceneState, master: Float, t: Float, images: Map<String, ImageBitmap> = emptyMap()) {
    for (a in scene.actors) {
        val st = state[a.id] ?: continue
        when (a.kind) {
            "starOrder" -> drawStarOrder(a, st, master, t)
            "dome" -> drawDome(a, st, master)
            "shade" -> drawShade(st, master, t)
            "dimlights" -> drawDimLights(a, st, master)
            "image" -> drawImageActor(a, st, master, images)
            "beam" -> drawBeam(a, st, master)
            "crowd" -> drawCrowd(a, st, master)
            "smoke" -> drawSmoke(a, st, master, t)
            "wisps" -> drawWisps(a, st, master, t)
            "figure" -> drawFigure(a, st, master, t)
            "lantern" -> drawLantern(a, st, master, t)
        }
    }
}

fun DrawScope.drawFrame(frame: SceneFrame, alpha: Float, t: Float, images: Map<String, ImageBitmap>) {
    drawEnvironment(frame.scene.env, alpha, t)
    drawActors(frame.scene, frame.state, alpha, t, images)
}

@Composable
fun SceneCanvas(
    from: SceneFrame?,
    to: SceneFrame,
    transition: Float,
    timeMs: Long,
    animate: Boolean,
    modifier: Modifier = Modifier,
    images: Map<String, ImageBitmap> = emptyMap(),
) {
    Canvas(modifier = modifier) {
        val t = if (animate) timeMs / 1000f else 0f
        val p = transition.coerceIn(0f, 1f)
        if (from != null && p < 1f) {
            drawFrame(from, 1f, t, images)
            drawFrame(to, p, t, images)
        } else {
            drawFrame(to, 1f, t, images)
        }
    }
}
