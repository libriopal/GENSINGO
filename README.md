# Ginseng Scout: Offline-First Habitat Mapping & Scouting

A production-ready Android application engineered for high-precision ginseng (*Panax quinquefolius*) scouting in remote, offline environments. The app integrates advanced geospatial rendering, edge AI predictive modeling, and strict regulatory compliance enforcement.

## 🚀 Key Features

- **Offline-First Geospatial Engine**: Powered by **MapLibre Native**, rendering high-resolution USGS topography and contour data from local **MBTiles** files.
- **Predictive Habitat Modeling**: Real-time "suitability scores" generated on-device using **ONNX Runtime Mobile** and quantized machine learning models.
- **High-Accuracy Tracking**: A robust **Foreground Service** provides continuous GPS breadcrumbs with battery-efficient batch-writing to a local **Room** database.
- **Regulatory Compliance**: Hardcoded enforcement of NC harvesting seasons and automated **Geofencing** alerts for prohibited areas (National Parks, etc.).
- **Secure Data Vault**: Uses **Jetpack Security** (`EncryptedSharedPreferences` and `EncryptedFile`) to protect sensitive landowner permission metadata and scanned documents.

---

## 🛠 Technical Architecture

The application follows **Clean Architecture** principles to ensure modularity and testability:

- **Presentation Layer**: Built with **Jetpack Compose** and **Hilt** for dependency injection.
- **Domain Layer**: Contains pure business logic, including `UseCases` for habitat scoring and regulatory checks.
- **Data Layer**: Manimates data persistence via **Room/SQLite** with **R-Tree spatial indexing** for high-performance proximity queries.
- **Edge AI**: Implements an asynchronous inference pipeline using **ONNX Runtime**.

---

## 📋 Setup & Deployment

### Prerequisites
- **Android Studio** (Ladybug or newer recommended).
- **Android SDK 34** (UpsideDownCake).
- **Physical Android Device** (Recommended for testing GPS and Camera/Scan features).

### 1. Building the Project
1. Clone the repository.
2. Open the project in Android Studio.
3. Sync Gradle to download dependencies (MapLibre, ONNX, Room, Hilt, etc.).
4. Build the project: `./gradlew assembleDebug`

### 2. Required Assets (Crucial)
For the app to function in its intended offline capacity, you must place the following files in the `app/src/main/assets/` directory:

- **Maps**: An `.mbtiles` file containing your desired vector/raster tiles (e.g., `usgs_topo.mbtiles`).
- **AI Model**: A quantized `.onnx` model file (e.g., `habitat_model_int8.onnx`).

### 3. Running Tests
The project includes a comprehensive test suite:
- **Unit Tests**: Run via `./gradlew test` (verifies Domain logic, AI extraction, and Batching).
- **Instrumented Tests**: Run via `./gradlew connectedAndroidTest` (requires an emulator or device).

---

## 🛡️ Compliance & Safety
This application is designed with a **"Safety Shield"** approach:
- **Geofencing**: Uses a Ray Casting algorithm to detect entry into prohibited polygons.
- **Seasonal Enforcement**: Automatically restricts data logging and alerts users during illegal harvesting windows.
- **Data Privacy**: All sensitive files are encrypted at rest using AES-256.

---

## 🛠 Development Notes
- **Spatial Indexing**: We utilize the SQLite R-Tree module. When adding new spatial entities, ensure the `@RawQuery` in the DAO is optimized for bounding-box searches.
- **Batching Logic**: The `LocationBatcher` uses a `Mutex` to prevent race conditions during the "Emergency Flush" triggered by service termination.
