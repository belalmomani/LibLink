package com.liblink.app.storage

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.android.apksig.ApkSigner
import com.liblink.app.data.ProjectRepository
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate

data class ApkKey(val pkg: String, val fp: String)

// مفاتيح التوقيع: قراءة بصمة APK والتطبيق المثبّت، ومطابقتها، وإعادة التوقيع بمفتاح قديم
object Signing {

    fun fp(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }

    // أول 8 بايتات تكفي للعرض والمقارنة البصرية
    fun short(fp: String) = fp.take(23)

    private fun first(sigs: Array<Signature>?): String? = sigs?.firstOrNull()?.let { fp(it.toByteArray()) }

    // اسم الحزمة وبصمة مفتاح توقيع ملف APK
    fun ofApk(ctx: Context, apk: File): ApkKey? = try {
        val pm = ctx.packageManager
        if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            val f = first(info?.signingInfo?.apkContentsSigners)
            if (info != null && f != null) ApkKey(info.packageName, f) else null
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            val f = first(info?.signatures)
            if (info != null && f != null) ApkKey(info.packageName, f) else null
        }
    } catch (_: Exception) { null }

    // بصمة مفتاح التطبيق المثبّت على هذا الجهاز (إن كان مثبتًا)
    fun ofInstalled(ctx: Context, pkg: String): String? = try {
        val pm = ctx.packageManager
        if (Build.VERSION.SDK_INT >= 28) {
            first(pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners)
        } else {
            @Suppress("DEPRECATION")
            first(pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures)
        }
    } catch (_: Exception) { null }

    // المفتاح المرجعي لمقارنة كل النسخ: المحفوظ، وإلا مفتاح التطبيق المثبّت، وإلا أقدم نسخة مسجّلة
    fun reference(ctx: Context, repo: ProjectRepository, store: ReleaseStore, projectId: String, k: ApkKey): String {
        val saved = repo.keyRef(projectId)
        if (saved.isNotBlank()) return saved
        val ref = ofInstalled(ctx, k.pkg)
            ?: store.list(projectId).filter { it.keyFp.isNotBlank() }.minByOrNull { it.number }?.keyFp
            ?: k.fp
        repo.setKeyRef(projectId, ref)
        return ref
    }

    // ملفات المفاتيح الموجودة داخل المشروع
    fun findKeystores(dir: File): List<File> =
        dir.walkTopDown().onEnter { it == dir || it.name !in setOf(".git", "build", ".gradle", "node_modules") }
            .maxDepth(5)
            .filter { it.isFile && it.extension.lowercase() in setOf("jks", "keystore", "p12", "pfx", "ks") }
            .take(20).toList()

    fun loadKeyStore(file: File, pass: String): KeyStore {
        var last: Exception? = null
        for (type in listOf("PKCS12", "BKS", "JKS")) {
            try {
                val ks = KeyStore.getInstance(type)
                file.inputStream().use { ks.load(it, pass.toCharArray()) }
                return ks
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("keystore")
    }

    fun firstAlias(ks: KeyStore): String = ks.aliases().toList().firstOrNull().orEmpty()

    fun keystoreFp(ks: KeyStore, alias: String): String? = ks.getCertificate(alias)?.let { fp(it.encoded) }

    // إعادة توقيع APK بالمفتاح المختار (التوقيعات القديمة تُستبدل)
    fun resign(input: File, output: File, ks: KeyStore, alias: String, keyPass: String) {
        val key = ks.getKey(alias, keyPass.toCharArray()) as? PrivateKey
            ?: throw IllegalStateException("no private key for alias $alias")
        val chain = ks.getCertificateChain(alias).map { it as X509Certificate }
        val cfg = ApkSigner.SignerConfig.Builder("liblink", key, chain).build()
        output.delete()
        ApkSigner.Builder(listOf(cfg))
            .setInputApk(input).setOutputApk(output).setMinSdkVersion(24)
            .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true)
            .build().sign()
    }
}
