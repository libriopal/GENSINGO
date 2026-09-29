# Google Play Data safety declaration — GENSINGO

Source of truth for the Play Console **Data safety** form. Every row is a statement about
what the shipped code does, and names where to verify it. Rewritten in Phase 7: the previous
version declared no data sharing at all, listed a camera permission the app no longer has,
and listed three permissions as "not requested" that track recording now needs.

## Summary

| Question | Answer |
|---|---|
| Does the app collect or share any user data? | **Only if the user turns on the research model.** Off by default. See "Research model" |
| Is data encrypted in transit? | Yes. All requests are HTTPS; cleartext traffic is disabled in the manifest |
| Can users request data deletion? | Everything is on the device: delete a find in-app, or uninstall. The app has no server |
| Independent security review? | No |

No account system, no backend, no analytics, advertising or crash-reporting SDK.

## Research model (the one path that can send data, and only by opt-in)

With **Layers → Research model → Send to …** switched on and the user's own API key saved,
a research request is sent to the provider the user picked (Anthropic or Google), billed to
their key. It contains, and only contains:

| Sent | Resolution | Verify |
|---|---|---|
| The grid cell the user is in | 0.1° (~11 km), the cell centre, never the fix | `SuggestionAssembler.coarseRegion`, `PromptPrivacyTest` |
| State name, season line, the state's rule text | From the bundled sourced data | `ResearchRepository.buildRequest` |
| Each candidate's terrain numbers and a distance band | No coordinates; distance banded (e.g. "3-6 km") | `ResearchPrompt.user` |
| Counts of past suggestion outcomes and average factor values of the user's finds | Aggregates only | `MemorySummary.describe` |

**Never sent:** the GPS fix, the recorded track, the location of any find or suggestion,
notes, or photos. A canary test puts a distinctive fix through the real prompt builder and
fails if any of its digits appear (`PromptPrivacyTest`); mutation P2 (send the exact fix)
is killed by it.

Play form: *Approximate location* — collected, **optional** (user-enabled), shared with the
AI provider the user selects, for the app-functionality purpose of research suggestions.

## Permissions and why each is requested

| Permission | Why | Verify |
|---|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Show where you are, record a track when you ask, stamp a find, detect the state's rules | `field/LocationProvider.kt`, `field/TrackService.kt` |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Keep recording a track with the screen off, only after the user taps Track; stoppable from its notification | `field/TrackService.kt` |
| `POST_NOTIFICATIONS` | Show that notification (API 33+) | `MainActivity.kt` |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Map and elevation tiles; the opt-in research request | `ui/map/FieldMap.kt`, `terrain/DemTileStore.kt`, `research/*Client.kt` |

**Deliberately not requested:** `ACCESS_BACKGROUND_LOCATION` (a location service started
from the foreground keeps the while-in-use grant), `CAMERA`, storage and media permissions.

## Tile requests reveal the area being viewed

Map tiles (OpenFreeMap) and elevation tiles (AWS Open Data) are requested by tile address,
so those hosts can infer roughly which area is on screen, as with any map app. No user data
is attached to those requests. After "Save 10 miles around me", the area works with no
requests at all.

## Location data on the device

- Tracks, finds, suggestions and research runs are in the Room database in app-private
  storage. `android:allowBackup="false"` plus `backup_rules.xml` / `data_extraction_rules.xml`
  exclude them from cloud backup and device transfer.
- The recording notification shows distance and mode, never coordinates.
- API keys are encrypted with an AES-GCM key held in the Android Keystore (`research/KeyVault.kt`),
  never compiled into the APK.

## Third-party SDKs

| SDK | Purpose | Network |
|---|---|---|
| MapLibre Android SDK | Map rendering | Tile requests to `tiles.openfreemap.org` |
| Anthropic Java SDK | Opt-in research model (Claude) | `api.anthropic.com`, only with consent and the user's key |
| Play Services Location | GPS fixes (platform GPS is the fallback) | None initiated by this app |
| Room, Jetpack Compose, kotlinx.serialization | Storage and UI | None |
