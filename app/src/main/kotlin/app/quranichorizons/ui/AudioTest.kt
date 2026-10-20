package app.quranichorizons.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quranichorizons.data.OnlineAudio
import app.quranichorizons.data.StreamManifest
import app.quranichorizons.i18n.tr
import app.quranichorizons.player.AudioPlayer
import kotlinx.coroutines.launch

/**
 * اختبار تقني للبثّ: يشغّل آيات الفاتحة (المتاحة في بيئة prelive) بالتتابع من Quran.Foundation عبر الخدمة الوسيطة.
 * يظهر فقط حين تكون التلاوة مضبوطة (نسخة الاختبار). لا تُحفظ أي ملفات صوت.
 */
@Composable
fun AudioTestSection() {
    if (!OnlineAudio.configured) return
    var manifest by remember { mutableStateOf<StreamManifest?>(null) }
    var idx by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val next = rememberUpdatedState<() -> Unit> {
        val size = manifest?.files?.size ?: 0
        if (idx + 1 < size) idx += 1 else { playing = false; idx = 0 }
    }
    val player = remember { AudioPlayer(onCompleted = { next.value() }, onFailed = { failed = true; playing = false }) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(playing, idx, manifest) {
        if (playing) manifest?.files?.getOrNull(idx)?.let { player.play(it.url) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("audioTest"), color = QhColors.Gold, fontSize = 18.sp)
        Text(tr("audioTestNote"), color = QhColors.Muted, fontSize = 12.sp)
        Button(
            enabled = !loading,
            onClick = {
                if (playing) { playing = false; player.release() }
                else scope.launch {
                    loading = true; failed = false
                    val m = OnlineAudio.fetch(1)
                    loading = false
                    if (m == null) failed = true else { manifest = m; idx = 0; playing = true }
                }
            },
        ) { Text(if (playing) tr("audioTestStop") else tr("audioTestPlay")) }
        manifest?.let {
            Text(tr("audioCredit", it.reciterName) + if (playing) "  ${idx + 1}/${it.files.size}" else "", color = QhColors.Muted, fontSize = 12.sp)
        }
        if (failed) {
            Text(tr("audioOffline"), color = QhColors.GoldSoft, fontSize = 12.sp)
            if (OnlineAudio.lastError.isNotBlank()) Text(OnlineAudio.lastError, color = QhColors.Muted, fontSize = 11.sp)
        }
    }
}
