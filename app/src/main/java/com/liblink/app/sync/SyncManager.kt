package com.liblink.app.sync

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.liblink.app.R
import com.liblink.app.data.LibSnapshot
import com.liblink.app.data.LibraryRepository
import com.liblink.app.data.ProjectRepository
import com.liblink.app.data.libKey
import com.liblink.app.data.mergeLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// الإعدادات المتزامنة: آخر تعديل هو الفائز. الـ Token يُرفع مشفرًا فقط إن فعّله المستخدم
private data class Cfg(
    val user: String, val name: String, val email: String,
    val testAds: Boolean, val lang: String, val tokenEnc: String, val at: Long
) {
    fun toJson(): String = JSONObject().put("user", user).put("name", name).put("email", email)
        .put("testAds", testAds).put("lang", lang).put("tokenEnc", tokenEnc).toString()

    companion object {
        fun fromJson(s: String?, at: Long): Cfg {
            val o = runCatching { JSONObject(s ?: "{}") }.getOrDefault(JSONObject())
            return Cfg(o.optString("user"), o.optString("name"), o.optString("email"),
                o.optBoolean("testAds", true), o.optString("lang"), o.optString("tokenEnc"), at)
        }
    }
}

private class Data(val lib: LibSnapshot, val cfg: Cfg)
private class Merged(val data: Data, val remoteSettingsWon: Boolean)

// مزامنة المكتبة والإعدادات بين أجهزة نفس الحساب عبر Firebase Auth + Firestore
object SyncManager {
    // تتغير بعد كل مزامنة فتتحدث الشاشات التي تقرأها
    var changeCount by mutableIntStateOf(0)
    var warningRes by mutableIntStateOf(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var pending: Job? = null

    fun available(ctx: Context): Boolean =
        runCatching { FirebaseApp.getApps(ctx.applicationContext).isNotEmpty() }.getOrDefault(false)

    fun signedIn(): Boolean = runCatching { FirebaseAuth.getInstance().currentUser != null }.getOrDefault(false)
    fun email(): String = runCatching { FirebaseAuth.getInstance().currentUser?.email.orEmpty() }.getOrDefault("")

    suspend fun signIn(email: String, pass: String) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), pass).await()
    }

    suspend fun register(email: String, pass: String) {
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), pass).await()
    }

    suspend fun resetPassword(email: String) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() { runCatching { FirebaseAuth.getInstance().signOut() } }

    // رسالة مفهومة بدل أخطاء Firebase الخام
    fun describe(ctx: Context, e: Exception): String {
        val res = when {
            e is FirebaseAuthWeakPasswordException -> R.string.sync_err_weak
            e is FirebaseAuthInvalidCredentialsException -> R.string.sync_err_cred
            e is FirebaseAuthInvalidUserException -> R.string.sync_err_user
            e is FirebaseAuthUserCollisionException -> R.string.sync_err_exists
            e is FirebaseNetworkException -> R.string.sync_err_network
            e is FirebaseAuthException && e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" -> R.string.sync_err_notallowed
            e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.sync_err_perm
            e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> R.string.sync_err_network
            e is IllegalStateException && !available(ctx) -> R.string.sync_err_nosync
            else -> 0
        }
        return if (res != 0) ctx.getString(res) else (e.message ?: e.toString())
    }

    // عند تغيير محلي: مزامنة بعد 3 ثوانٍ (تجميع التغييرات المتتابعة)
    fun onLocalChange(ctx: Context) = request(ctx.applicationContext, 3000)

    // عند فتح التطبيق: مزامنة فورية لجلب ما غيّرته من جهاز آخر
    fun requestNow(ctx: Context) = request(ctx.applicationContext, 0)

    private fun request(app: Context, delayMs: Long) {
        val repo = ProjectRepository(app)
        if (!signedIn() || !repo.syncAuto() || !repo.syncApproved()) return
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            runCatching { syncNow(app) }
        }
    }

    private fun localData(app: Context, withToken: Boolean = true): Data {
        val repo = ProjectRepository(app)
        val c = repo.credentials()
        val pass = repo.syncPassphrase()
        val enc = if (withToken && repo.tokenSync() && pass.isNotEmpty() && c.token.isNotBlank()) Crypto.encrypt(c.token, pass) else ""
        return Data(
            LibraryRepository(app).snapshot(),
            Cfg(c.user, c.authorName, c.authorEmail, repo.testAds(), repo.language(), enc, repo.settingsUpdatedAt())
        )
    }

    private fun parseRemote(snap: DocumentSnapshot): Pair<LibSnapshot, Cfg> {
        val lib = LibSnapshot(
            LibraryRepository.itemsFromJson(JSONArray(snap.getString("library") ?: "[]")),
            LibraryRepository.tombsFromJson(JSONArray(snap.getString("tombs") ?: "[]")),
            JSONArray(snap.getString("categories") ?: "[]").let { a -> (0 until a.length()).map { a.getString(it) } }
        )
        return lib to Cfg.fromJson(snap.getString("settings"), snap.getLong("settingsAt") ?: 0L)
    }

    // معاينة نتيجة الدمج دون كتابة أي شيء: ما سيُضاف هنا وما سيُزال
    class Preview(
        val localCount: Int, val remoteCount: Int, val mergedCount: Int,
        val added: List<String>, val removed: List<String>, val settingsFromCloud: Boolean
    )

    suspend fun preview(ctx: Context): Preview = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val user = FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException("not signed in")
        val snap = FirebaseFirestore.getInstance().collection("users").document(user.uid)
            .collection("sync").document("main").get().await()
        val (remoteLib, remoteCfg) = parseRemote(snap)
        val local = localData(app, withToken = false)
        val merged = mergeLibrary(local.lib, remoteLib)
        val localKeys = local.lib.items.map { libKey(it.url) }.toSet()
        val mergedKeys = merged.items.map { libKey(it.url) }.toSet()
        Preview(
            local.lib.items.size, remoteLib.items.size, merged.items.size,
            merged.items.filter { libKey(it.url) !in localKeys }.map { it.name },
            local.lib.items.filter { libKey(it.url) !in mergedKeys }.map { it.name },
            snap.exists() && remoteCfg.at > local.cfg.at
        )
    }

    // الدمج الكامل بين هذا الجهاز وما في السحابة داخل معاملة (Transaction) آمنة من التعارض
    suspend fun syncNow(ctx: Context) = mutex.withLock {
        val app = ctx.applicationContext
        val user = FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException("not signed in")
        val db = FirebaseFirestore.getInstance()
        val ref = db.collection("users").document(user.uid).collection("sync").document("main")

        val merged: Merged = db.runTransaction { tx ->
            val snap = tx.get(ref)
            val (remoteLib, remoteCfg) = parseRemote(snap)
            val local = localData(app)

            val lib = mergeLibrary(local.lib, remoteLib)
            val remoteWon = remoteCfg.at >= local.cfg.at
            val cfg = if (local.cfg.at > remoteCfg.at) local.cfg else remoteCfg

            val libJson = LibraryRepository.itemsToJson(lib.items).toString()
            val tombsJson = LibraryRepository.tombsToJson(lib.tombs).toString()
            val catJson = JSONArray(lib.categories).toString()
            val cfgJson = cfg.toJson()
            val unchanged = snap.exists() && snap.getString("library") == libJson && snap.getString("tombs") == tombsJson &&
                snap.getString("categories") == catJson && snap.getString("settings") == cfgJson && snap.getLong("settingsAt") == cfg.at
            if (!unchanged) {
                tx.set(ref, mapOf(
                    "schema" to 1L, "library" to libJson, "tombs" to tombsJson, "categories" to catJson,
                    "settings" to cfgJson, "settingsAt" to cfg.at, "updatedAt" to System.currentTimeMillis()
                ))
            }
            Merged(Data(lib, cfg), remoteWon && remoteCfg.at > local.cfg.at)
        }.await()

        // ---- تطبيق النتيجة محليًا
        val libRepo = LibraryRepository(app)
        // إن كان الدمج سيغيّر مكتبة هذا الجهاز نحفظ نسخة قبله للتراجع
        if (LibraryRepository.itemsToJson(libRepo.items()).toString() != LibraryRepository.itemsToJson(merged.data.lib.items).toString()) {
            libRepo.savePreSync()
        }
        libRepo.replaceAll(merged.data.lib)
        val repo = ProjectRepository(app)
        var warn = 0
        var langToApply = ""
        if (merged.remoteSettingsWon) {
            val cfg = merged.data.cfg
            var token: String? = null
            val pass = repo.syncPassphrase()
            if (repo.tokenSync() && cfg.tokenEnc.isNotEmpty()) {
                if (pass.isEmpty()) warn = R.string.sync_warn_decrypt
                else {
                    token = Crypto.decrypt(cfg.tokenEnc, pass)
                    if (token == null) warn = R.string.sync_warn_decrypt
                }
            }
            repo.applyRemoteSettings(cfg.user, cfg.name, cfg.email, cfg.testAds, cfg.lang, token, cfg.at)
            langToApply = cfg.lang
        }
        repo.setLastSync(System.currentTimeMillis())
        withContext(Dispatchers.Main) {
            warningRes = warn
            if (langToApply.isNotEmpty() &&
                !AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith(langToApply)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langToApply))
            }
            changeCount++
        }
    }
}
