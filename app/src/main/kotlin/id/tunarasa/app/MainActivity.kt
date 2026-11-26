package id.tunarasa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import id.tunarasa.app.navigation.TunarasaNavHost
import id.tunarasa.app.ui.theme.TunarasaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TunarasaTheme {
                TunarasaNavHost()
            }
        }
    }
}
