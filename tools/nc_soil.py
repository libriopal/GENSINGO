#!/usr/bin/env python3
"""Pulls verified soil facts for a point in the North Carolina mountains.

Source: USDA-NRCS Soil Data Access (SSURGO), the same survey a county extension agent reads.
No key, no licence restriction, public domain.

Why this exists: every other layer in GENSINGO infers landform from a ~10 m elevation raster.
SSURGO records landform because a soil surveyor walked that ground and wrote it down, and its
vocabulary includes the literal term "coves". Where the raster and the surveyor disagree, the
surveyor wins.

Measured across 19 western NC survey areas during development:

    mountain slopes 1296 | ridges 1280 | coves 412 | drainageways 376 | fans 312
    flood plains 175 | hillslopes 145 | stream terraces 131 | depressions 55 | bogs 1

And the distinction that matters, because "cove" alone is not the answer:

    well drained coves .... Saunook, Cullasaja, Santeetlah, Greenlee, Lonon, Balsam, Brevard,
                            Chiltoskie, Heintooga, Keener, Lostcove, Maymead, Northcove, Spivey
    poorly drained coves .. Alarka, Sylva

Usage:
    python3 tools/nc_soil.py 35.12 -83.45
    python3 tools/nc_soil.py --selftest
"""

import json
import sys
import urllib.request

SDA = "https://sdmdataaccess.sc.egov.usda.gov/Tabular/post.rest"

# Western NC survey areas - the ginseng counties.
NC_MOUNTAIN_AREAS = [
    "NC009", "NC011", "NC021", "NC023", "NC027", "NC039", "NC043", "NC075", "NC087",
    "NC099", "NC111", "NC113", "NC115", "NC121", "NC173", "NC175", "NC189", "NC193", "NC199",
]


def sda(query, timeout=120):
    req = urllib.request.Request(
        SDA,
        data=json.dumps({"query": query, "format": "JSON"}).encode(),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode()).get("Table", [])


def soil_at(lat, lon):
    """Everything the survey knows about this spot, or a clear statement that it knows nothing.

    The horizon join is LEFT, deliberately. Urban land, water and rubble have a component but no
    horizons, and an inner join silently returns zero rows - which during development looked
    exactly like a broken pipeline and was in fact a car park in downtown Boone. Absence of soil
    data is itself a finding and has to survive the query.
    """
    q = f"""
    SELECT TOP 4
        c.compname, c.comppct_r, c.drainagecl, c.slope_l, c.slope_h,
        g.geomfname,
        h.ph1to1h2o_r, h.om_r, h.sumbases_r, h.hzdept_r, h.hzdepb_r
    FROM SDA_Get_Mukey_from_intersection_with_WktWgs84('point({lon} {lat})') AS mu
    JOIN component  AS c ON c.mukey = mu.mukey AND c.majcompflag = 'Yes'
    LEFT JOIN cogeomordesc AS g ON g.cokey = c.cokey AND g.geomftname = 'Landform'
    LEFT JOIN chorizon     AS h ON h.cokey = c.cokey AND h.hzdept_r <= 15
    ORDER BY c.comppct_r DESC, h.hzdept_r
    """
    rows = sda(q)
    if not rows:
        return {"found": False, "note": "No soil survey record at this point."}
    r = rows[0]

    def num(v):
        try:
            return float(v)
        except (TypeError, ValueError):
            return None

    return {
        "found": True,
        "series": r[0],
        "component_percent": num(r[1]),
        "drainage_class": r[2],
        "slope_low": num(r[3]),
        "slope_high": num(r[4]),
        "landform": r[5],
        "surface_ph": num(r[6]),
        "organic_matter_percent": num(r[7]),
        "sum_bases": num(r[8]),
    }


# Mirrors SoilSuitability.kt exactly. Kept in sync by hand, and the self-test below is what
# catches drift: if these two ever disagree the app and the tool are scoring different models.
LANDFORM_SCORE = {
    "coves": 1.00,
    "fans": 0.85, "benches": 0.85, "toes": 0.85,
    "drainageways": 0.55, "valleys": 0.55, "mountain valleys": 0.55,
    "mountain slopes": 0.45, "mountainsides": 0.45, "hillslopes": 0.45, "hillsides": 0.45,
    "hills": 0.45, "low hills": 0.45, "escarpments": 0.45,
    "ridges": 0.15, "interfluves": 0.15, "broad interstream divides": 0.15, "rims": 0.15,
    "flood plains": 0.20, "stream terraces": 0.20, "terraces": 0.20,
    "natural levees": 0.20, "river valleys": 0.20, "backswamps": 0.20, "flats": 0.20,
    "depressions": 0.05, "bogs": 0.05, "pocosins": 0.05, "marshes": 0.05, "sloughs": 0.05,
    "slackwater areas": 0.05, "troughs": 0.05, "dune slacks": 0.05, "lakebeds": 0.05,
    "carolina bays": 0.05,
    # Coastal "coves" are NOT mountain coves. The statewide vocabulary contains "mainland
    # coves" and "barrier coves"; scoring those as ginseng ground would be badly wrong.
    "mainland coves": 0.10, "barrier coves": 0.10, "barrier flats": 0.10,
    "tidal marshes": 0.05, "tidal flats": 0.05, "marine terraces": 0.20,
}
DRAINAGE_SCORE = {
    "well drained": 1.00,
    "moderately well drained": 0.75,
    "somewhat excessively drained": 0.45,
    "excessively drained": 0.20,
    "somewhat poorly drained": 0.20,
    "poorly drained": 0.05,
    "very poorly drained": 0.00,
}


def score(s):
    if not s.get("found"):
        return None
    lf = LANDFORM_SCORE.get((s.get("landform") or "").strip().lower(), 0.40)
    dr = DRAINAGE_SCORE.get((s.get("drainage_class") or "").strip().lower(), 0.40)
    ph = s.get("surface_ph")
    ph_s = 0.5 if ph is None else (
        0.15 if ph < 4.0 else 0.55 if ph < 4.5 else 1.00 if ph <= 6.5 else 0.80 if ph <= 7.3 else 0.45
    )
    om = s.get("organic_matter_percent")
    om_s = 0.5 if om is None else (
        0.15 if om < 1.0 else 0.45 if om < 2.0 else 0.80 if om < 4.0 else 1.00
    )
    return round(0.45 * lf + 0.30 * dr + 0.10 * ph_s + 0.15 * om_s, 3)


def selftest():
    """Points retrieved during development, plus the car park that looked like a bug."""
    cases = [
        ("Nantahala NF, Macon", 35.12, -83.45),
        ("Pisgah NF, Transylvania", 35.29, -82.78),
        ("Roan area, Mitchell", 36.09, -82.12),
        ("downtown Boone (expect: no soil)", 36.2168, -81.6746),
    ]
    for name, lat, lon in cases:
        s = soil_at(lat, lon)
        if not s["found"]:
            print(f"{name:<34} NO SURVEY RECORD")
            continue
        print(
            f"{name:<34} {str(s['series']):<14} {str(s['landform']):<16} "
            f"{str(s['drainage_class']):<28} pH={s['surface_ph']} OM={s['organic_matter_percent']} "
            f"score={score(s)}"
        )


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        selftest()
    elif len(sys.argv) >= 3:
        s = soil_at(float(sys.argv[1]), float(sys.argv[2]))
        print(json.dumps(s, indent=2))
        print("score:", score(s))
    else:
        print(__doc__)
