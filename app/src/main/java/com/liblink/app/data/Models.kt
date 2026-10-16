package com.liblink.app.data

// وضع الحفظ: منظم تلقائي أو يدوي
enum class StorageMode { ORGANIZED, MANUAL }

data class Project(
    val id: String,
    val name: String,
    val path: String,       // مجلد المشروع المستقل
    val remoteUrl: String,
    val mode: StorageMode
)

data class Credentials(
    val user: String,
    val token: String,
    val authorName: String,
    val authorEmail: String
)
