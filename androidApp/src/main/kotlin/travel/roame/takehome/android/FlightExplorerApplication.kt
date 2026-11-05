package travel.roame.takehome.android

import android.app.Application
import org.osmdroid.config.Configuration
import travel.roame.takehome.presentation.FlightExplorer
import travel.roame.takehome.presentation.FlightExplorerFactory
import java.io.File

class FlightExplorerApplication : Application() {

    /** App-scoped: survives configuration changes and is shared by every screen. */
    lateinit var explorer: FlightExplorer
        private set

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            load(this@FlightExplorerApplication, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        explorer = FlightExplorerFactory.create(
            storageDirectory = File(filesDir, "flight-explorer").absolutePath,
            baseUrl = API_BASE_URL,
        )
        explorer.start()
    }

    companion object {
        /**
         * The Android emulator reaches the host machine at 10.0.2.2. On a physical device, run
         * `adb reverse tcp:8080 tcp:8080` and change this to http://localhost:8080.
         */
        const val API_BASE_URL = "http://10.0.2.2:8080"
    }
}
