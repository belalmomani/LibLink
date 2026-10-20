package com.liblink.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.liblink.app.LibLinkApp
import com.liblink.app.R
import com.startapp.sdk.ads.banner.Banner

// بانر Start.io — لا يظهر إلا عند ضبط App ID، ويحجز ارتفاعًا ثابتًا ليُقاس صحيحًا داخل Compose
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (LibLinkApp.adsEnabled(stringResource(R.string.start_io_app_id))) {
        Box(modifier.fillMaxWidth().heightIn(min = 50.dp), contentAlignment = Alignment.Center) {
            AndroidView(factory = { Banner(it) })
        }
    }
}
