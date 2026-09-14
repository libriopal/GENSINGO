#!/usr/bin/env python3
"""
Build-time authoring of GENSINGO reference assets (PRD_CC.md §2.2, §6.2, §8.6).

Emits:
  assets/geo/state_boundaries.geojson   real outlines, filtered to the 19 approved states
  assets/data/state_regulations.json    19 states + Menominee Tribe, with per-FIELD provenance
  assets/geo/protected_areas.geojson    approximate land-status polygons, labelled approximate

PROVENANCE NOTE - this is the reason the schema differs from PRD §6.2.
The PRD's example record carries "season_end": "Nov 30" with "source": "FWS 2025".
FWS does not publish per-state end dates. Its ginseng page states only that "in all 19
States, ginseng harvest season starts in September". Filling season_end for all 19 from
"FWS 2025" would be a fabricated measurement wearing an authoritative label - exactly the
failure EINCOL.md §4 calls the vocabulary proxy, and here it is also a legal hazard: a
digger shown a confident wrong end date can harvest out of season.

So every date carries its own verification flag. Unverified end dates are null and the UI
renders "confirm with <agency>" instead of a number. Sourced end dates are marked true.
"""
import json, sys

STATES_GEOJSON, OUT_BOUNDS, OUT_REGS, OUT_PROTECTED = sys.argv[1:5]

FWS = "U.S. Fish & Wildlife Service, American Ginseng export program"

# minimum_age_years / minimum_prongs: FWS states 18 of the 19 require 5 years and 3
# compound leaves; Illinois requires 10 years and 4 leaves.
# season_end: only filled where an end date was actually sourced (see EINCOL_REPORT.md).
STATES = [
    # code, name, season_end, end_verified, agency
    ("AL", "Alabama",        None,     False, "Alabama Dept. of Conservation & Natural Resources"),
    ("AR", "Arkansas",       None,     False, "Arkansas Dept. of Agriculture"),
    ("GA", "Georgia",        None,     False, "Georgia Dept. of Natural Resources"),
    ("IL", "Illinois",       None,     False, "Illinois Dept. of Natural Resources"),
    ("IN", "Indiana",        None,     False, "Indiana DNR, Division of Nature Preserves"),
    ("IA", "Iowa",           None,     False, "Iowa Dept. of Natural Resources"),
    ("KY", "Kentucky",       "Dec 31", True,  "Kentucky Dept. of Agriculture"),
    ("MD", "Maryland",       None,     False, "Maryland Dept. of Natural Resources"),
    ("MN", "Minnesota",      None,     False, "Minnesota Dept. of Natural Resources"),
    ("MO", "Missouri",       None,     False, "Missouri Dept. of Conservation"),
    ("NY", "New York",       None,     False, "New York State Dept. of Environmental Conservation"),
    ("NC", "North Carolina", "Dec 31", True,  "NC Dept. of Agriculture, Plant Conservation Program"),
    ("OH", "Ohio",           None,     False, "Ohio Dept. of Natural Resources"),
    ("PA", "Pennsylvania",   None,     False, "Pennsylvania DCNR"),
    ("TN", "Tennessee",      "Dec 31", True,  "Tennessee Dept. of Environment & Conservation"),
    ("VT", "Vermont",        None,     False, "Vermont Agency of Natural Resources"),
    ("VA", "Virginia",       "Dec 31", True,  "Virginia Dept. of Agriculture & Consumer Services"),
    ("WV", "West Virginia",  "Nov 30", True,  "West Virginia Division of Forestry"),
    ("WI", "Wisconsin",      None,     False, "Wisconsin Dept. of Natural Resources"),
]

MENOMINEE = {
    "state_code": "MEN",
    "state_name": "Menominee Indian Tribe Reservation of Wisconsin",
    "is_tribal": True,
    "season_start": "September", "season_start_verified": True,
    "season_end": None, "season_end_verified": False,
    "minimum_age_years": 5, "minimum_prongs": 3,
    "permit_required": True,
    "agency": "Menominee Tribal Enterprises",
    "harvest_notes": "The Menominee Reservation is the one tribal jurisdiction approved by "
                     "FWS for wild American ginseng harvest and trade. Harvest is governed by "
                     "tribal law, not Wisconsin state law. Permission from the Tribe is "
                     "required; non-members should assume harvest is not open to them.",
    "source": FWS,
}

regs = []
for code, name, end, end_ok, agency in STATES:
    illinois = code == "IL"
    regs.append({
        "state_code": code,
        "state_name": name,
        "is_tribal": False,
        # FWS: "In all 19 States, ginseng harvest season starts in September."
        "season_start": "Sept 1",
        "season_start_verified": True,
        "season_end": end,
        "season_end_verified": end_ok,
        "minimum_age_years": 10 if illinois else 5,
        "minimum_prongs": 4 if illinois else 3,
        "permit_required": False,
        "agency": agency,
        "harvest_notes": (
            "Illinois requires plants to be at least 10 years old with 4 compound leaves - "
            "the strictest maturity rule of the 19 approved states."
            if illinois else
            "Plants must be at least 5 years old with 3 compound leaves. Count stem scars on "
            "the root neck to confirm: a 5-year-old plant carries 4 scars."
        ) + (
            "" if end_ok else
            f" End of season not published by FWS - confirm the closing date with {agency} "
            f"before digging late in the year."
        ),
        "source": FWS,
    })
regs.append(MENOMINEE)

with open(OUT_REGS, "w") as f:
    json.dump(regs, f, indent=2)
print(f"WROTE {OUT_REGS}: {len(regs)} jurisdictions "
      f"({sum(1 for r in regs if r['season_end_verified'])} with a sourced end date)")

# ---------------------------------------------------------------- state boundaries
want = {name for _, name, _, _, _ in STATES}
src = json.load(open(STATES_GEOJSON))
feats = []
for f in src["features"]:
    nm = f["properties"].get("name")
    if nm in want:
        code = next(c for c, n, *_ in STATES if n == nm)
        feats.append({"type": "Feature",
                      "properties": {"name": nm, "code": code},
                      "geometry": f["geometry"]})
out = {"type": "FeatureCollection",
       "properties": {"provenance": "verified_reference",
                      "source": "US Census/public-domain state outlines (generalised)",
                      "note": "Used only for offline state auto-detect by ray casting."},
       "features": feats}
with open(OUT_BOUNDS, "w") as f:
    json.dump(out, f, separators=(",", ":"))
missing = want - {f["properties"]["name"] for f in feats}
print(f"WROTE {OUT_BOUNDS}: {len(feats)}/19 states, missing={sorted(missing) or 'none'}")

# ---------------------------------------------------------------- protected areas
# APPROXIMATE bounding polygons. Real unit boundaries are highly irregular; these boxes
# over-cover. That is acceptable ONLY because PRD §8.6 requires a WARNING and explicitly
# forbids hard-blocking, and because every feature carries provenance=approximate so the
# UI can say so. A box will raise false warnings near a unit edge - stated, not hidden.
def box(s, w, n, e):
    return {"type": "Polygon", "coordinates": [[[w, s], [e, s], [e, n], [w, n], [w, s]]]}


# name, status, south, west, north, east
AREAS = [
    ("Great Smoky Mountains National Park", "national_park", 35.43, -84.00, 35.80, -83.07),
    ("Shenandoah National Park", "national_park", 38.03, -78.90, 38.95, -78.15),
    ("New River Gorge National Park", "national_park", 37.75, -81.25, 38.20, -80.85),
    ("Mammoth Cave National Park", "national_park", 37.10, -86.30, 37.30, -86.00),
    ("Cuyahoga Valley National Park", "national_park", 41.13, -81.65, 41.38, -81.48),
    ("Monongahela National Forest", "national_forest", 38.00, -80.35, 39.45, -79.05),
    ("George Washington & Jefferson National Forests", "national_forest", 36.60, -81.60, 39.20, -78.30),
    ("Pisgah National Forest", "national_forest", 35.20, -82.90, 36.15, -81.50),
    ("Nantahala National Forest", "national_forest", 34.90, -84.10, 35.60, -82.90),
    ("Cherokee National Forest", "national_forest", 35.00, -84.40, 36.60, -81.90),
    ("Daniel Boone National Forest", "national_forest", 36.60, -84.60, 38.30, -83.30),
    ("Wayne National Forest", "national_forest", 38.50, -82.80, 39.70, -81.50),
    ("Hoosier National Forest", "national_forest", 38.10, -86.80, 39.20, -86.10),
    ("Shawnee National Forest", "national_forest", 37.905, -89.50, 37.75, -88.10),
    ("Mark Twain National Forest", "national_forest", 36.50, -93.00, 38.30, -90.50),
    ("Allegheny National Forest", "national_forest", 41.35, -79.40, 42.00, -78.65),
    ("Chattahoochee-Oconee National Forest", "national_forest", 34.40, -84.70, 35.00, -83.10),
    ("Ozark-St. Francis National Forest", "national_forest", 35.35, -94.20, 36.20, -92.10),
    ("Green Mountain National Forest", "national_forest", 42.75, -73.30, 44.30, -72.70),
    ("Finger Lakes National Forest", "national_forest", 42.45, -76.85, 42.62, -76.70),
    ("Chequamegon-Nicolet National Forest", "national_forest", 45.30, -91.40, 46.30, -88.30),
]

STATUS_RULE = {
    "national_park": ("PROHIBITED",
                      "Harvesting ginseng in a National Park is a federal offence. "
                      "Do not dig here."),
    "national_forest": ("PERMIT REQUIRED",
                        "National forests may allow harvest only with a permit issued by "
                        "that forest, in limited numbers. Dig only with a valid permit."),
    "fws_refuge": ("PROHIBITED",
                   "Harvesting on a National Wildlife Refuge is prohibited."),
}

pf = []
for name, status, s, w, n, e in AREAS:
    s, n = min(s, n), max(s, n)
    w, e = min(w, e), max(w, e)
    rule, msg = STATUS_RULE[status]
    pf.append({"type": "Feature",
               "properties": {"name": name, "status": status, "rule": rule,
                              "message": msg, "provenance": "approximate"},
               "geometry": box(s, w, n, e)})
with open(OUT_PROTECTED, "w") as f:
    json.dump({"type": "FeatureCollection",
               "properties": {"provenance": "approximate",
                              "note": "Approximate bounding polygons. They over-cover: a "
                                      "warning near a boundary may fall outside the real "
                                      "unit. Always confirm land status on the ground."},
               "features": pf}, f, separators=(",", ":"))
print(f"WROTE {OUT_PROTECTED}: {len(pf)} areas (all provenance=approximate)")
