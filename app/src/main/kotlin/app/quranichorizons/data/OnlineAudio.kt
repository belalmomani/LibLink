package app.quranichorizons.data

import app.quranichorizons.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** رابط تلاوة آية واحدة كما يرجع من خدمة الوسيط (مصدرها Quran.Foundation). لا تُخزَّن الملفات أبدًا؛ تُبثّ مباشرة. */
@Serializable
data class StreamFile(val verseKey: String, val url: String)

@Serializable
data class StreamManifest(
    val source: String,            // "Quran.Foundation"
    val reciterName: String,
    val recitationId: Int,
    val files: List<StreamFile>,
)

/**
 * التلاوة أونلاين: يطلب التطبيق قائمة الروابط من خدمة وسيطة تحمل بيانات اعتماد Quran.Foundation
 * (لا يجوز وضع client secret في التطبيق). القائمة تبقى في الذاكرة فقط ولا تُحفظ على القرص.
 */
object OnlineAudio {
    private val json = Json { ignoreUnknownKeys = true }

    /** أسماء النطاقات المسموح البثّ منها (لاحقة). تُراجَع مع Quran.Foundation قبل التفعيل. */
    val allowedHostSuffixes = listOf("qurancdn.com", "quran.com", "quran.foundation")

    val configured: Boolean get() = BuildConfig.AUDIO_ENABLED && BuildConfig.AUDIO_API_BASE_URL.isNotBlank()

    /** يتحقق من المانيفست: https فقط، نطاق مسموح، ومفاتيح آيات سليمة. يرجع null إن كان أي شيء مشبوهًا. */
    fun parse(text: String, surah: Int, hosts: List<String> = allowedHostSuffixes): StreamManifest? {
        val m = try { json.decodeFromString(StreamManifest.serializer(), text) } catch (e: Exception) { return null }
        if (m.source != "Quran.Foundation" || m.reciterName.isBlank() || m.files.isEmpty()) return null
        for (f in m.files) {
            if (!Regex("^$surah:\\d{1,3}$").matches(f.verseKey)) return null
            val u = try { URL(f.url) } catch (e: Exception) { return null }
            if (u.protocol != "https") return null
            if (hosts.none { u.host == it || u.host.endsWith(".$it") }) return null
        }
        return m
    }

    /** سبب آخر فشل (للتشخيص في شاشة الاختبار فقط) */
    @Volatile var lastError: String = ""

    suspend fun fetch(surah: Int): StreamManifest? = withContext(Dispatchers.IO) {
        lastError = ""
        try {
            val c = URL("${BuildConfig.AUDIO_API_BASE_URL.trimEnd('/')}/audio.php?surah=$surah&recitation=${BuildConfig.RECITATION_ID}")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "QuranicHorizons/${BuildConfig.VERSION_NAME} (Android)")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) { lastError = "HTTP ${c.responseCode}"; return@withContext null }
            val body = c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
            val m = parse(body, surah)
            if (m == null) lastError = "الرد ليس JSON متوقعًا: " + body.trim().take(60).replace("\n", " ")
            m
        } catch (e: Exception) { lastError = e.javaClass.simpleName + ": " + (e.message ?: ""); null }
    }
}
