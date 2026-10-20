package app.quranichorizons.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quranichorizons.i18n.tr
import app.quranichorizons.data.Catalog
import app.quranichorizons.data.ContentRepository
import app.quranichorizons.data.Prefs
import app.quranichorizons.data.SurahAvailability
import app.quranichorizons.data.SurahsFile

@Composable
fun HomeScreen(
    repo: ContentRepository,
    prefs: Prefs,
    surahs: SurahsFile,
    catalog: Catalog,
    onOpen: (Int) -> Unit,
    onContinue: (Int, Int) -> Unit,
    onSettings: () -> Unit,
) {
    var notice by remember { mutableStateOf("") }
    val last = prefs.lastSurah
    val lastInfo = surahs.surahs.firstOrNull { it.number == last }
    val canContinue = lastInfo != null && repo.availability(catalog, last) != SurahAvailability.IN_PREPARATION

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(QhColors.NavyDeep, QhColors.Navy))),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(app.quranichorizons.R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(84.dp),
                    )
                    Text("الآفاق القرآنية", color = QhColors.Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("Quranic Horizons", color = QhColors.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(tr("tagline"), color = QhColors.Ink, fontSize = 15.sp)
                }
            }
            if (canContinue) {
                item {
                    Card(
                        Modifier.fillMaxWidth().clickable { onContinue(last, prefs.lastAyah) },
                        colors = CardDefaults.cardColors(containerColor = QhColors.NavyCard),
                        border = BorderStroke(1.dp, QhColors.Gold.copy(alpha = 0.5f)),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(tr("continue"), color = QhColors.Gold, fontSize = 13.sp)
                            Text("${tr("surahNamed", lastInfo!!.nameAr)} — ${tr("ayahNo", prefs.lastAyah)}", color = QhColors.Ink, fontSize = 18.sp)
                        }
                    }
                }
            }
            if (notice.isNotEmpty()) {
                item { Text(notice, color = QhColors.GoldSoft, fontSize = 14.sp) }
            }
            item { Text(tr("surahs"), color = QhColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            items(surahs.surahs, key = { it.number }) { s ->
                val av = repo.availability(catalog, s.number)
                Card(
                    Modifier.fillMaxWidth().clickable {
                        if (av == SurahAvailability.IN_PREPARATION) {
                            notice = "سورة ${s.nameAr} قيد الإعداد. تُسلَّم السور بترتيب عكسي: الناس ثم الفلق وهكذا."
                        } else {
                            notice = ""
                            onOpen(s.number)
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = QhColors.NavyCard.copy(alpha = if (av == SurahAvailability.IN_PREPARATION) 0.55f else 1f)),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).background(QhColors.Navy, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${s.number}", color = QhColors.Gold, fontSize = 14.sp)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(s.nameAr, color = QhColors.Ink, fontSize = 18.sp)
                            Text(tr("ayahsCount", s.ayahCount), color = QhColors.Muted, fontSize = 12.sp)
                        }
                        StatusChip(av)
                    }
                }
            }
            item {
                TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("settings"), color = QhColors.GoldSoft)
                }
            }
        }
    }
}
