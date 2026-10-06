# Wave P.1 — polish, and Google-Maps/Earth-like mapping

**The owner's directive (2026-10-06), verbatim:** *"Continue, adjust your goal to polish the app , also
add basic Google maps and goog earth like functionality for more information and comprehensive
mapping with address search , rendering settings and skip testing produce a apk update asap"*

The owner exercised §9 twice here: a geocoding destination for address search (asked for by name),
and **skip testing** (the wave ships with compile and assemble only; no unit-test run, no mutants,
no device run). Both are recorded, not hidden: every claim below is "built", none is "verified".

| ID | Candidate (owner-directed) | As built |
|---|---|---|
| J38 | **Address, place and coordinate search** | `ui/map/PlaceSearch.kt`: typed coordinates parsed on the phone (decimal, N/S/E/W, degrees-minutes-seconds; offline); else Android's platform `Geocoder` (no key, no dependency); else OpenStreetMap's public Nominatim (one request per search, a real User-Agent, results credited). Only the typed words leave the phone; the position is never sent; neither query nor result is logged. A search bar over the map (`MainScreen.SearchBar`); one result flies there, several are listed; the place is pinned on the ground (`Terrain3DView`) until cleared |
| J39 | **Map types on the 3D ground** | `Basemap`: Streets (the dark OpenFreeMap style, saved offline as before), **Satellite** and **Topo** (USGS The National Map, public domain, the destination the app used before M.1, as one-layer raster styles), Terrain only. `MapDrape.render` takes a style JSON and a zoom cap (15 for the rasters). The battery mode still drapes nothing |
| J40 | **Tap the ground: what is here** (Earth-style) | A tap (one finger, within the touch slop, under 400 ms) finds the ground point under it (`CameraMath.groundAt`, the pan's own ray march) and opens a card: coordinates, elevation in ft and m, slope and the way it faces (the analysis's Horn stencil, `Scene.slopeAspectAt`), the terrain score with its band, labelled a model estimate (`Scene.scoreAt`), distance and bearing from you; **Directions** opens Google Maps (or the browser) with the point as the destination, on the owner's tap only; **Copy** puts the coordinates on the clipboard |
| J41 | **Rendering settings** | Relief exaggeration 1× / 1.5× / 2× / 3× (`Scene.exaggeration` replaces the constant in the camera maths, the markers and the legend; a change rebuilds the square); the legend can be hidden (tap it) and brought back (the "Legend" chip, or the Layers sheet). Map type, relief and legend persist (`SettingsStore`) |
| — | polish | The status chip stops at two lines; the Layers sheet groups the map type, relief and legend under the layer switches |

## Verification (what was and was not run)

| Check | Result |
|---|---|
| Compile | `compileDebugKotlin` exit 0; `compileDebugUnitTestKotlin` exit 0 (the existing tests still compile) |
| Build | `assembleField` (arm64) exit 0; delivered as `gensingo-field-arm64-P1.apk` |
| Tests, mutants, lint, device | **not run: skipped by the owner** ("skip testing produce a apk update asap"). Open: unit tests for `PlaceSearch.parseCoordinates`, `Scene.scoreAt`/`slopeAspectAt`, `CameraMath.groundAt`; a device run of search, the three map types, the card and the relief setting; M.1's three device-found fixes are still unchecked on screen |

**Known risks, stated:** in still mode MapLibre fails a whole snapshot on one failed tile, so a
USGS tile outage shows "needs a connection" instead of a partial map; the platform geocoder's
quality depends on the phone (on phones without Google services it is absent and Nominatim
answers); the tap card's terrain score is empty until the habitat colour has finished.

**Drafted this iteration (owner directive, five ideas):** J42 measure distance and area on the
ground (terrain-following); J43 saved places to check (a searched or tapped point kept, with its own
way-to chip); J44 Satellite saved offline with "Save 10 miles" (the USGS imagery as a second offline
region); J45 public-land boundaries (PAD-US, public domain: where walking and permits apply; a new
dataset, blocked(owner)); J46 hours of direct sun at a tapped point from the terrain's horizon.
