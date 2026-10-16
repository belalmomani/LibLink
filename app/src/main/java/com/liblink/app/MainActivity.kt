package com.liblink.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.liblink.app.ui.LibLinkRoot
import com.liblink.app.ui.LibLinkTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LibLinkTheme { LibLinkRoot() }
        }
    }
}
