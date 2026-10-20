package app.quranichorizons.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.quranichorizons.BuildConfig
import app.quranichorizons.data.OnlineAudio
import app.quranichorizons.data.StreamManifest
import app.quranichorizons.i18n.Lang
import app.quranichorizons.i18n.LocalLang
import app.quranichorizons.i18n.tr
import app.quranichorizons.data.Catalog
import app.quranichorizons.data.ContentRepository
import app.quranichorizons.data.Prefs
import app.quranichorizons.data.SurahsFile
import app.quranichorizons.player.AudioPlayer
import app.quranichorizons.scene.SceneCanvas
import app.quranichorizons.scene.SceneFrame
import app.quranichorizons.scene.SceneTimeline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    repo: ContentRepository,
    prefs: Prefs,
    surahs: SurahsFile,
    catalog: Catalog,
    number: Int,
    startAyah: Int,
    onExit: () -> Unit,
    onToList: () -> Unit,
) {
    val (mod, error) = remember(number) { loadModuleSafely(repo, surahs, catalog, number) }
    if (mod == null) {
        Column(Modifier.fillMaxSize().background(QhColors.NavyDeep).padding(24.dp)) {
            Text(error ?: "تعذر التحميل", color = QhColors.Gold, fontSize = 18.sp)
            TextButton(onClick = onExit) { Text(tr("back"), color = QhColors.GoldSoft) }
        }
        return
    }
    val ayahs = mod.module.ayahs
    val images = remember(mod) { repo.loadImages(mod) }
    var index by remember { mutableStateOf((startAyah - 1).coerceIn(0, ayahs.size - 1)) }
    var playing by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }
    var showTafsir by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var reduce by remember { mutableStateOf(prefs.reduceMotion) }
    var scale by remember { mutableStateOf(prefs.textScale) }
    var pace by remember { mutableStateOf(prefs.paceSeconds) }
    var audioFailed by remember { mutableStateOf<String?>(null) }
    var replayKey by remember { mutableStateOf(0) }
    var timeMs by remember { mutableLongStateOf(0L) }   // ساعة الحركة الدقيقة (وميض/تموّج)
    var ayahMs by remember { mutableLongStateOf(0L) }   // الزمن المنقضي داخل الآية الحالية

    // التلاوة تُبثّ أونلاين من Quran.Foundation عبر الخدمة الوسيطة؛ وإن تعذّرت يستمر وضع القراءة
    var manifest by remember(number) { mutableStateOf<StreamManifest?>(null) }
    LaunchedEffect(number) { if (OnlineAudio.configured) { manifest = OnlineAudio.fetch(number); if (manifest == null) audioFailed = "manifest" } }
    val streamUrl = manifest?.files?.firstOrNull { it.verseKey == "$number:${ayahs[index].number}" }?.url
    val audioMode = OnlineAudio.configured && manifest != null && streamUrl != null && audioFailed == null
    val active = playing && !showTafsir && !showSettings && !finished
    val ayah = ayahs[index]
    val scene = mod.scenes.getValue(ayah.sceneId)
    val beat = scene.beats.firstOrNull { it.ayah == ayah.number }

    fun goTo(i: Int) {
        ayahMs = 0L
        finished = false
        index = i.coerceIn(0, ayahs.size - 1)
    }

    // مدة الآية: المدة المقيسة من ملف التلاوة إن وُجدت (تتبعها الصور)، وإلا وتيرة القراءة
    val ayahLenMs: Long = ayah.audio.durationMs?.takeIf { it > 0 } ?: (pace * 1000L)

    val lang = LocalLang.current
    val translation = remember(mod, lang) { repo.loadTranslation(mod, lang.code) }

    val advanceState = rememberUpdatedState<() -> Unit> {
        ayahMs = 0L
        if (index < ayahs.size - 1) { index += 1 } else { finished = true }
    }

    LaunchedEffect(index) { prefs.lastSurah = number; prefs.lastAyah = index + 1 }

    // التلاوة (معطّلة في هذا الإصدار؛ تُفعَّل بعد التحقق من ترخيص القارئ)
    val player = remember {
        AudioPlayer(onCompleted = { advanceState.value() }, onFailed = { audioFailed = it })
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(index, audioMode, replayKey) { if (audioMode && streamUrl != null) player.play(streamUrl) }
    LaunchedEffect(active, audioMode) { if (audioMode) { if (active) player.resume() else player.pause() } }

    // ساعة واحدة تحرّك المشهد وتقدّم الآيات (في وضع القراءة) فلا ينفصل المشهد عن الآية
    LaunchedEffect(active, audioMode, ayahLenMs) {
        if (active) {
            var last = withFrameMillis { it }
            while (true) {
                val now = withFrameMillis { it }
                val dt = now - last
                last = now
                timeMs += dt
                if (!audioMode) {
                    ayahMs += dt
                    if (ayahMs >= ayahLenMs) advanceState.value()
                }
            }
        }
    }

    // تقدّم الحدث داخل الآية: يتبع التلاوة إن وُجدت، وإلا مؤقّت القراءة. تقليل الحركة = الحالة النهائية للحدث.
    val progress: Float = when {
        reduce -> 1f
        audioMode -> player.fraction()
        else -> (ayahMs / ayahLenMs.toFloat()).coerceIn(0f, 1f)
    }
    val frame = SceneFrame(scene, SceneTimeline.resolve(scene, ayah.number, progress))

    // الانتقال بين مشهدين مختلفين الموضوع: مزج من آخر لقطة للمشهد السابق
    var fromFrame by remember { mutableStateOf<SceneFrame?>(null) }
    var curSceneId by remember { mutableStateOf(scene.id) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(scene.id) {
        if (scene.id != curSceneId) {
            val prev = mod.scenes.getValue(curSceneId)
            fromFrame = SceneFrame(prev, SceneTimeline.finalState(prev))
            curSceneId = scene.id
            if (reduce) fade.snapTo(1f) else {
                fade.snapTo(0f)
                fade.animateTo(1f, tween(scene.transition.ms))
            }
            fromFrame = null
        }
    }

    // إيقاف عند خروج التطبيق للخلفية
    val ctx = LocalContext.current
    DisposableEffect(ctx) {
        val lc = (ctx as? ComponentActivity)?.lifecycle
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) playing = false }
        lc?.addObserver(obs)
        onDispose { lc?.removeObserver(obs) }
    }

    Box(Modifier.fillMaxSize().background(QhColors.NavyDeep)) {
        SceneCanvas(
            from = fromFrame, to = frame, transition = fade.value,
            timeMs = timeMs, animate = active && !reduce, modifier = Modifier.fillMaxSize(), images = images,
        )
        // غلالة خفيفة أسفل الشاشة فقط لحماية وضوح النص؛ منطقة الحدث في الأعلى تبقى صافية
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color(0x44060D25), 0.45f to Color.Transparent, 0.62f to Color(0xAA060D25), 1.0f to Color(0xDD060D25),
                ),
            ),
        )
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onExit) { Text("‹", color = QhColors.GoldSoft, fontSize = 22.sp) }
                Text(
                    "${tr("surahNamed", mod.info.nameAr)} — ${index + 1}/${ayahs.size}",
                    color = QhColors.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
                TextButton(onClick = { playing = false; showSettings = true }) { Text("⚙", color = QhColors.GoldSoft, fontSize = 20.sp) }
            }
            if (mod.module.review.status != "approved") {
                Text(
                    "معاينة قيد المراجعة — المشاهد غير معتمدة بعد",
                    color = QhColors.GoldSoft, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                )
            }
            // منطقة المشهد (الحدث البصري) في الأعلى
            Spacer(Modifier.weight(1.1f))
            // وصف قصير لما يحدث الآن، يربط المشهد بمعنى الآية
            if (beat != null) {
                Text(
                    beat.visualEvent, color = QhColors.GoldSoft, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), textAlign = TextAlign.Center,
                )
            }
            Box(Modifier.weight(0.9f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (index == 0 && mod.module.basmala != null) {
                        Text(
                            mod.module.basmala, style = AyahStyle, color = QhColors.GoldSoft,
                            fontSize = (22 * scale).sp, lineHeight = (40 * scale).sp, textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        ayah.text, style = AyahStyle, fontSize = (30 * scale).sp, lineHeight = (54 * scale).sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            manifest?.let {
                Text(
                    if (audioMode) tr("audioCredit", it.reciterName) else tr("audioOffline"),
                    color = QhColors.Muted, fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                )
            }
            if (OnlineAudio.configured && manifest == null && audioFailed != null) {
                Text(tr("audioOffline"), color = QhColors.Muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Slider(
                value = index.toFloat(),
                onValueChange = { goTo(it.toInt()) },
                valueRange = 0f..(ayahs.size - 1).toFloat().coerceAtLeast(1f),
                steps = (ayahs.size - 2).coerceAtLeast(0),
            )
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { if (index > 0) goTo(index - 1) }) { Text(tr("prev"), color = QhColors.Ink) }
                Button(
                    onClick = { if (finished) { goTo(0); replayKey++; playing = true } else playing = !playing },
                    colors = ButtonDefaults.buttonColors(containerColor = QhColors.Gold, contentColor = QhColors.NavyDeep),
                ) { Text(if (playing && !finished) tr("pause") else tr("play")) }
                TextButton(onClick = { if (index < ayahs.size - 1) goTo(index + 1) }) { Text(tr("next"), color = QhColors.Ink) }
            }
            TextButton(
                onClick = { playing = false; showTafsir = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("meaningBtn"), color = QhColors.GoldSoft) }
        }

        if (finished) {
            Box(Modifier.fillMaxSize().background(Color(0xDD060D25)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("finished", mod.info.nameAr), color = QhColors.Gold, fontSize = 24.sp)
                    Button(
                        onClick = { goTo(0); playing = true; replayKey++ },
                        colors = ButtonDefaults.buttonColors(containerColor = QhColors.Gold, contentColor = QhColors.NavyDeep),
                    ) { Text(tr("replay")) }
                    TextButton(onClick = onToList) { Text(tr("toSurahs"), color = QhColors.GoldSoft) }
                }
            }
        }
    }

    if (showTafsir) {
        ModalBottomSheet(onDismissRequest = { showTafsir = false }, containerColor = QhColors.NavyCard) {
            var tab by remember { mutableStateOf(0) }
            val recs = mod.tafsir.filter { it.ayahs.isEmpty() || ayah.number in it.ayahs }
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (beat != null) {
                    Text("معنى الآية (خلاصة تحريرية)", color = QhColors.Gold, fontSize = 16.sp)
                    Text(beat.meaning, color = QhColors.Ink, fontSize = 15.sp, lineHeight = 26.sp)
                    if (lang != Lang.AR) {
                        Text(tr("transHeader"), color = QhColors.Gold, fontSize = 16.sp)
                        val tm = translation?.meaning?.get(ayah.number.toString())
                        if (tm != null) {
                            Text(tm, color = QhColors.Ink, fontSize = 15.sp)
                            Text(tr("transUnapproved") + (translation?.source?.let { " ($it)" } ?: ""), color = QhColors.Muted, fontSize = 12.sp)
                        } else {
                            Text(tr("transMissing"), color = QhColors.Muted, fontSize = 12.sp)
                        }
                    }
                    Text("ما يظهر في المشهد", color = QhColors.Gold, fontSize = 16.sp)
                    Text(beat.visualEvent, color = QhColors.Ink, fontSize = 15.sp, lineHeight = 26.sp)
                    Text("سبب الملاءمة", color = QhColors.Gold, fontSize = 16.sp)
                    Text(beat.rationale, color = QhColors.Ink, fontSize = 15.sp, lineHeight = 26.sp)
                    if (beat.gap.isNotBlank()) {
                        Text("ما لا تُظهره هذه اللقطة: ${beat.gap}", color = QhColors.GoldSoft, fontSize = 13.sp)
                    }
                    if (beat.symbolic) {
                        Text("تمثيل رمزي: ${beat.symbolicNote}", color = QhColors.Muted, fontSize = 13.sp)
                    }
                    val src = mod.tafsir.firstOrNull { it.id == beat.tafsirRecord }?.bookAr ?: "التفسير"
                    Text("المقطع المعتمد عليه (نقل حرفي من $src — النسبة قيد المراجعة)", color = QhColors.Gold, fontSize = 14.sp)
                    Text(beat.tafsirQuote, color = QhColors.Ink, fontSize = 15.sp, lineHeight = 26.sp)
                }
                if (recs.isNotEmpty()) {
                    androidx.compose.material3.TabRow(
                        selectedTabIndex = tab.coerceIn(0, recs.size - 1), containerColor = QhColors.NavyCard,
                    ) {
                        recs.forEachIndexed { i, r ->
                            androidx.compose.material3.Tab(selected = tab == i, onClick = { tab = i }, text = { Text(r.bookAr, fontSize = 13.sp) })
                        }
                    }
                    val r = recs[tab.coerceIn(0, recs.size - 1)]
                    Text("${r.bookAr} — ${r.authorAr}", color = QhColors.GoldSoft, fontSize = 14.sp)
                    Text("نقل حرفي — النسبة قيد المراجعة (الترخيص: ${r.licenseStatus})", color = QhColors.Muted, fontSize = 12.sp)
                    r.sourceNotes.forEach { Text("• $it", color = QhColors.Muted, fontSize = 12.sp) }
                    Text(r.text, color = QhColors.Ink, fontSize = 15.sp, lineHeight = 26.sp)
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = QhColors.NavyCard,
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text(tr("done"), color = QhColors.Gold) } },
            title = { Text(tr("displaySettings"), color = QhColors.Gold) },
            text = {
                PrefsControls(
                    reduce, { reduce = it; prefs.reduceMotion = it },
                    scale, { scale = it; prefs.textScale = it },
                    pace, { pace = it; prefs.paceSeconds = it },
                )
            },
        )
    }
}
