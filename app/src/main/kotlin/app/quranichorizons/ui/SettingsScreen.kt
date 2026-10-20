package app.quranichorizons.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quranichorizons.BuildConfig
import app.quranichorizons.i18n.Lang
import app.quranichorizons.i18n.LocalLang
import app.quranichorizons.i18n.tr
import app.quranichorizons.data.Catalog
import app.quranichorizons.data.ContentRepository
import app.quranichorizons.data.Prefs
import app.quranichorizons.data.SurahsFile

/** عناصر التفضيلات المشتركة بين شاشة الإعدادات ونافذة المشغّل. */
@Composable
fun PrefsControls(
    reduce: Boolean, onReduce: (Boolean) -> Unit,
    scale: Float, onScale: (Float) -> Unit,
    pace: Int, onPace: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("reduceMotion"), color = QhColors.Ink, modifier = Modifier.weight(1f))
            Switch(checked = reduce, onCheckedChange = onReduce)
        }
        Text(tr("textSize", (scale * 100).toInt()), color = QhColors.Ink, fontSize = 14.sp)
        Slider(value = scale, onValueChange = onScale, valueRange = 0.8f..1.5f)
        Text(tr("pace", pace), color = QhColors.Ink, fontSize = 14.sp)
        Slider(value = pace.toFloat(), onValueChange = { onPace(it.toInt()) }, valueRange = 5f..25f)
    }
}

private fun mb(bytes: Long): String = "%.2f ميغابايت".format(bytes / 1048576.0)

@Composable
fun SettingsScreen(
    repo: ContentRepository, prefs: Prefs, surahs: SurahsFile, catalog: Catalog, onBack: () -> Unit,
    langOverride: String, onLang: (String) -> Unit,
) {
    var reduce by remember { mutableStateOf(prefs.reduceMotion) }
    var scale by remember { mutableStateOf(prefs.textScale) }
    var pace by remember { mutableStateOf(prefs.paceSeconds) }
    var downloaded by remember { mutableStateOf(repo.downloadedSizeBytes()) }

    Column(
        Modifier.fillMaxSize().background(QhColors.NavyDeep).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ " + tr("back"), color = QhColors.GoldSoft) }
        Text(tr("settingsTitle"), color = QhColors.Gold, fontSize = 26.sp)
        Text(tr("language"), color = QhColors.Gold, fontSize = 18.sp)
        Text(tr("langNote"), color = QhColors.Muted, fontSize = 12.sp)
        TextButton(onClick = { onLang("") }) {
            Text((if (langOverride.isEmpty()) "● " else "○ ") + tr("langAuto") + " — " + Lang.fromDevice().native, color = QhColors.Ink)
        }
        Lang.values().forEach { l ->
            TextButton(onClick = { onLang(l.code) }) {
                Text((if (langOverride == l.code) "● " else "○ ") + l.flag + "  " + l.native, color = QhColors.Ink)
            }
        }
        PrefsControls(
            reduce, { reduce = it; prefs.reduceMotion = it },
            scale, { scale = it; prefs.textScale = it },
            pace, { pace = it; prefs.paceSeconds = it },
        )

        Text("المحتوى المنزَّل", color = QhColors.Gold, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
        Text("المساحة المستخدمة: ${mb(downloaded)}", color = QhColors.Ink, fontSize = 14.sp)
        Text(
            if (repo.remoteConfigured) "خادم المحتوى مضبوط. لا تُنزَّل وحدات كبيرة تلقائيًا على بيانات الجوال."
            else "لا يوجد خادم محتوى مضبوط في هذا الإصدار؛ المعاينة مضمّنة في التطبيق.",
            color = QhColors.Muted, fontSize = 13.sp,
        )
        TextButton(onClick = { repo.deleteDownloaded(); downloaded = repo.downloadedSizeBytes() }) {
            Text("حذف المحتوى المنزَّل", color = QhColors.GoldSoft)
        }

        Text("حالة المحتوى", color = QhColors.Gold, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
        catalog.modules.forEach { m ->
            val name = surahs.surahs.firstOrNull { it.number == m.surah }?.nameAr ?: "${m.surah}"
            Text("سورة $name — الإصدار ${m.version} — الحالة: ${m.status} — الحجم ${mb(m.totalBytes)}", color = QhColors.Ink, fontSize = 13.sp)
        }
        Text(
            if (app.quranichorizons.data.OnlineAudio.configured) tr("audioStatusOn") else tr("audioStatusOff"),
            color = QhColors.Muted, fontSize = 13.sp,
        )
        AudioTestSection()
        Text("الإعلانات: غير مفعّلة في هذا الإصدار", color = QhColors.Muted, fontSize = 13.sp)
        Text("الإصدار ${BuildConfig.VERSION_NAME}", color = QhColors.Muted, fontSize = 12.sp)
    }
}
