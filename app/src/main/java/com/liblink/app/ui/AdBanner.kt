package com.liblink.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.liblink.app.LibLinkApp
import com.liblink.app.R
import com.startapp.sdk.ads.banner.Banner

// بانر Start.io — لا يظهر إلا عند ضبط App ID
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (LibLinkApp.adsEnabled(stringResource(R.string.start_io_app_id))) {
        AndroidView(modifier = modifier, factory = { Banner(it) })
    }
}
