#!/usr/bin/env python3
"""Ranks real ground in the North Carolina mountains for wild ginseng, from the soil survey.

WHAT THIS IS
    Not a heatmap. A ranked list of actual polygons, with coordinates you can walk to, each one
    a map unit that a USDA soil surveyor delineated and described on the ground.

WHY IT BEATS A TERRAIN MODEL
    A terrain model infers landform from a ~10 m elevation raster and calls a curvature minimum a
    "cove". SSURGO records landform because a person stood there. Its vocabulary contains the
    literal term "coves" - 412 components of it across 19 western NC survey areas.

    The part that matters is that "cove" alone is not the answer. Drainage varies WITHIN coves,
    and the difference decides whether ginseng grows or rots:
        well drained coves .... Saunook, Cullasaja, Santeetlah, Greenlee, Lonon, Spivey, Toecane
        poorly drained coves .. Alarka, Sylva

SOURCES (both public domain, no key, no licence restriction)
    Geometry:  USDA-NRCS Soil Data Access WFS  (SDMWGS84Geographic.wfs, MapunitPoly)
    Attributes: USDA-NRCS Soil Data Access tabular REST

WHAT IT IS NOT
    Not validated against ginseng occurrence. No occurrence dataset exists at usable precision -
    iNaturalist and GBIF deliberately obscure this species' coordinates to deter poaching. This
    ranks ground by habitat evidence a surveyor recorded. It does not know where ginseng is.

    It also does not know who owns the ground. Check that yourself before you walk.

USAGE
    python3 tools/nc_prospect.py --bbox -83.95 35.30 -83.85 35.40 --top 15
    python3 tools/nc_prospect.py --around 35.34 -83.89 --radius-km 5 --gpx out.gpx
"""

import argparse
import json
import re
import sys
import urllib.parse
import urllib.request

WFS = "https://SDMDataAccess.nrcs.usda.gov/Spatial/SDMWGS84Geographic.wfs"
SDA = "https://sdmdataaccess.sc.egov.usda.gov/Tabular/post.rest"
UA = {"User-Agent": "GENSINGO-prospect/1.0 (personal field tool)"}

# ---------------------------------------------------------------- scoring (mirrors the app)

LANDFORM = {
    "coves": (1.00, "COVE"),
    "fans": (0.85, "colluvial fan"), "benches": (0.85, "bench"), "toes": (0.85, "toe slope"),
    "drainageways": (0.55, "drainageway"), "valleys": (0.55, "valley"),
    "mountain valleys": (0.55, "mountain valley"),
    "mountain slopes": (0.45, "mountain slope"), "mountainsides": (0.45, "mountainside"),
    "hillslopes": (0.45, "hillslope"), "hillsides": (0.45, "hillside"),
    "hills": (0.45, "hill"), "low hills": (0.45, "low hill"), "escarpments": (0.45, "escarpment"),
    "ridges": (0.15, "ridge"), "interfluves": (0.15, "interfluve"),
    "broad interstream divides": (0.15, "divide"), "rims": (0.15, "rim"),
    "flood plains": (0.20, "flood plain"), "stream terraces": (0.20, "stream terrace"),
    "terraces": (0.20, "terrace"), "natural levees": (0.20, "levee"),
    "river valleys": (0.20, "river valley"), "backswamps": (0.20, "backswamp"),
    "flats": (0.20, "flat"),
    "depressions": (0.05, "depression"), "bogs": (0.05, "bog"), "pocosins": (0.05, "pocosin"),
    # Coastal landforms wearing mountain words. "mainland coves" and "barrier coves" are real
    # values in the statewide vocabulary and are NOT ginseng ground.
    "mainland coves": (0.10, "coastal cove"), "barrier coves": (0.10, "coastal cove"),
    "marine terraces": (0.20, "marine terrace"), "tidal marshes": (0.05, "tidal marsh"),
}
DRAINAGE = {
    "well drained": 1.00, "moderately well drained": 0.75,
    "somewhat excessively drained": 0.45, "excessively drained": 0.20,
    "somewhat poorly drained": 0.20, "poorly drained": 0.05, "very poorly drained": 0.00,
}
W_LANDFORM, W_DRAINAGE, W_PH, W_OM = 0.45, 0.30, 0.10, 0.15


def ph_score(ph):
    # Real NC mountain forest measures pH 4.6-5.3. Cultivation guides say 5.5-6.0; scoring
    # against that target would mark genuinely good wild ground as wrong.
    if ph is None:
        return 0.5
    if ph < 4.0:
        return 0.15
    if ph < 4.5:
        return 0.55
    if ph <= 6.5:
        return 1.00
    return 0.80 if ph <= 7.3 else 0.45


def om_score(om):
    if om is None:
        return 0.5
    if om < 1.0:
        return 0.15
    if om < 2.0:
        return 0.45
    return 0.80 if om < 4.0 else 1.00


def score_unit(u):
    lf, _ = LANDFORM.get((u.get("landform") or "").strip().lower(), (0.40, "unknown"))
    dr = DRAINAGE.get((u.get("drainage") or "").strip().lower(), 0.40)
    return round(
        W_LANDFORM * lf + W_DRAINAGE * dr + W_PH * ph_score(u.get("ph")) + W_OM * om_score(u.get("om")),
        3,
    )


# ---------------------------------------------------------------- data access

def sda(query, timeout=180):
    req = urllib.request.Request(
        SDA, data=json.dumps({"query": query, "format": "JSON"}).encode(),
        headers={"Content-Type": "application/json", **UA},
    )
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode()).get("Table", []) or []


def fetch_polygons(west, south, east, north, timeout=300):
    """Map unit polygons in a bbox, as {mukey: [rings]}. Geometry comes back as GML."""
    flt = (
        "<Filter><BBOX><PropertyName>Geometry</PropertyName>"
        f'<Box srsName="EPSG:4326"><coordinates>{west},{south} {east},{north}</coordinates></Box>'
        "</BBOX></Filter>"
    )
    url = (
        f"{WFS}?SERVICE=WFS&VERSION=1.1.0&REQUEST=GetFeature&TYPENAME=MapunitPoly"
        f"&FILTER={urllib.parse.quote(flt)}"
    )
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout) as r:
        xml = r.read().decode("utf-8", "replace")

    out = {}
    # Each <gml:featureMember> carries one polygon and one mukey.
    for chunk in xml.split("featureMember")[1:]:
        mk = re.search(r"<ms:mukey>(\d+)</ms:mukey>", chunk)
        if not mk:
            continue
        for coords in re.findall(r"<gml:coordinates>(.*?)</gml:coordinates>", chunk, re.S):
            pts = []
            for pair in coords.split():
                try:
                    a, b = pair.split(",")[:2]
                except ValueError:
                    continue
                # AXIS ORDER. WFS 1.1.0 with srsName EPSG:4326 returns LAT,LON - not lon,lat.
                # This is the OGC axis-order trap and it bit hard: read the wrong way round,
                # every centroid came out as (-83.9, 35.3) reversed, the National Forest lookup
                # queried a point in the Indian Ocean, found nothing, and the tool reported
                # "not National Forest" for ground that is squarely inside the Nantahala.
                # A legal check that fails OPEN is the worst possible direction to be wrong in.
                lat_v, lon_v = float(a), float(b)
                pts.append((lon_v, lat_v))
            if len(pts) >= 3:
                out.setdefault(mk.group(1), []).append(pts)
    return out


def fetch_attributes(mukeys, timeout=240):
    """Landform, drainage, pH and organic matter for the dominant component of each map unit."""
    attrs = {}
    keys = list(mukeys)
    for i in range(0, len(keys), 180):
        batch = ",".join(f"'{k}'" for k in keys[i:i + 180])
        rows = sda(f"""
            SELECT c.mukey, c.compname, c.comppct_r, c.drainagecl, g.geomfname,
                   h.ph1to1h2o_r, h.om_r, m.muname
            FROM component c
            JOIN mapunit m ON m.mukey = c.mukey
            LEFT JOIN cogeomordesc g ON g.cokey = c.cokey AND g.geomftname = 'Landform'
            LEFT JOIN chorizon h ON h.cokey = c.cokey AND h.hzdept_r <= 15
            WHERE c.mukey IN ({batch}) AND c.majcompflag = 'Yes'
            ORDER BY c.mukey, c.comppct_r DESC
        """, timeout=timeout)
        for r in rows:
            mk = r[0]
            if mk in attrs:          # keep the dominant component only
                continue

            def num(v):
                try:
                    return float(v)
                except (TypeError, ValueError):
                    return None

            attrs[mk] = {
                "series": r[1], "comppct": num(r[2]), "drainage": r[3], "landform": r[4],
                "ph": num(r[5]), "om": num(r[6]), "muname": r[7],
            }
    return attrs


# ---------------------------------------------------------------- land status

USFS_Q = ("https://apps.fs.usda.gov/arcx/rest/services/EDW/"
          "EDW_ForestSystemBoundaries_01/MapServer/0/query")


def land_status(lat, lon, timeout=60):
    """What is this ground, legally?

    Right now this answers ONE question well - is it National Forest - because that is the
    endpoint that actually responds, and because it is the trap this tool walks you into by
    default. Good ginseng soil in western NC is overwhelmingly on National Forest: the first run
    of this tool ranked twelve coves around Santeetlah and every one of them was inside the
    Nantahala.

    Harvest of wild ginseng is PROHIBITED in most national forests, and permitted only in
    certain ones with a US Forest Service permit obtained in advance. So a high score on NF land
    is not an invitation, it is a phone call to the district office.

    What this does NOT yet cover, and you must check yourself: NC state game lands, state parks
    and forests, the Blue Ridge Parkway, Great Smoky Mountains National Park (all prohibited),
    and private property (written, dated landowner permission required in NC, carried on you,
    valid 180 days - digging without it with intent to steal is a felony).

    Absence of a warning here is NOT permission.
    """
    params = urllib.parse.urlencode({
        "f": "json", "geometryType": "esriGeometryPoint", "inSR": "4326",
        "spatialRel": "esriSpatialRelIntersects", "returnGeometry": "false",
        "outFields": "FORESTNAME",
        "geometry": json.dumps({"x": lon, "y": lat, "spatialReference": {"wkid": 4326}}),
    })
    try:
        req = urllib.request.Request(f"{USFS_Q}?{params}", headers=UA)
        with urllib.request.urlopen(req, timeout=timeout) as r:
            d = json.loads(r.read().decode())
        feats = d.get("features") or []
        if feats:
            return {"national_forest": feats[0]["attributes"].get("forestname"),
                    "flag": "NATIONAL FOREST - permit required or prohibited"}
        return {"national_forest": None, "flag": "not national forest - ownership UNKNOWN"}
    except Exception as e:
        return {"national_forest": None, "flag": f"land status lookup failed: {str(e)[:60]}"}


# ---------------------------------------------------------------- geometry helpers

def ring_area_centroid(pts):
    """Planar centroid and |area| in square degrees. Good enough to rank and to place a waypoint."""
    a = cx = cy = 0.0
    n = len(pts)
    for i in range(n):
        x0, y0 = pts[i]
        x1, y1 = pts[(i + 1) % n]
        cr = x0 * y1 - x1 * y0
        a += cr
        cx += (x0 + x1) * cr
        cy += (y0 + y1) * cr
    if abs(a) < 1e-14:
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
        return 0.0, (sum(xs) / n, sum(ys) / n)
    a *= 0.5
    return abs(a), (cx / (6 * a), cy / (6 * a))


def deg2_to_hectares(area_deg2, lat):
    import math
    m_per_deg_lat = 110_574.0
    m_per_deg_lon = 111_320.0 * math.cos(math.radians(lat))
    return area_deg2 * m_per_deg_lat * m_per_deg_lon / 10_000.0


# ---------------------------------------------------------------- output

def write_gpx(path, rows):
    esc = lambda s: (s or "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    with open(path, "w") as f:
        f.write('<?xml version="1.0" encoding="UTF-8"?>\n')
        f.write('<gpx version="1.1" creator="GENSINGO nc_prospect" '
                'xmlns="http://www.topografix.com/GPX/1/1">\n')
        for i, r in enumerate(rows, 1):
            f.write(f'  <wpt lat="{r["lat"]:.6f}" lon="{r["lon"]:.6f}">\n')
            f.write(f'    <name>{i:02d} {esc(r["series"])} {r["score"]:.2f}</name>\n')
            f.write(f'    <desc>{esc(r["landform_label"])}; {esc(r["drainage"])}; '
                    f'{r["hectares"]:.1f} ha; {esc(r["muname"])}</desc>\n')
            f.write("  </wpt>\n")
        f.write("</gpx>\n")


def write_geojson(path, rows):
    feats = [{
        "type": "Feature",
        "geometry": {"type": "Polygon", "coordinates": [[[x, y] for x, y in r["ring"]]]},
        "properties": {k: v for k, v in r.items() if k != "ring"},
    } for r in rows]
    json.dump({"type": "FeatureCollection", "features": feats}, open(path, "w"))


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--bbox", nargs=4, type=float, metavar=("W", "S", "E", "N"))
    ap.add_argument("--around", nargs=2, type=float, metavar=("LAT", "LON"))
    ap.add_argument("--radius-km", type=float, default=4.0)
    ap.add_argument("--top", type=int, default=15)
    ap.add_argument("--min-hectares", type=float, default=1.0)
    ap.add_argument("--gpx"), ap.add_argument("--geojson")
    a = ap.parse_args()

    if a.around:
        import math
        lat, lon = a.around
        dlat = a.radius_km / 110.574
        dlon = a.radius_km / (111.320 * math.cos(math.radians(lat)))
        west, south, east, north = lon - dlon, lat - dlat, lon + dlon, lat + dlat
    elif a.bbox:
        west, south, east, north = a.bbox
    else:
        ap.error("give --bbox or --around")

    print(f"area: {west:.4f},{south:.4f} to {east:.4f},{north:.4f}", file=sys.stderr)
    polys = fetch_polygons(west, south, east, north)
    print(f"map units with geometry: {len(polys)}", file=sys.stderr)
    if not polys:
        print("No soil polygons returned for that area.", file=sys.stderr)
        return
    attrs = fetch_attributes(polys.keys())
    print(f"map units with attributes: {len(attrs)}", file=sys.stderr)

    rows = []
    for mk, rings in polys.items():
        at = attrs.get(mk)
        if not at:
            continue
        sc = score_unit(at)
        lf_label = LANDFORM.get((at.get("landform") or "").strip().lower(), (0.4, "unrecorded"))[1]
        for ring in rings:
            area, (cx, cy) = ring_area_centroid(ring)
            ha = deg2_to_hectares(area, cy)
            if ha < a.min_hectares:
                continue
            rows.append({
                "mukey": mk, "score": sc, "series": at.get("series"),
                "landform": at.get("landform"), "landform_label": lf_label,
                "drainage": at.get("drainage"), "ph": at.get("ph"), "om": at.get("om"),
                "muname": at.get("muname"), "hectares": round(ha, 1),
                "lat": cy, "lon": cx, "ring": ring,
            })

    rows.sort(key=lambda r: (-r["score"], -r["hectares"]))
    top = rows[:a.top]

    # Land status only for what is actually going to be printed - one network call each.
    print(f"checking land status for {len(top)} sites...", file=sys.stderr)
    for r in top:
        st = land_status(r["lat"], r["lon"])
        r["national_forest"] = st["national_forest"]
        r["land_flag"] = st["flag"]

    print(f"\nTop {len(top)} of {len(rows)} delineations >= {a.min_hectares} ha\n")
    print(f"{'#':>2} {'score':>5} {'lat,lon':<22} {'ha':>6}  {'series':<12} "
          f"{'landform':<10} {'drainage':<16} land")
    print("-" * 118)
    for i, r in enumerate(top, 1):
        nf = r.get("national_forest")
        land = "NAT FOREST - permit/prohibited" if nf else "not NF - ownership UNKNOWN"
        print(f"{i:>2} {r['score']:>5.2f} {r['lat']:.5f},{r['lon']:.5f}  {r['hectares']:>6.1f}  "
              f"{str(r['series'])[:12]:<12} {r['landform_label'][:10]:<10} "
              f"{str(r['drainage'])[:16]:<16} {land}")

    on_nf = sum(1 for r in top if r.get("national_forest"))
    if on_nf:
        print(f"\n*** {on_nf} of {len(top)} sites are on NATIONAL FOREST land. ***")
        print("    Ginseng harvest is prohibited in most national forests and permit-only in")
        print("    the rest. Ring the district office before you dig any of these.")

    coves = [r for r in rows if (r["landform"] or "").lower() == "coves"]
    wet_coves = [r for r in coves if DRAINAGE.get((r["drainage"] or "").lower(), 1) <= 0.2]
    print(f"\ncoves in this area: {len(coves)}   of which poorly drained (skip these): "
          f"{len(wet_coves)}")
    print("\nThis ranks HABITAT recorded by a soil surveyor. It does not know where ginseng is.")
    print("Land status covers National Forest ONLY. It does NOT check state game lands, state")
    print("parks or forests, the Blue Ridge Parkway, the Smokies, or private property. In NC you")
    print("need written, dated landowner permission on your person, valid 180 days - digging")
    print("without it with intent to steal is a felony. Absence of a warning here is NOT")
    print("permission.")

    if a.gpx:
        write_gpx(a.gpx, top); print(f"\nwrote {a.gpx}", file=sys.stderr)
    if a.geojson:
        write_geojson(a.geojson, top); print(f"wrote {a.geojson}", file=sys.stderr)


if __name__ == "__main__":
    main()
