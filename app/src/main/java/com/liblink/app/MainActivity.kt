package com.liblink.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liblink.app.sync.SyncManager
import com.liblink.app.ui.LibLinkRoot
import com.liblink.app.ui.LibLinkTheme

class MainActivity : AppCompatActivity() {
    // نص مُشارَك من تطبيق آخر، ومعرّف مشروع من إشعار البناء
    private var shared by mutableStateOf<String?>(null)
    private var openBuild by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent) // لا نعيد المعالجة عند تدوير الشاشة
        setContent {
            LibLinkTheme {
                LibLinkRoot(
                    shared = shared, openBuild = openBuild,
                    onShareConsumed = { shared = null }, onBuildConsumed = { openBuild = null }
                )
            }
        }
    }

    // كل مرة يُفتح التطبيق: جلب ما تغيّر من أجهزتك الأخرى
    override fun onResume() {
        super.onResume()
        SyncManager.requestNow(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_SEND && i.type == "text/plain") {
            val text = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            // أول رابط في النص المُشارَك، وإلا النص كاملًا
            shared = Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', '"') ?: text.trim()
            if (shared.isNullOrBlank()) shared = null
        }
        i.getStringExtra("project_id")?.let { openBuild = it }
    }
}
