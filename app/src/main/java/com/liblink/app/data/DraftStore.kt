package com.liblink.app.data

import android.content.Context

// مسودات المحرر: تُحفظ تلقائيًا وتُسترجع عند فتح الملف من جديد
class DraftStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("liblink_drafts", Context.MODE_PRIVATE)

    fun get(path: String): String? = prefs.getString(path, null)
    fun put(path: String, text: String) = prefs.edit().putString(path, text).apply()
    fun clear(path: String) = prefs.edit().remove(path).apply()
}
