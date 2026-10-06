# Wave N.1 — logging roads, trails and navigation

**The owner's directive (2026-10-06), verbatim:** *"Add logroads, trials and generaly useful
navigation improvements to the app also"*

| ID | Candidate (owner-directed) | As built |
|---|---|---|
| J47 | **Logging roads, trails and roads on the ground** | `ROADS_STYLE` (`ui/map/MapLayers.kt`): OpenStreetMap transportation lines from the OpenFreeMap vector tiles the streets map already uses (no new destination; the tiles "Save 10 miles" stores serve it offline): logging and forest roads (`highway=track`, class "track") amber dashed, trails (`path`, `footway`, `bridleway`) coral dotted, minor roads white, major roads bold white, each over a dark casing. Rendered as a transparent snapshot at pixel ratio 1 (so it reaches zoom 14, where the tiles carry tracks and trails), refused if it ever comes back mostly opaque (`MapDrape.mostlyClear`), and baked **over** the habitat colour (`TerrainTextures.Ground.overlay`), so the colour never hides a way in or out. A layer in the registry (`SceneLayer.ROADS`, a sheet switch); kept in the battery mode. Legend rows and the OSM credit. No labels: text needs glyphs, and one missing glyph fails a whole snapshot |
| J48 | **"Go here": walking guidance** | From the tap card, a suggestion, or a saved place: a dashed line on the ground from you to the point, a flag on it, and a chip "➜ name: 640 m NE · at 2 o'clock" (`field/Guidance.kt`: the clock is the direction relative to where the phone faces; "Arrived" within 12 m or the fix's accuracy); tap the chip to stop. Works offline |
| J49 | **Compass heading** | `ui/Heading.kt`: the rotation-vector sensor, corrected to true north by the declination at the fix, smoothed on the unit circle, redrawn only past 2° and at most every 150 ms, stopped with the screen; drawn as a cone on your position; a switch in Layers |
| J50 | **Follow me** | A button under "Centre on me": each fix keeps you in the middle (bearing, tilt and zoom left as set; the square is rebuilt as you walk off it); moving the map by hand stops it |
| J51 | **Saved places** | "Save" on the tap card keeps a point (the truck, a trailhead, a patch); a **Places** sheet lists them nearest first with distance and direction, "Go here", "Show" and "Delete"; diamonds with names on the ground. Kept in the app's preferences on the phone only (excluded from every backup) |

## Verification

| Check | Result |
|---|---|
| Compile, build | `compileDebugKotlin`, `assembleField` (arm64) exit 0; delivered as `gensingo-field-arm64-N1.apk` |
| Tests | the existing 437 debug unit tests pass; new `GuidanceTest` (clock across north, arrival rule, heading tip against an independent distance and bearing) and `CoordinateSearchTest` (P.1's parser: decimal, hemispheres, DMS, non-coordinates) pass |
| Not run | lint, mutants, and any device run (the owner asked for the APK quickly). **Open:** whether this MapLibre build clears an overlay snapshot to transparent (the guard refuses it if not, and the layer then shows nothing); the heading cone and the clock on a real phone's compass; follow mode while walking |

**Drafted this iteration (owner directive, five ideas):** J52 road and trail names and forest-road
numbers on the ground (labels, with the glyphs the offline save already holds); J53 a route along
trails and logging roads to the target, then cross-country for the last stretch (the vector tiles'
road graph, then J5's walking cost); J54 an off-route buzz while "Go here" is on (more than 50 m
off the line, phone in the pocket); J55 gates, parking and trailheads from the same tiles; J56
export saved places and tracks as GPX, owner-initiated, for a partner or another GPS.
