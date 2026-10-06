# Wave F.1 — make the map work in the field, and a field chat

**The owner's directive (2026-10-06), verbatim:** *"Search works, terrain roads and other layers
don't, finish by continuing work iteratively executing eincol.md and implement and update app until
I reil and most optimal tool for real field testing use is actually true. Making the apk produced
next from many drafted canidate ideas and improvements until a actually real field test app fo
personal use when a human user used the android app on there phone hiking physically to actual
locations. Add a persistent libe llm chat with suggestions and memory for when online."* Then,
mid-wave: *"Skip test produce app now"*.

**Diagnosis from the report.** Search worked, so the network and the geocoder did; terrain, roads
and the other layers did not. Reading the code against that: the 3D square was built only after a
first GPS fix, and a search did not count as one, so indoors, or with a slow fix, the map waited for
ever with nothing on it. Renderer failures (shader compile, link, framebuffer) went to the log only,
so a phone that could not draw showed a blank map with no reason. And nothing on the phone could
say whether the elevation tiles, the map snapshot or the roads overlay had worked.

| ID | Candidate (owner-directed) | As built |
|---|---|---|
| J57 | **The map no longer waits for a GPS fix** | `FieldViewModel.placeChosen`: a search, a "Show"/"Go here", moving the map by hand, or 15 s without a fix (`FIX_WAIT_MS`) all let the square build where the camera is; the status reads "Waiting for a GPS fix… (or search a place)" until then |
| J58 | **Failures say so on screen** | `TerrainGlRenderer.fail()`: every drawing failure sets the error, logs it and calls `onError`; the view shows "3D drawing failed on this phone: … (Layers → Field diagnostics)" |
| J59 | **Field diagnostics** | `perf/FieldDiagnostics.kt`: GPS state, elevation tiles from storage / downloaded / failed (with the last failure's HTTP code or exception class), the square's build times, the GPU's name and version, frames drawn, the drape and roads results, the self-test. A Layers section shows it in monospace with **Copy diagnostics**, so the owner can paste it back. No coordinate, tile id, URL or search text is ever put in it |
| J60 | **Map renderer self-test** | `MapDrape.selfTest`: renders a tiny inline map (a red line on a clear background, no network) with the same snapshotter the drape and roads use, and reports OK with the line's pixel count and whether the background came out clear, or OPAQUE (the roads overlay would then be refused), or FAILED with the reason. **Layers → Test map rendering** |
| J61 | **Field chat, persistent, with memory** | `research/FieldChat.kt`, `FieldChatRepository.kt`, `ClaudeChatClient.kt`, `GeminiChatClient.kt`: a chat sheet (the Forum button) using the Research settings' provider, key and consent. The conversation is kept on the phone (`field_chat.json`, last 400 turns) and the last 30 go with each message. Each message carries the app's field context: date, the coarse area (the same ~10 km cell the research layer uses), today's legal state and season, the top suggestions with aspect, distance and direction, saved places, the target, the finds count — never a coordinate. Claude: cached system prompt, web search, a strict `remember` tool for durable notes (county, habits, what worked), refusals reported as such. Gemini: Google Search grounding, notes as a final `NOTE:` line. Notes are listed in the sheet and can be deleted one by one; Clear wipes the conversation. Replies that name "#3" get **Show #3** / **Go #3** buttons. Quick prompts: where first today, a 3-hour walk through the best spots, what to check at #1, season and rules, companion plants, what next after an empty spot. Offline, without consent, or without a key, the sheet says which and sends nothing |
| — | Debug-only network config | `res/xml/network_security_config.xml`: cleartext off; user certificates trusted only in debuggable builds (`debug-overrides`), so the emulator can be tested through a proxy without weakening the release |

## Verification

| Check | Result |
|---|---|
| Compile, build | `compileDebugKotlin`, `assembleField -Pgensingo.abis=arm64-v8a` exit 0; manifest not debuggable; delivered as `gensingo-field-arm64-F1.apk` (20,999,139 bytes) |
| Tests | 449 debug unit tests pass, 0 failed; new `FieldChatTest` (store round trip and bounds, a broken file is an empty conversation, a request never starts with the model's turn, note cleaning, context only on the newest message, Gemini notes split off, the system prompt's privacy and legality lines) |
| Device | **Not run, by the owner's instruction** ("Skip test produce app now"). Attempted first: an online gate on the AVD failed on this container's proxy (TLS tunnels closed after ~13 s); an offline gate with 221 seeded elevation tiles (`gateF1b.sh`) was ready and was not run |
| Not run | lint, mutants |

**Open:** the owner's Field diagnostics text from the phone is the next input: it says which of
the elevation tiles, the 3D renderer, the drape or the roads overlay fails there. Whether this
MapLibre build clears a snapshot to transparent is now answered on the phone by the self-test.

**Drafted this iteration (owner directive, five ideas):** J62 a "field kit" start screen for a
hike: download the area (elevation, map, roads) for the day in one tap with a progress bar and a
size, before leaving signal; J63 chat answers on the ground: a place the chat recommends becomes a
pin and a "Go here" (only from the app's own ranked suggestions, never a coordinate the model made
up); J64 a hike log: each outing's track, finds, notes and chat summary as one entry, reviewable at
home; J65 low-signal chat: queue a question when offline and send it when signal returns, with a
notification; J66 a visit checklist at each suggestion (canopy, slope, companions seen, soil,
moisture) that feeds the find record and the next ranking.
