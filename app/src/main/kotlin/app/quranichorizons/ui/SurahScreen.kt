package app.quranichorizons.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quranichorizons.i18n.tr
import app.quranichorizons.data.Catalog
import app.quranichorizons.data.ContentRepository
import app.quranichorizons.data.SurahAvailability
import app.quranichorizons.data.SurahModule
import app.quranichorizons.data.SurahsFile

/** تحميل وحدة السورة مع رسالة خطأ صريحة بدل الانهيار. */
fun loadModuleSafely(repo: ContentRepository, surahs: SurahsFile, catalog: Catalog, number: Int): Pair<SurahModule?, String?> {
    val info = surahs.surahs.firstOrNull { it.number == number } ?: return null to "سورة غير معروفة"
    val entry = catalog.modules.firstOrNull { it.surah == number } ?: return null to "السورة قيد الإعداد"
    return try {
        repo.loadModule(info, entry) to null
    } catch (e: Exception) {
        null to (e.message ?: "تعذر تحميل السورة")
    }
}

@Composable
fun SurahScreen(
    repo: ContentRepository,
    surahs: SurahsFile,
    catalog: Catalog,
    number: Int,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val (module, error) = remember(number) { loadModuleSafely(repo, surahs, catalog, number) }
    val av = repo.availability(catalog, number)

    Column(
        Modifier.fillMaxSize().background(QhColors.NavyDeep).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ " + tr("back"), color = QhColors.GoldSoft) }
        if (module == null) {
            Text("تعذر فتح السورة", color = QhColors.Gold, fontSize = 22.sp)
            Text(error ?: "", color = QhColors.Muted, fontSize = 15.sp)
            return@Column
        }
        val info = module.info
        Text(tr("surahNamed", info.nameAr), color = QhColors.Gold, fontSize = 32.sp)
        Text("${info.nameEn} — ${info.meaning}", color = QhColors.Muted, fontSize = 14.sp)
        Text("${tr("ayahsCount", info.ayahCount)} • ${info.revelation}", color = QhColors.Ink, fontSize = 14.sp)
        Column(horizontalAlignment = Alignment.Start) { StatusChip(av) }

        if (av != SurahAvailability.READY) {
            Column(
                Modifier.fillMaxWidth().background(QhColors.NavyCard, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("هذه معاينة قيد المراجعة", color = QhColors.GoldSoft, fontSize = 15.sp)
                Text(
                    "لم تُعتمد السورة بعد. المعوّقات الحالية:",
                    color = QhColors.Muted, fontSize = 13.sp,
                )
                module.module.review.blockers.forEach { Text("• $it", color = QhColors.Ink, fontSize = 13.sp) }
            }
        }
        Text(
            "المشاهد تتبع معاني التفسير المنقول حرفيًا في الشاشة التالية، ولا تُصوِّر أنبياء ولا ذات الله تعالى.",
            color = QhColors.Muted, fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = QhColors.Gold, contentColor = QhColors.NavyDeep),
        ) { Text(if (av == SurahAvailability.READY) tr("start") else tr("startPreview"), fontSize = 17.sp) }
    }
}
