package app.quranichorizons

import app.quranichorizons.i18n.Lang
import app.quranichorizons.i18n.Strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LangTest {
    @Test fun sevenLanguagesAreSupported() {
        assertEquals(setOf("en", "zh", "hi", "es", "ar", "fr", "bn"), Lang.values().map { it.code }.toSet())
    }

    @Test fun deviceLanguageIsDefaultAndOverrideWins() {
        assertEquals(Lang.FR, Lang.resolve("", Locale("fr", "CA")))
        assertEquals(Lang.ZH, Lang.resolve("", Locale.SIMPLIFIED_CHINESE))
        assertEquals(Lang.AR, Lang.resolve("", Locale("de")))       // غير مدعومة → العربية
        assertEquals(Lang.BN, Lang.resolve("bn", Locale("fr")))     // الاختيار اليدوي أولًا
    }

    @Test fun everyUiKeyHasAllSevenLanguages() {
        for ((k, m) in Strings.table) for (l in Lang.values()) assertTrue("$k/$l", !m[l].isNullOrBlank())
    }

    @Test fun placeholdersAreFilled() {
        assertEquals("Surah An-Nas", Strings.get(Lang.EN, "surahNamed", "An-Nas"))
    }
}
