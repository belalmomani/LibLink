package com.liblink.app

import android.app.Application
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK

class LibLinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // تهيئة Start.io فقط عند وضع App ID حقيقي
        val id = getString(R.string.start_io_app_id)
        if (adsEnabled(id)) {
            StartAppSDK.init(this, id, false) // false = بدون إعلانات الإرجاع
            StartAppAd.disableSplash()        // تعطيل شاشة البداية الإعلانية
        }
    }

    companion object {
        fun adsEnabled(id: String) = id.isNotBlank() && id != "YOUR_STARTIO_APP_ID"
    }
}
