package com.replymate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.replymate.ui.DebugScreen
import com.replymate.ui.PersonaScreen
import com.replymate.ui.SetupScreen
import com.replymate.ui.theme.ReplyMateTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    SETUP("Setup", Icons.Outlined.CheckCircle),
    PERSONA("Persona", Icons.Outlined.Person),
    DEBUG("Debug", Icons.Outlined.Build),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReplyMateTheme { ReplyMateApp() }
        }
    }
}

@Composable
private fun ReplyMateApp() {
    val app = LocalContext.current.applicationContext as ReplyMateApplication
    var selected by rememberSaveable { mutableStateOf(Tab.SETUP) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                Tab.SETUP -> SetupScreen(app.settings, onOpenPersona = { selected = Tab.PERSONA })
                Tab.PERSONA -> PersonaScreen(app.settings, app.appScope)
                Tab.DEBUG -> DebugScreen()
            }
        }
    }
}
