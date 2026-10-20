package app.quranichorizons.data

import android.content.Context

/** تفضيلات المستخدم: آخر موضع، تقليل الحركة، حجم النص، وتيرة العرض. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("qh_prefs", Context.MODE_PRIVATE)

    var lastSurah: Int
        get() = sp.getInt("last_surah", 0)
        set(v) = sp.edit().putInt("last_surah", v).apply()

    var lastAyah: Int
        get() = sp.getInt("last_ayah", 1)
        set(v) = sp.edit().putInt("last_ayah", v).apply()

    var reduceMotion: Boolean
        get() = sp.getBoolean("reduce_motion", false)
        set(v) = sp.edit().putBoolean("reduce_motion", v).apply()

    /** مضاعف حجم نص الآية (1.0 = الافتراضي) */
    var textScale: Float
        get() = sp.getFloat("text_scale", 1.0f)
        set(v) = sp.edit().putFloat("text_scale", v).apply()

    /** ثواني بقاء كل آية في وضع القراءة (حين لا تتوفر تلاوة) */
    var paceSeconds: Int
        get() = sp.getInt("pace", 9)
        set(v) = sp.edit().putInt("pace", v).apply()

    /** رمز اللغة المختار يدويًا؛ فارغ = حسب لغة الجهاز */
    var langOverride: String
        get() = sp.getString("lang", "") ?: ""
        set(v) = sp.edit().putString("lang", v).apply()
}
