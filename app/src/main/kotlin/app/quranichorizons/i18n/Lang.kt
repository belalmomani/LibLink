package app.quranichorizons.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

/** لغات واجهة التطبيق وترجمة المعنى. نص القرآن لا يُترجم ولا يتغيّر بتغيّر اللغة أبدًا. */
enum class Lang(val code: String, val native: String, val flag: String) {
    EN("en", "English", "🇬🇧"),
    ZH("zh", "中文（普通话）", "🇨🇳"),
    HI("hi", "हिन्दी", "🇮🇳"),
    ES("es", "Español", "🇪🇸"),
    AR("ar", "العربية الفصحى", "🌍"),
    FR("fr", "Français", "🇫🇷"),
    BN("bn", "বাংলা", "🇧🇩");

    companion object {
        fun fromCode(code: String): Lang? = values().firstOrNull { it.code == code }

        /** حسب لغة الجهاز؛ وإن لم تكن مدعومة فالعربية. */
        fun fromDevice(locale: Locale = Locale.getDefault()): Lang =
            fromCode(locale.language.lowercase()) ?: AR

        fun resolve(override: String, locale: Locale = Locale.getDefault()): Lang =
            fromCode(override) ?: fromDevice(locale)
    }
}

val LocalLang = compositionLocalOf { Lang.AR }

/** نص واجهة بلغة الحالية. {0} {1} … مواضع للقيم. المفتاح الناقص يرجع للعربية ثم للمفتاح نفسه. */
@Composable
fun tr(key: String, vararg args: Any): String = Strings.get(LocalLang.current, key, *args)
