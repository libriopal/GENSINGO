# Verification Plan: Ginseng Scouting App

This document outlines the rigorous verification procedures required to certify the implementation of the core modules of the Ginseng Scouting application.

## 1. Data Persistence & Spatial Integrity
**Objective**: Ensure zero data loss and high-performance spatial querying.

- **[Test] Room Entity Integrity**: Verify that `TrackNode`, `GinsengPatch`, and `LandownerPermission` are correctly persisted and retrieved.
- **[Test] Spatial Query Performance**: Execute bounding-box queries using `@RawQuery` (R-Tree) and verify accuracy and execution time.
- **[Test] Batching & Recovery (Critical)**:
    - Simulate a process death during active tracking.
    - Verify that the `LocationBatcher`'s "Emergency Flush" mechanism successfully commits the in-memory buffer to the database upon service termination.
- **[Test] Type Conversion**: Verify that `Gson` converters correctly handle `UUID` and `Map<String, String>` for metadata.

## 2. Location Tracking & Service Resilience
**Objective**: Guarantee continuous, battery-efficient background tracking.

- **[Test] Foreground Service Lifecycle**: Verify the service correctly transitions to the foreground with a visible notification and complies with Android 14+ `FOREGROUND_SERVICE_LOCATION` requirements.
- **[Test] High-Accuracy Stream**: Verify that `LocationTracker` provides a continuous flow of high-accuracy updates via `FusedLocationProviderClient`.
- **[Test] Battery Optimization**: Verify that the batching interval (60s) and size (50) are respected to minimize disk I/O.

## 3. Geospatial Mapping & UI
**Objective**: Ensure smooth rendering and lifecycle-safe map interactions.

- **[Test] MapLibre Lifecycle**: Verify that the `MapLibreComposeView` correctly manages `onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, and `onDestroy` within the Compose `DisposableEffect`.
- **[Test] Overlay Responsiveness**: Verify that updating the `TrackHistory` or `GinsengPatches` state triggers an immediate, smooth update of the `MapOverlayManager` layers without re-rendering the entire map view.
- **[Test] MBTiles Loading**: Verify that the `LocalMBTilesProvider` can successfully fetch and decode tiles from a local SQLite source.

## 4. Edge AI (Inference Pipeline)
**Objective**: Verify high-performance, offline-first intelligence.

- **[Test] ONNX Lifecycle**: Verify that `HabitatInferenceManager` correctly initializes the `OrtEnvironment` and `OrtSession` from assets and releases them on `release()`.
- **[Test] Inference Latency**: Benchmark the `predictSuitability` call. Target: `< 100ms` on standard mobile hardware.
- **[Test] Feature Extraction Accuracy**: Verify that the `FeatureExtractor` correctly maps raw sensor inputs to the `FeatureSet` schema.

## 5. Dependency Injection & Architecture
**Objective**: Ensure the system is correctly wired and follows Clean Architecture.

- **[Test] Hilt Wiring**: Verify that all Repositories, DAOs, and UseCases are correctly provided by Hilt and can be injected into ViewModels and Services.
- **[Test] Layer Isolation**: Verify that the `Domain` layer has zero dependencies on `Data` or `UI` layers.
