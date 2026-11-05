package travel.roame.takehome.android

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import travel.roame.takehome.android.ui.FlightExplorerApp
import travel.roame.takehome.android.ui.FlightExplorerTheme

class MainActivity : ComponentActivity() {

    private val explorer get() = (application as FlightExplorerApplication).explorer

    /** The first fetch runs before the user can grant access, so fetch again once they do. */
    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) explorer.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FlightExplorerTheme {
                FlightExplorerApp(explorer)
            }
        }
        if (savedInstanceState == null) requestLocalNetworkAccessIfNeeded()
    }

    /**
     * Android 17 blocks traffic to local-network addresses (including the emulator's 10.0.2.2
     * host alias) unless the app holds ACCESS_LOCAL_NETWORK, a runtime permission.
     */
    private fun requestLocalNetworkAccessIfNeeded() {
        if (Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SDK) return
        if (checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) return
        localNetworkPermission.launch(ACCESS_LOCAL_NETWORK)
    }

    private companion object {
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        const val LOCAL_NETWORK_PERMISSION_SDK = 37
    }
}
