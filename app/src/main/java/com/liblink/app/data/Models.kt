package com.liblink.app.data

// وضع الحفظ: منظم تلقائي أو يدوي
enum class StorageMode { ORGANIZED, MANUAL }

data class Project(
    val id: String,
    val name: String,
    val path: String,       // مجلد المشروع المستقل
    val remoteUrl: String,
    val mode: StorageMode,
    val updatedAt: Long = 0L,     // آخر نشاط داخل التطبيق
    val pushState: String = ""    // "" أو "ok" أو "failed" (حالة آخر رفع)
)

data class Credentials(
    val user: String,
    val token: String,
    val authorName: String,
    val authorEmail: String
)
