package com.example.nearworkthesis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.nearworkthesis.core.ui.theme.NearworkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val skipSplash = intent?.getBooleanExtra(EXTRA_SKIP_SPLASH_FOR_UI_TESTS, false) == true
        setContent {
            NearworkTheme {
                NearworkApp(skipSplash = skipSplash)
            }
        }
    }

    companion object {
        const val EXTRA_SKIP_SPLASH_FOR_UI_TESTS = "skip_splash_for_ui_tests"
    }
}
