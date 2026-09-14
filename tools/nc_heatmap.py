#!/usr/bin/env python3
"""Multilayer ginseng heatmap for the North Carolina mountains, at ~3.9 m.

Two independent lines of evidence, combined per cell:

  SOIL   USDA SSURGO. Landform and drainage recorded by a surveyor who walked the ground.
         Its vocabulary contains the literal term "coves". A raster infers a cove from
         curvature; the survey states it. Where they disagree, the surveyor wins.

  TERRAIN AWS Terrarium DEM at zoom 15 = 3.86 m/px at this latitude. Gives aspect, slope,
         heat load and position on slope - the things that vary WITHIN a soil polygon and
         that the soil survey averages away.

Neither alone is enough. A 45-hectare cove polygon is one soil label over ground that contains
both a north-facing bench and a dry south shoulder, and the terrain layer is what separates them.
Conversely, terrain alone calls any curvature minimum a cove, including a rocky draw with no
soil in it.

RESOLUTION, HONESTLY. The tiles are 3.86 m/px. The USGS data beneath them is mostly 1/3
arc-second (~10 m), so structure finer than about 10 m is interpolation, not measurement. Read
this at the scale of "which end of this bench", not "which square metre".

NOT VALIDATED against ginseng occurrence - none exists at usable precision, because iNaturalist
and GBIF deliberately obscure this species. This ranks habitat. It does not know where ginseng is.

USAGE
    python3 tools/nc_heatmap.py --around 35.60 -83.05 --radius-km 3 --top 25 --gpx spots.gpx
    python3 tools/nc_heatmap.py --around 35.60 -83.05 --png heat.png
"""

import argparse
import json
import math
import struct
import sys
import urllib.request
import zlib

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from nc_prospect import (  # noqa: E402
    DRAINAGE, LANDFORM, fetch_attributes, fetch_polygons, land_status, om_score, ph_score,
)

TERRARIUM = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"
UA = {"User-Agent": "GENSINGO-heatmap/1.0 (personal field tool)"}
Z = 15                     # 3.86 m/px at 35.6 N
TILE = 256


# ---------------------------------------------------------------- tiles

def lonlat_to_tile(lon, lat, z=Z):
    n = 1 << z
    x = (lon + 180.0) / 360.0 * n
    r = math.radians(lat)
    y = (1.0 - math.asinh(math.tan(r)) / math.pi) / 2.0 * n
    return x, y


def tile_to_lonlat(x, y, z=Z):
    n = 1 << z
    lon = x / n * 360.0 - 180.0
    lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / n))))
    return lon, lat




def decode_png_rgb(raw):
    if raw[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a png")
    pos = 8
    idat = b""
    w = h = None
    ncomp = 3
    while pos < len(raw):
        ln = struct.unpack(">I", raw[pos:pos + 4])[0]
        typ = raw[pos + 4:pos + 8]
        data = raw[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h, depth, color = struct.unpack(">IIBB", data[:10])
            if depth != 8 or color not in (2, 6):
                raise ValueError(f"unsupported png depth/color {depth}/{color}")
            ncomp = 3 if color == 2 else 4
        elif typ == b"IDAT":
            idat += data
        elif typ == b"IEND":
            break
        pos += 12 + ln
    buf = zlib.decompress(idat)
    stride = w * ncomp
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for row in range(h):
        ft = buf[p]; p += 1
        line = bytearray(buf[p:p + stride]); p += stride
        if ft == 1:
            for i in range(ncomp, stride):
                line[i] = (line[i] + line[i - ncomp]) & 0xFF
        elif ft == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ft == 3:
            for i in range(stride):
                a = line[i - ncomp] if i >= ncomp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ft == 4:
            for i in range(stride):
                a = line[i - ncomp] if i >= ncomp else 0
                c = prev[i - ncomp] if i >= ncomp else 0
                b = prev[i]
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[row * stride:(row + 1) * stride] = line
        prev = line
    return w, h, ncomp, bytes(out)


_tile_cache = {}


def fetch_tile(z, x, y):
    key = (z, x, y)
    if key in _tile_cache:
        return _tile_cache[key]
    url = TERRARIUM.format(z=z, x=x, y=y)
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=90) as r:
            w, h, nc, px = decode_png_rgb(r.read())
    except Exception:
        _tile_cache[key] = None
        return None
    elev = [0.0] * (w * h)
    for i in range(w * h):
        o = i * nc
        elev[i] = (px[o] * 256 + px[o + 1] + px[o + 2] / 256.0) - 32768.0
    _tile_cache[key] = (w, h, elev)
    return _tile_cache[key]


def build_grid(west, south, east, north):
    """One elevation grid covering the bbox, at zoom 15."""
    x0f, y0f = lonlat_to_tile(west, north)
    x1f, y1f = lonlat_to_tile(east, south)
    tx0, ty0, tx1, ty1 = int(x0f), int(y0f), int(x1f), int(y1f)
    nx, ny = tx1 - tx0 + 1, ty1 - ty0 + 1
    if nx * ny > 64:
        raise SystemExit(f"area too large: {nx*ny} tiles at z{Z}. Use a smaller --radius-km.")
    W, H = nx * TILE, ny * TILE
    grid = [0.0] * (W * H)
    got = 0
    for j in range(ny):
        for i in range(nx):
            t = fetch_tile(Z, tx0 + i, ty0 + j)
            if not t:
                continue
            got += 1
            _, _, e = t
            for r in range(TILE):
                dst = (j * TILE + r) * W + i * TILE
                grid[dst:dst + TILE] = e[r * TILE:(r + 1) * TILE]
    return {"grid": grid, "W": W, "H": H, "tx0": tx0, "ty0": ty0, "tiles": got, "want": nx * ny}


def cell_lonlat(g, col, row):
    return tile_to_lonlat(g["tx0"] + col / TILE, g["ty0"] + row / TILE)


# ---------------------------------------------------------------- terrain

def metres_per_px(lat):
    return 40075016.686 * math.cos(math.radians(lat)) / (TILE * (1 << Z))


def slope_aspect(g, col, row, mpp):
    W, H = g["W"], g["H"]
    gr = g["grid"]
    def z(c, r):
        c = min(max(c, 0), W - 1); r = min(max(r, 0), H - 1)
        return gr[r * W + c]
    a, b, c_ = z(col-1, row-1), z(col, row-1), z(col+1, row-1)
    d, f = z(col-1, row), z(col+1, row)
    gg, h, i = z(col-1, row+1), z(col, row+1), z(col+1, row+1)
    dzdx = ((c_ + 2*f + i) - (a + 2*d + gg)) / (8.0 * mpp)
    dzdy = ((gg + 2*h + i) - (a + 2*b + c_)) / (8.0 * mpp)
    rise = math.hypot(dzdx, dzdy)
    slope = math.degrees(math.atan(rise))
    if rise < 1e-9:
        return slope, -1.0
    asp = math.degrees(math.atan2(dzdy, -dzdx))
    asp = (90.0 - asp) % 360.0
    return slope, asp


def heat_load(lat, slope_deg, aspect_deg):
    """McCune & Keon (2002) Eq. 3. Folded about NE-SW: coolest at north-east."""
    l = math.radians(min(max(lat, 0), 60))
    s = math.radians(min(max(slope_deg, 0), 60))
    asp = 135.0 if aspect_deg < 0 else aspect_deg
    folded = math.radians(180.0 - abs(asp - 225.0))
    return (0.339 + 0.808*math.cos(l)*math.cos(s) - 0.196*math.sin(l)*math.sin(s)
            - 0.482*math.cos(folded)*math.sin(s))


def tpi(g, col, row, radius):
    """Elevation minus the mean of a ring neighbourhood. Negative = cove / lower slope."""
    W, H = g["W"], g["H"]
    gr = g["grid"]
    tot = n = 0
    for dr in range(-radius, radius + 1, max(1, radius // 4)):
        for dc in range(-radius, radius + 1, max(1, radius // 4)):
            if dc*dc + dr*dr > radius*radius:
                continue
            c = min(max(col + dc, 0), W - 1); r = min(max(row + dr, 0), H - 1)
            tot += gr[r * W + c]; n += 1
    if n == 0:
        return 0.0
    return gr[min(max(row,0),H-1) * W + min(max(col,0),W-1)] - tot / n


def band(v, lo, hi, falloff):
    if lo <= v <= hi:
        return 1.0
    d = (lo - v) if v < lo else (v - hi)
    return max(0.0, 1.0 - d / falloff)


def terrain_score(lat, slope, aspect, tpi_m, elev):
    heat = 1.0 - min(max((heat_load(lat, slope, aspect) - 0.2865) / (1.1136 - 0.2865), 0.0), 1.0)
    pos = band(tpi_m, -8.0, -1.0, 6.0)
    steep = band(slope, 6.0, 25.0, 9.0)
    elev_s = band(elev, 300.0, 1300.0, 250.0)
    return 0.40*heat + 0.30*pos + 0.20*steep + 0.10*elev_s


# ---------------------------------------------------------------- polygons

def point_in_ring(lon, lat, ring):
    inside = False
    n = len(ring)
    j = n - 1
    for i in range(n):
        xi, yi = ring[i]; xj, yj = ring[j]
        if (yi > lat) != (yj > lat):
            if lon < (xj - xi) * (lat - yi) / (yj - yi + 1e-18) + xi:
                inside = not inside
        j = i
    return inside


def ring_bbox(ring):
    xs = [p[0] for p in ring]; ys = [p[1] for p in ring]
    return min(xs), min(ys), max(xs), max(ys)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--around", nargs=2, type=float, required=True, metavar=("LAT", "LON"))
    ap.add_argument("--radius-km", type=float, default=3.0)
    ap.add_argument("--top", type=int, default=25)
    ap.add_argument("--step", type=int, default=4, help="sample every Nth cell (4 = ~15 m)")
    ap.add_argument("--min-soil", type=float, default=0.55,
                    help="ignore soil polygons scoring below this")
    ap.add_argument("--gpx"), ap.add_argument("--geojson"), ap.add_argument("--png")
    a = ap.parse_args()

    lat0, lon0 = a.around
    dlat = a.radius_km / 110.574
    dlon = a.radius_km / (111.320 * math.cos(math.radians(lat0)))
    west, south, east, north = lon0 - dlon, lat0 - dlat, lon0 + dlon, lat0 + dlat

    print(f"area {west:.4f},{south:.4f} .. {east:.4f},{north:.4f}", file=sys.stderr)
    polys = fetch_polygons(west, south, east, north)
    attrs = fetch_attributes(polys.keys())
    print(f"soil: {len(polys)} map units, {len(attrs)} with attributes", file=sys.stderr)

    scored_polys = []
    for mk, rings in polys.items():
        at = attrs.get(mk)
        if not at:
            continue
        lf, label = LANDFORM.get((at.get("landform") or "").strip().lower(), (0.40, "unrecorded"))
        dr = DRAINAGE.get((at.get("drainage") or "").strip().lower(), 0.40)
        s = 0.45*lf + 0.30*dr + 0.10*ph_score(at.get("ph")) + 0.15*om_score(at.get("om"))
        if s < a.min_soil:
            continue
        for ring in rings:
            scored_polys.append({"soil": round(s, 3), "label": label, "series": at.get("series"),
                                 "drainage": at.get("drainage"), "ring": ring,
                                 "bbox": ring_bbox(ring)})
    print(f"soil polygons at or above {a.min_soil}: {len(scored_polys)}", file=sys.stderr)
    if not scored_polys:
        print("Nothing scored above the soil threshold here. Try --min-soil 0.4.")
        return

    print("fetching elevation tiles...", file=sys.stderr)
    g = build_grid(west, south, east, north)
    print(f"elevation: {g['tiles']}/{g['want']} tiles, grid {g['W']}x{g['H']}", file=sys.stderr)
    if g["tiles"] == 0:
        print("No elevation tiles returned; cannot score terrain.", file=sys.stderr)
        return

    mpp = metres_per_px(lat0)
    tpi_radius = max(6, int(120.0 / mpp))     # ~120 m neighbourhood
    print(f"cell {mpp:.2f} m, TPI radius {tpi_radius} cells (~{tpi_radius*mpp:.0f} m)",
          file=sys.stderr)

    cells = []
    for row in range(2, g["H"] - 2, a.step):
        for col in range(2, g["W"] - 2, a.step):
            lon, lat = cell_lonlat(g, col, row)
            if not (west <= lon <= east and south <= lat <= north):
                continue
            hit = None
            for sp in scored_polys:
                x0, y0, x1, y1 = sp["bbox"]
                if x0 <= lon <= x1 and y0 <= lat <= y1 and point_in_ring(lon, lat, sp["ring"]):
                    hit = sp
                    break
            if not hit:
                continue
            elev = g["grid"][row * g["W"] + col]
            if elev <= -1000:
                continue
            slope, aspect = slope_aspect(g, col, row, mpp)
            t = terrain_score(lat, slope, aspect, tpi(g, col, row, tpi_radius), elev)
            cells.append({
                "lat": lat, "lon": lon, "elev": round(elev), "slope": round(slope, 1),
                "aspect": round(aspect, 1), "terrain": round(t, 3), "soil": hit["soil"],
                "combined": round(0.5 * hit["soil"] + 0.5 * t, 3),
                "series": hit["series"], "landform": hit["label"], "drainage": hit["drainage"],
            })

    if not cells:
        print("No cells fell inside a qualifying soil polygon.", file=sys.stderr)
        return
    cells.sort(key=lambda c: -c["combined"])

    # Spread the picks out: one per ~150 m, so the list is 25 places not 25 pixels of one place.
    picks, min_sep = [], 150.0 / 111320.0
    for c in cells:
        if all((c["lat"]-p["lat"])**2 + (c["lon"]-p["lon"])**2 > min_sep**2 for p in picks):
            picks.append(c)
        if len(picks) >= a.top:
            break

    print(f"\nscored {len(cells)} cells at {mpp:.1f} m inside qualifying soil\n")
    print(f"{'#':>2} {'tot':>5} {'soil':>5} {'terr':>5} {'lat,lon':<21} {'el':>5} "
          f"{'slp':>4} {'asp':>4}  {'series':<11} landform")
    print("-" * 110)
    for i, c in enumerate(picks, 1):
        comp = ("N","NE","E","SE","S","SW","W","NW")[int(((c["aspect"]+22.5)%360)//45)] \
            if c["aspect"] >= 0 else "flat"
        print(f"{i:>2} {c['combined']:>5.2f} {c['soil']:>5.2f} {c['terrain']:>5.2f} "
              f"{c['lat']:.5f},{c['lon']:.5f} {c['elev']:>5} {c['slope']:>4.0f} {comp:>4}  "
              f"{str(c['series'])[:11]:<11} {c['landform']}")

    print("\nchecking land for the top picks...", file=sys.stderr)
    for c in picks[:min(10, len(picks))]:
        st = land_status(c["lat"], c["lon"])
        c["land"] = st["national_forest"] or "not National Forest"
    print("\nland (factual, nothing filtered):")
    for i, c in enumerate(picks[:10], 1):
        print(f"  {i:>2} {c['lat']:.5f},{c['lon']:.5f}  {c.get('land')}")

    print("\n~3.9 m cells, but the USGS source beneath is mostly ~10 m: read this as "
          "'which end of the bench', not 'which square metre'.")
    print("Ranks HABITAT. It does not know where ginseng is.")

    if a.gpx:
        from nc_prospect import write_gpx
        write_gpx(a.gpx, [{"lat": c["lat"], "lon": c["lon"], "score": c["combined"],
                           "series": c["series"], "landform_label": c["landform"],
                           "drainage": c["drainage"], "hectares": 0.0,
                           "muname": f"{c['elev']}m slope{c['slope']:.0f}"} for c in picks])
        print(f"wrote {a.gpx}", file=sys.stderr)
    if a.geojson:
        json.dump({"type": "FeatureCollection", "features": [
            {"type": "Feature",
             "geometry": {"type": "Point", "coordinates": [c["lon"], c["lat"]]},
             "properties": c} for c in picks]}, open(a.geojson, "w"))
        print(f"wrote {a.geojson}", file=sys.stderr)


if __name__ == "__main__":
    main()
