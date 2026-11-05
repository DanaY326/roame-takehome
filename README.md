# Flight Explorer

A flight list and flight map, inspired by Roame, built twice: SwiftUI on iOS and Jetpack Compose on Android. Both apps sit on one shared Kotlin Multiplatform layer that handles networking, persistence, search, filtering, map aggregation and presentation state.

```
api/server.py          Local API (Python stdlib): GET /flights
shared/                KMP module used by both apps
  model/               Flight, SavedFlight, CachedFlights (kotlinx.serialization)
  domain/              FlightFilter + FlightFiltering, MapAggregation, FilterOptions, dates/formatting
  data/                KtorFlightApi, FileDocumentStore (atomic JSON files), FlightRepository
  presentation/        FlightExplorer (intents in, ExplorerState out), FlightExplorerFactory
iosApp/                SwiftUI app (MapKit)
androidApp/            Compose app (Material 3, osmdroid / OpenStreetMap)
fixtures/flights.json  1,000 flights
```

## Requirements

Same as the starter: Xcode 26.4+ with an iOS simulator runtime, and Android Studio with SDK 37 / build-tools 37.0.0. Gradle provisions JDK 25 itself. The first build needs internet access.

## 1. Run the API

```sh
python3 api/server.py
```

This serves `fixtures/flights.json` at `http://127.0.0.1:8080/flights`. It uses only the standard library, so there is nothing to install. Optional flags for exercising other states:

| Command | Behaviour |
| --- | --- |
| `python3 api/server.py --mode empty` | 200 with `{"flights": []}` (successful empty response) |
| `python3 api/server.py --mode error` | 500 (failed request) |
| `python3 api/server.py --delay 3` | Waits 3 s before responding (shows loading states) |

To test offline behaviour, stop the server (Ctrl+C).

## 2. Run the apps

**iOS:** open `iosApp/RoameStarter.xcodeproj`, choose the `RoameStarter` scheme and an iPhone simulator, then press Run. The app calls `http://localhost:8080`; App Transport Security allows local networking only.

**Android:** open the repository root in Android Studio and run `androidApp` on an emulator. The app calls `http://10.0.2.2:8080`, the emulator's alias for the host machine. Cleartext traffic is allowed only to that host and to localhost. On a physical device, run `adb reverse tcp:8080 tcp:8080` and change `API_BASE_URL` in `FlightExplorerApplication.kt` to `http://localhost:8080`.

The Android map uses OpenStreetMap tiles through osmdroid, so it needs no API key.

## 3. Run the tests

```sh
./gradlew :shared:jvmTest
```

The shared module has a plain `jvm()` target only so these tests can run on any machine, without an emulator or simulator. On a Mac with a simulator runtime installed, `./gradlew :shared:iosSimulatorArm64Test` runs the same common tests on Kotlin/Native.

| Test class | Covers |
| --- | --- |
| `FlightFilteringTest` | Origin and destination search (code or city, case and whitespace ignored), inclusive date and points boundaries, AND across filters, OR within programs, empty filters |
| `MapAggregationTest` | Grouping by destination, flight count, lowest points |
| `FormattingTest` | Date maths and formatting, and which filters count as active (the full date range does not) |
| `FlightApiTest` | Decoding the envelope, empty versus HTTP error versus malformed body |
| `FileDocumentStoreTest` | Persistence round trip across instances, atomic replace, corrupt-file quarantine |
| `FlightRepositoryTest` | Cache survives a restart; a failed refresh keeps data and marks it stale; error with no cache; save upserts without duplicates; failed save or remove changes nothing; saved flights outlive the API |
| `FlightExplorerTest` | Results follow the latest filter, selection is kept or cleared, retry, stale state, saved list independent of filters |
| `FixtureIntegrationTest` (JVM) | The real 1,000-flight fixture through the real decoder and rules |

See [SUBMISSION_NOTES.md](SUBMISSION_NOTES.md) for architecture, assumptions, tradeoffs and limitations.
