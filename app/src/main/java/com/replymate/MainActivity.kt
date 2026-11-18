package com.replymate

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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

internal enum class Tab(val label: String, val icon: ImageVector) {
    SETUP("Setup", Icons.Outlined.CheckCircle),
    PERSONA("Persona", Icons.Outlined.Person),
    DEBUG("Debug", Icons.Outlined.Build),
}

class MainActivity : ComponentActivity() {

    /** Tab requested by the overlay panel ("Open settings" / "Open debug"). */
    private val requestedTab = mutableStateOf<Tab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab.value = tabFrom(intent)
        setContent {
            ReplyMateTheme { ReplyMateApp(requestedTab) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFrom(intent)?.let { requestedTab.value = it }
    }

    private fun tabFrom(intent: Intent?): Tab? =
        intent?.getStringExtra(EXTRA_OPEN_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } }

    companion object {
        const val EXTRA_OPEN_TAB = "com.replymate.OPEN_TAB"
        const val TAB_PERSONA = "PERSONA"
        const val TAB_DEBUG = "DEBUG"
    }
}

@Composable
private fun ReplyMateApp(requestedTab: MutableState<Tab?>) {
    val app = LocalContext.current.applicationContext as ReplyMateApplication
    var selected by rememberSaveable { mutableStateOf(requestedTab.value ?: Tab.SETUP) }
    val requested = requestedTab.value
    LaunchedEffect(requested) {
        if (requested != null) {
            selected = requested
            requestedTab.value = null
        }
    }

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
                Tab.PERSONA -> PersonaScreen(app.settings, app.appScope, app.suggester)
                Tab.DEBUG -> DebugScreen()
            }
        }
    }
}
