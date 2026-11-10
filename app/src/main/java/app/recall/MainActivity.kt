package app.recall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.recall.ui.RecallRoot
import app.recall.ui.theme.RecallTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RecallTheme {
                RecallRoot()
            }
        }
    }
}
