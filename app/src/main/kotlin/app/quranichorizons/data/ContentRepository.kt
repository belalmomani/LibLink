package app.quranichorizons.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.quranichorizons.BuildConfig
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * مصدر المحتوى: الوحدات المنزَّلة في التخزين الداخلي أولًا، ثم المحتوى المضمَّن في الأصول (نموذج أول).
 * كل ملف يُتحقق من بصمته قبل استعماله.
 */
class ContentRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val modulesDir: File get() = File(context.filesDir, "modules")

    private fun asset(path: String): ByteArray = context.assets.open(path).use { it.readBytes() }

    fun loadSurahs(): SurahsFile = json.decodeFromString(SurahsFile.serializer(), String(asset("surahs.json"), Charsets.UTF_8))

    fun loadCatalog(): Catalog = json.decodeFromString(Catalog.serializer(), String(asset("catalog.json"), Charsets.UTF_8))

    /** حالة كل سورة كما تظهر في القائمة. */
    fun availability(catalog: Catalog, surah: Int): SurahAvailability {
        val m = catalog.modules.firstOrNull { it.surah == surah } ?: return SurahAvailability.IN_PREPARATION
        return if (m.status == "approved") SurahAvailability.READY else SurahAvailability.PREVIEW
    }

    private fun downloaded(entry: CatalogModule, path: String): ByteArray? {
        val f = File(modulesDir, "${entry.surah}/${entry.version}/${path.substringAfterLast('/')}")
        return if (f.isFile) f.readBytes() else null
    }

    /** يحمّل وحدة سورة ويتحقق منها. يرمي استثناءً برسالة واضحة إن كانت تالفة. */
    fun loadModule(info: SurahInfo, entry: CatalogModule): SurahModule {
        fun bytesOf(path: String): ByteArray {
            val fe = entry.files.first { it.path == path }
            val b = downloaded(entry, path) ?: asset(path)
            check(ContentVerifier.fileMatches(fe, b)) { "بصمة الملف $path لا تطابق الكتالوج" }
            return b
        }
        fun text(path: String) = String(bytesOf(path), Charsets.UTF_8)
        val module = json.decodeFromString(ModuleFile.serializer(), text("${entry.dir}/module.json"))
        val tafsir = json.decodeFromString(TafsirFile.serializer(), text("${entry.dir}/tafsir.json")).records
        val scenes = json.decodeFromString(SceneFile.serializer(), text("${entry.dir}/scenes.json")).scenes.associateBy { it.id }
        val problems = ContentVerifier.structureProblems(info, module, scenes)
        check(problems.isEmpty()) { "وحدة السورة ${info.number} غير سليمة: " + problems.joinToString("، ") }
        return SurahModule(info, entry, module, tafsir, scenes)
    }

    /** ترجمة معنى الآيات للغة معيّنة إن وُجدت في الكتالوج (ومطابقة لبصمتها)؛ وإلا null فيُعرض العربي. */
    fun loadTranslation(module: SurahModule, lang: String): TranslationFile? {
        val path = "${module.catalog.dir}/translations/$lang.json"
        val fe = module.catalog.files.firstOrNull { it.path == path } ?: return null
        val b = try { downloaded(module.catalog, path) ?: asset(path) } catch (e: Exception) { return null }
        if (!ContentVerifier.fileMatches(fe, b)) return null
        return try { json.decodeFromString(TranslationFile.serializer(), String(b, Charsets.UTF_8)) } catch (e: Exception) { null }
    }

    /** صور عناصر المشهد (kind = image): تُحمَّل بعد التحقق من بصمة كل ملف؛ المفتاح هو معرّف العنصر. */
    fun loadImages(module: SurahModule): Map<String, ImageBitmap> {
        val out = HashMap<String, ImageBitmap>()
        for (scene in module.scenes.values) for (a in scene.actors) {
            if (a.kind != "image" || a.file.isBlank()) continue
            val fe = module.catalog.files.firstOrNull { it.path == a.file } ?: continue
            val b = downloaded(module.catalog, a.file) ?: asset(a.file)
            if (!ContentVerifier.fileMatches(fe, b)) continue   // صورة تالفة: لا تُعرض
            val bmp = BitmapFactory.decodeByteArray(b, 0, b.size) ?: continue
            out[a.id] = bmp.asImageBitmap()
        }
        return out
    }

    // ---------- التنزيل من خادم المحتوى (يعمل فقط حين يُضبط CONTENT_BASE_URL) ----------

    val remoteConfigured: Boolean get() = BuildConfig.CONTENT_BASE_URL.isNotBlank()

    private fun httpGet(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    fun fetchRemoteCatalog(): Catalog {
        val base = BuildConfig.CONTENT_BASE_URL.trimEnd('/')
        return json.decodeFromString(Catalog.serializer(), String(httpGet("$base/catalog.json"), Charsets.UTF_8))
    }

    /** ينزّل ملفات الوحدة في مجلد مؤقت ثم ينقلها دفعة واحدة بعد التحقق من كل بصمة (3 محاولات لكل ملف). */
    fun downloadModule(entry: CatalogModule) {
        val base = BuildConfig.CONTENT_BASE_URL.trimEnd('/')
        val tmp = File(modulesDir, "${entry.surah}/.tmp-${entry.version}")
        tmp.deleteRecursively()
        tmp.mkdirs()
        for (fe in entry.files) {
            var last: Exception? = null
            var ok = false
            for (attempt in 1..3) {
                try {
                    val b = httpGet("$base/${fe.path}")
                    check(ContentVerifier.fileMatches(fe, b)) { "بصمة ${fe.path} لا تطابق" }
                    File(tmp, fe.path.substringAfterLast('/')).writeBytes(b)
                    ok = true
                    break
                } catch (e: Exception) {
                    last = e
                    Thread.sleep(600L * attempt)
                }
            }
            if (!ok) {
                tmp.deleteRecursively()
                throw IllegalStateException("تعذر تنزيل ${fe.path}: ${last?.message}")
            }
        }
        val dest = File(modulesDir, "${entry.surah}/${entry.version}")
        dest.deleteRecursively()
        check(tmp.renameTo(dest)) { "تعذر حفظ الوحدة" }
    }

    fun downloadedSizeBytes(): Long = modulesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun deleteDownloaded() {
        modulesDir.deleteRecursively()
    }
}
