# Design Blueprint: Hybrid Macro/Micro Terrain LOD & Renderer Interface

This document defines the architectural interface and synchronization contract for the dual-scale terrain rendering system. The goal is to achieve a seamless transition from MapLibre's macro-tiles to a high-fidelity, custom-rendered micro-mesh.

## 1. Architectural Pattern: Managed Overlay Injection

To avoid the "Split-Brain" problem (where two different renderers fight for the camera and coordinate system), we will treat the **Micro-Mesh** as a managed overlay synchronized to the **MapLibre Projection Matrix**.

### Core Components:
1.  **`MapLibreContext`**: The authoritative source for camera position, zoom, and projection matrices.
2.  **`MicroTerrainRenderer`**: A specialized component that manages the high-resolution 3D mesh, its vertex buffers, and the custom shaders.
3.  **`LODTransitionManager`**: An orchestration layer that monitors the camera's state and manages the lifecycle and alpha-blending of the micro-mesh.

---

## 2. Component Interfaces

### `MicroTerrainRenderer`
Responsible for the "Micro" view.

```kotlin
interface MicroTerrainRenderer {
    /**
     * Loads high-resolution DEM data and builds the vertex/index buffers.
     * Must be called on a background thread.
     */
    suspend fun loadTerrainData(demFile: File, bounds: BoundingBox)

    /**
     * Updates the rendering parameters (e.s. alpha, scale) to facilitate transitions.
     */
    fun updateRenderState(alpha: Float, projectionMatrix: FloatArray)

    /**
     * Renders the mesh into the current GL/WebGPU context.
     * Must be called within the MapLibre frame loop.
     */
    fun onDraw(projectionMatrix: FloatArray, viewMatrix: FloatArray)

    /**
     * Releases all GPU resources.
     */
    fun release()
}
```

### `LODTransitionManager`
Responsible for the "Seamlessness."

```kotlin
interface LODTransitionManager {
    /**
     * Monitors camera movement and triggers the transition logic.
     * @param cameraState The current camera position and zoom.
     */
    fun onCameraChanged(cameraState: CameraState)

    /**
     * Returns the current interpolation factor (0.0 to 1.0) for the micro-mesh alpha.
     * 0.0 = Macro (MapLibre only), 1.0 = Micro (Mesh fully visible).
     */
    val transitionAlpha: Float

    /**
     * Determines if the micro-mesh should be loaded or unloaded based on radius.
     */
    fun shouldLoadMicroTerrain(cameraPosition: LatLng): Boolean
}
```

---

## 3. The Synchronization Contract (The "Bridge")

To prevent visual "popping" and coordinate drift, we will implement the following protocol:

### A. Coordinate Alignment
The `MicroTerrainRenderer` will not use its own coordinate system. Instead, it will:
1.  Accept the `ModelViewProjection` (MVP) matrix directly from the `MapLibre` camera.
2. local-space coordinates in the mesh will be relative to the **center of the Micro-View bounding box** to maintain floating-point precision.
3. The `LODTransitionManager` will calculate the transformation required to align the local mesh origin with the global MapLibre coordinate.

### B. Temporal Alpha-Blending (The "Seamless" Part)
The transition is not a toggle, but a **ramp**.
- **Thresholds**: 
    - `EnterRadius`: The distance at which the micro-mesh begins loading.
    - `ActiveRadius`: The distance where `transitionAlpha` moves from $0.0 \to 1.0$.
    - `ExitRadius`: The distance where the micro-mesh begins fading out.
- **Implementation**: Use a smooth-step function $f(x) = 3x^2 - 2x^3$ to drive the `transitionAlpha` to ensure the fade-in/out feels organic and lacks sudden jumps.

### C. Resource Lifecycle
- **Phase 1 (Macro)**: Only MapLibre is active. `MicroTerrainRenderer` is uninitialized.
- **Phase 2 (Pre-fetch)**: Camera enters `EnterRadius`. `LODTransitionManager` triggers background loading of DEM data via `MicroTerrainRenderer.loadTerrainData()`.
- **Phase 3 (Micro-Active)**: Camera is in `ActiveRadius`. `transitionAlpha` is $1.0$. Mesh is visible and updating every frame.
- **Phase 4 (Fade-out)**: Camera exits `ActiveRadius`. `transitionAlpha` ramps $1.0 \to 0.0$.
- **Phase 5 (Cleanup)**: `transitionAlpha` reaches $0.0$. `MicroTerrainRenderer.release()` is called to clear GPU memory.

---

## 4. Error Handling & Fallbacks

- **Resource Starvation**: If the `MicroTerrainRenderer` fails to load the mesh (e.g., out of memory), the `LODTransitionManager` must catch the error and clamp `transitionAlpha` at $0.0$, effectively staying in the Macro view.
- **Context Loss**: If the GPU context is lost, the system must trigger a full re-initialization of both the MapLibre and Micro components.
