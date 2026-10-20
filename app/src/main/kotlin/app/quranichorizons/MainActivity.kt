package app.quranichorizons

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.quranichorizons.data.Catalog
import app.quranichorizons.data.ContentRepository
import app.quranichorizons.data.Prefs
import app.quranichorizons.data.SurahsFile
import app.quranichorizons.i18n.Lang
import app.quranichorizons.i18n.LocalLang
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.quranichorizons.ui.HomeScreen
import app.quranichorizons.ui.PlayerScreen
import app.quranichorizons.ui.QhTheme
import app.quranichorizons.ui.SettingsScreen
import app.quranichorizons.ui.SurahScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = ContentRepository(applicationContext)
        val prefs = Prefs(applicationContext)
        setContent { QhTheme { App(repo, prefs) } }
    }
}

private const val HOME = 0
private const val SURAH = 1
private const val PLAYER = 2
private const val SETTINGS = 3

@Composable
private fun App(repo: ContentRepository, prefs: Prefs) {
    val surahs: SurahsFile = remember { repo.loadSurahs() }
    val catalog: Catalog = remember { repo.loadCatalog() }
    var langOverride by remember { mutableStateOf(prefs.langOverride) }
    val lang = Lang.resolve(langOverride)
    var screen by rememberSaveable { mutableStateOf(HOME) }
    var selected by rememberSaveable { mutableStateOf(0) }
    var startAyah by rememberSaveable { mutableStateOf(1) }

    BackHandler(enabled = screen != HOME) {
        screen = if (screen == PLAYER) SURAH else HOME
    }

    CompositionLocalProvider(
        LocalLang provides lang,
        LocalLayoutDirection provides (if (lang == Lang.AR) LayoutDirection.Rtl else LayoutDirection.Ltr),
    ) {
    when (screen) {
        HOME -> HomeScreen(
            repo = repo, prefs = prefs, surahs = surahs, catalog = catalog,
            onOpen = { selected = it; screen = SURAH },
            onContinue = { s, a -> selected = s; startAyah = a; screen = PLAYER },
            onSettings = { screen = SETTINGS },
        )
        SURAH -> SurahScreen(
            repo = repo, surahs = surahs, catalog = catalog, number = selected,
            onBack = { screen = HOME },
            onStart = { startAyah = 1; screen = PLAYER },
        )
        PLAYER -> PlayerScreen(
            repo = repo, prefs = prefs, surahs = surahs, catalog = catalog, number = selected, startAyah = startAyah,
            onExit = { screen = SURAH },
            onToList = { screen = HOME },
        )
        SETTINGS -> SettingsScreen(
            repo = repo, prefs = prefs, surahs = surahs, catalog = catalog, onBack = { screen = HOME },
            langOverride = langOverride, onLang = { langOverride = it; prefs.langOverride = it },
        )
    }
    }
}
