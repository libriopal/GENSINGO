# 114 upgrades in the shape of "bedrock geology, because calcium is the predictor no layer sees"

(The file was drafted aiming at 150 and contains 114. Counted rather than padded: an earlier
round of this project shipped a list headed "156 proposals" that held 141, and the auditor
opened with it - a document that cannot count itself is asking to be trusted with 50-state legal
booleans. Six of the 114 are already built, and inflating the rest to reach a round number would
make the list worse, not longer.)

The pattern that makes an idea belong on this list: **name a predictor the current layers are
blind to, then name a public dataset that measures it.** An idea without a reachable dataset is
a wish. Each item below is tagged:

`[DONE]` built · `[HAVE]` data confirmed reachable · `[FIND]` needs a source check · `[NO]` ruled out

## A. Parent material and geology — the calcium axis
A1 `[DONE]` SSURGO `copm.pmorigin` as the rock type per polygon.
A2 `[DONE]` SSURGO `copm.pmkind` — colluvium vs residuum vs alluvium.
A3 `[DONE]` Amphibolite and hornblende gneiss as the base-rich signal.
A4 `[DONE]` Carbonates (marble, dolomite, limestone) as the strongest calcium signal.
A5 `[DONE]` Generic "igneous and metamorphic rock" scored NEUTRAL, never good.
A6 `[DONE]` Arkose classified after it appeared under the Whiteoak coves.
A7 `[HAVE]` USGS SGMC WMS for bedrock where SSURGO origin is generic.
A8 `[FIND]` NC Geological Survey 1:250k units, finer than SGMC.
A9 `[FIND]` Mapped amphibolite BODIES as polygons, not just soil attribution.
A10 `[FIND]` Ultramafic/serpentine bodies — flag as extreme, not good.
A11 `[HAVE]` SSURGO `sumbases_r` (sum of bases) where populated.
A12 `[HAVE]` SSURGO `cec7_r` cation exchange capacity.
A13 `[FIND]` `caco3_r` calcium carbonate where present.
A14 `[FIND]` Base saturation `bs82_r`.
A15 `[FIND]` Ca:Mg ratio, which the ramps literature flags.
A16 `[FIND]` Geologic contacts — rich flora often sits on the contact.
A17 `[NO]` Invent calcium from elevation. No.

## B. Canopy, forest type and light
B1 `[HAVE]` USFS EDW ForestType MapServer — responds.
B2 `[FIND]` Cove hardwood vs oak-hickory vs pine classification.
B3 `[FIND]` NLCD canopy cover percent — target 75-90% shade.
B4 `[FIND]` LANDFIRE existing vegetation type.
B5 `[FIND]` Tulip poplar presence (the ONE validated indicator).
B6 `[FIND]` Rhododendron/mountain laurel heath — a NEGATIVE signal.
B7 `[FIND]` Stand age; ginseng wants mature forest.
B8 `[FIND]` Recent clearcut/harvest polygons — rule out.
B9 `[FIND]` Hemlock decline areas (canopy opening).
B10 `[FIND]` Leaf-off vs leaf-on NDVI to infer conifer fraction.
B11 `[FIND]` LiDAR canopy height model where NC has it.
B12 `[FIND]` Gap fraction from LiDAR returns.

## C. Terrain, sharper
C1 `[DONE]` Horn slope/aspect at 3.86 m.
C2 `[DONE]` McCune & Keon heat load folded NE-SW.
C3 `[DONE]` Ring TPI over ~120 m.
C4 `[DONE]` Non-monotonic slope and position bands.
C5 `[FIND]` NC QL2 LiDAR DEM at 1 m — real 1 m, not interpolated 3 m.
C6 `[FIND]` Horizon/cast-shadow from the DEM (tile-edge problem to solve).
C7 `[FIND]` Solar insolation model, hourly integrated over the season.
C8 `[FIND]` Cold-air drainage / frost pockets.
C9 `[FIND]` Multi-scale TPI (30 m, 120 m, 500 m) to separate bench from cove.
C10 `[FIND]` Profile vs plan curvature separately.
C11 `[FIND]` Slope length and steepness factor.
C12 `[FIND]` Distance to stream from NHD.
C13 `[FIND]` Height above nearest drainage.
C14 `[FIND]` Terrain roughness — rubble and boulder fields.
C15 `[NO]` True catchment TWI. Unbounded upslope extent, not tractable.

## D. Hydrology and moisture
D1 `[FIND]` NHD flowlines — distance to perennial vs intermittent.
D2 `[FIND]` NHD waterbodies, to exclude.
D3 `[FIND]` Springs and seeps.
D4 `[FIND]` SSURGO depth to water table.
D5 `[FIND]` SSURGO flooding frequency — exclude frequent.
D6 `[FIND]` SSURGO ponding frequency.
D7 `[HAVE]` Drainage class, already used.
D8 `[FIND]` Available water capacity.
D9 `[FIND]` Hydric soil rating — exclude.

## E. Soil, deeper
E1 `[HAVE]` Organic matter, already used.
E2 `[HAVE]` Surface pH, already used and corrected off cultivation targets.
E3 `[FIND]` Depth to bedrock — shallow soils dry out.
E4 `[FIND]` Rock fragment volume.
E5 `[FIND]` Taxonomic order — Inceptisol vs Ultisol.
E6 `[FIND]` Umbric/mollic epipedon — thick dark surface, the cove signature.
E7 `[FIND]` Soil temperature regime (mesic vs frigid).
E8 `[FIND]` Slope range from the component record as a sanity cross-check.
E9 `[FIND]` Component percentage as a confidence weight, not a score.
E10 `[FIND]` Minor components — a 15% cove inside a 60% ridge unit.

## F. Climate and microclimate
F1 `[FIND]` PRISM annual precipitation.
F2 `[FIND]` PRISM mean temperature — the Jochum 5 °C sensitivity.
F3 `[FIND]` Growing degree days.
F4 `[FIND]` Frost-free period.
F5 `[FIND]` Fog/cloud immersion belt in the Southern Appalachians.
F6 `[FIND]` Elevation bands tuned to Haywood rather than statewide.

## G. Legal and access — factual columns, not gates
G1 `[DONE]` USFS national forest boundaries.
G2 `[HAVE]` NPS boundaries, verified both directions.
G3 `[FIND]` NC game lands.
G4 `[FIND]` State parks and forests.
G5 `[FIND]` Blue Ridge Parkway corridor.
G6 `[FIND]` Wilderness areas.
G7 `[FIND]` NC parcel data — who owns it.
G8 `[FIND]` Conservation easements.
G9 `[FIND]` PAD-US as the catch-all.
G10 `[FIND]` Roads and trails for access.
G11 `[FIND]` Distance from road — a proxy for dig pressure.
G12 `[FIND]` Gated forest roads.

## H. Pressure and depletion
H1 `[FIND]` Distance from trailhead/road as a poaching-pressure proxy.
H2 `[FIND]` Slope as an access deterrent — steep ground is less dug.
H3 `[NO]` County harvest tiers. Ecological fallacy; ruled out twice.
H4 `[FIND]` Deer density where a real dataset exists.
H5 `[NO]` Invent deer pressure. No dataset, no invention.

## I. Output and field use
I1 `[DONE]` GPX waypoints.
I2 `[DONE]` GeoJSON.
I3 `[DONE]` 150 m minimum separation so picks are places not pixels.
I4 `[FIND]` Offline bundle into the APK.
I5 `[FIND]` PNG heatmap render.
I6 `[FIND]` 3D relief render of a cove.
I7 `[FIND]` Contour overlay.
I8 `[FIND]` Walk order — nearest-neighbour route.
I9 `[FIND]` Distance and bearing from current position.
I10 `[FIND]` "Why this spot" per waypoint.
I11 `[FIND]` Print-friendly card.
I12 `[FIND]` Elevation profile along a walking line.

## J. Verification and honesty
J1 `[DONE]` Negative controls on every scorer.
J2 `[DONE]` Coastal-cove guard.
J3 `[DONE]` Axis-order control after the fail-open bug.
J4 `[DONE]` Resolution stated honestly (~10 m source under 3.9 m cells).
J5 `[DONE]` Saturation caught — a layer that only pins at 1.00 discriminates nothing.
J6 `[FIND]` Sensitivity sweep on the new weights.
J7 `[FIND]` Cross-check soil landform against terrain curvature; report disagreement.
J8 `[FIND]` Hold-out test on ground the user confirms.
J9 `[FIND]` Log what the user finds, to build the only real validation set.
J10 `[NO]` Claim validation without occurrence data.

## K. Things ruled out, and why
K1 `[NO]` Refit weights to the ramps Maxent study — cross-species transfer.
K2 `[NO]` County tiers — wrong unit of inference, and it maps where people dig.
K3 `[NO]` Three.js as a witness — same author, same priors.
K4 `[NO]` Encrypting the patch DB — data-loss risk exceeds the gain.
K5 `[NO]` Panic wipe — destroys the only copy.
K6 `[NO]` Presenting any of this as validated prediction.
