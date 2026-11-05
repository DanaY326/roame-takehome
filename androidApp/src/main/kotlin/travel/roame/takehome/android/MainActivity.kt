package travel.roame.takehome.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import travel.roame.takehome.android.ui.FlightExplorerApp
import travel.roame.takehome.android.ui.FlightExplorerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val explorer = (application as FlightExplorerApplication).explorer
        setContent {
            FlightExplorerTheme {
                FlightExplorerApp(explorer)
            }
        }
    }
}
