package com.cloudbox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cloudbox.app.ui.navigation.NavGraph
import com.cloudbox.app.ui.theme.CloudBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CloudBoxTheme {
                NavGraph()
            }
        }
    }
}
