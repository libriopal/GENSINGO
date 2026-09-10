# Design Blueprint: Ginseng Scouting & Habitat Mapping App

## 1. Architecture Overview
The application will follow **Clean Architecture** principles combined with **MVVM (Model-View-ViewModel)** to ensure high testability and separation of concerns, which is critical for an offline-first system with multiple complex modules (AI, Geospatial, Location).

### Layers:
- **Presentation Layer (UI)**: Jetpack Compose for the UI, ViewModels for state management.
- **Domain Layer (Business Logic)**: UseCases for specific actions (e.g., `LogGinsengPatchUseCase`, `CalculateHabitatSuitabilityUseCase`, `CheckHarvestingComplianceUseCase`).
- **Data Layer (Data Sources)**: 
    - **Repositories**: Single source of truth for the UI.
    - **Local Data Sources**: Room (SQLite) for structured data, MBTiles for maps, EncryptedSharedPreferences for security.
    - **Remote Data Sources**: (Not currently required for offline-first, but architected for future sync).

---

## 2. Directory Structure

```text
app/src/main/java/com/discomplemented/ginseng/
├── core/                       # Cross-cutting concerns
│   ├── common/                 # Extensions, Constants, Result types
│   ├── di/                     # Dependency Injection (Hilt/Koin)
│   ├── security/               # Encryption utilities, Scoped Storage
│   └── util/                   # Geo/Math utilities (Haversine, etc.)
├── data/                       # Data Layer
│   ├── local/
│   │   ├── database/           # Room DB, DAOs, Entities
│   │   ├── mbtiles/            # MBTiles/TileProvider logic
│   │   ├── preferences/       # EncryptedSharedPreferences
│   │   └── repository/         # Repository implementations
│   └── domain/                 # Domain Models (POJOs)
├── domain/                     # Domain Layer
│   ├── model/                  # Pure domain entities
│   ├── repository/             # Repository interfaces
│   └── usecase/                # Business logic UseCases
├── di/                         # DI Modules
├── location/                   # Location Module
│   ├── service/                # Foreground Service implementation
│   ├── tracker/                # High-accuracy GPS logic
│   └── batcher/                # In-memory buffer & Batch writing logic
├── mapping/                    # Mapping Module
│   ├── compose/                # MapLibre + Compose wrappers
│   ├── layers/                 # Heatmap & Contour overlay logic
│   └── provider/               # Custom Tile/Source providers
├── ai/                         # Edge AI Module
│   ├── inference/              # ONNX Runtime Manager
│   └── feature/                # Feature extraction (Sensor $\rightarrow$ Tensor)
├── compliance/                 # Regulatory Module
│   ├── geofence/               # Protected area logic
│   └── seasonal/               # Harvesting season enforcement
└── ui/                         # Presentation Layer
    ├── components/             # Reusable UI components
    ├── navigation/             # Compose Navigation
    └── screens/                # Feature-specific screens (Map, Log, Settings)
```

---

## 3. Data Model (Room Entities & Spatial Indexing)

### `TrackNode`
Stores high-frequency GPS breadcrumbs.
- `id`: UUID (Primary Key)
- `timestamp`: Long (Indexed)
- `latitude`: Double
- `longitude`: Double
- `altitude`: Float
- `accuracy`: Float

### `GinsengPatch`
Stores verified finds.
- `id`: UUID (Primary Key)
- `latitude`: Double
- `longitude`: Double
- `timestamp`: Long
- `confidence`: Float
- `metadata`: String (JSON representation of slope, canopy, maturity, etc.)
- **Spatial Index**: SQLite R-Tree on `(latitude, longitude)` for efficient bounding-box queries.

### `LandownerPermission`
Stores legal access metadata.
- `id`: UUID (Primary Key)
- `ownerName`: String
- `imageUri`: String (Scoped Storage path)
- `expiryDate`: Long
- `isVerified`: Boolean
- `locationPolygon`: String (GeoJSON representation of permitted area)

---

## 4. Module Architectures

### A. Location Tracking (`location` module)
- **`GpsForegroundService`**: Maintains a `Notification` to prevent process death.
- **`LocationBatcher`**: 
    - Collects `TrackNode` objects in an in-memory `MutableList`.
    - **Trigger 1 (Size)**: If list size $\ge$ 50, perform a Room transaction.
    - **Trigger 2 (Time)**: Every 60 seconds, perform a Room transaction.
    - **Trigger 3 (Emergency)**: On `onTaskRemoved()` or `onDestroy()`, perform an immediate final flush.

### B. Mapping Layer (`mapping` module)
- **`MapLibreComposeView`**: A `AndroidView` wrapper for `MapView`.
- **`OfflineTileProvider`**: A custom implementation that opens the `.mbtiles` file and serves tiles via a local SQLite query.
- **`OverlayManager`**: Coordinates the rendering of the `HeatmapSource` (for patches) and the `TerrainSource` (for contours).

### C. Edge AI Engine (`ai` module)
- **`HabitatInferenceManager`**:
    - Loads the `.onnx` model from assets.
    - **Method**: `getSuitabilityScore(features: FeatureSet): Float`
    - **FeatureSet**: A data class containing current GPS, elevation, canopy cover, and moisture.
    - **Concurrency**: Runs inference on a dedicated `Dispatchers.Default` thread to avoid blocking the UI or Location service.

### D. Compliance & Security (`compliance` module)
- **`RegulatoryGuard`**: A utility used by `UseCases` to verify if an action (e.g., `SavePatch`) is permitted based on the current `LocalDate`.
- **`GeofenceMonitor`**: Uses `GeofencingClient` or a manual distance check (Haversine) against locally stored protected area GeoJSONs to trigger `UserNotifications`.

---

## 5. Implementation Roadmap

1.  **Milestone 1: Foundation**: Gradle config, Room schema, and Base DI.
2.  **Milestone 2: Tracking**: Foreground Service and Batch-writing Repository.
3.  **Milestone 3: Map**: MapLibre integration and MBTiles provider.
4.  **Milestone 4: Intelligence**: ONNX integration and Habitat scoring.
5.  **Milestone 5: Compliance**: Geofencing and Security vault implementation.
