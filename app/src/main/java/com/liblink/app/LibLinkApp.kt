package com.liblink.app

import android.app.Application
import com.liblink.app.data.ProjectRepository
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK

class LibLinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // تهيئة Start.io فقط عند وضع App ID حقيقي
        val id = getString(R.string.start_io_app_id)
        if (adsEnabled(id)) {
            // الإعلانات التجريبية تؤكد أن الربط يعمل قبل اعتماد التطبيق في Start.io
            val test = ProjectRepository(this).testAds()
            StartAppSDK.setTestAdsEnabled(test)
            StartAppSDK.init(this, id, false) // false = بدون إعلانات الإرجاع
            StartAppSDK.setTestAdsEnabled(test)
            StartAppAd.disableSplash()        // تعطيل شاشة البداية الإعلانية
        }
    }

    companion object {
        fun adsEnabled(id: String) = id.isNotBlank() && id != "YOUR_STARTIO_APP_ID"
    }
}
