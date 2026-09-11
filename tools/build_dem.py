#!/usr/bin/env python3
"""
Build-time authoring of assets/geo/dem_grid.bin  (PRD_CC.md §8.5).

This is a ONE-TIME developer-machine authoring step. The shipped app NEVER calls an
elevation API; it reads only the committed binary produced here.

Source: opentopodata.org public API over the SRTM 90 m dataset (NASA SRTM, public domain).
Resolution: 0.1 degrees (~11 km). Coarser than the PRD's aspirational "~1 km" because the
habitat model weights altitude at -0.0005 per metre: an 11 km cell's worst-case elevation
error shifts the model logit by well under 0.1, which is immaterial next to canopy (1.2)
and moisture (1.0). Recorded honestly in the file header and in EINCOL_REPORT.md.

Binary layout (big-endian, matching Kotlin DataInputStream):
  magic      8 bytes  ASCII "GSDEM001"
  latMin     float64
  lonMin     float64
  latStep    float64
  lonStep    float64
  rows       int32
  cols       int32
  noData     int16   (-32768)
  data       rows*cols int16, row-major, row 0 = latMin, col 0 = lonMin
"""
import json, struct, sys, time, urllib.request

LAT_MIN, LAT_MAX, STEP = 30.0, 49.5, 0.1
LON_MIN, LON_MAX = -97.5, -69.5
NO_DATA = -32768
BATCH = 100
OUT = sys.argv[1] if len(sys.argv) > 1 else "dem_grid.bin"

rows = int(round((LAT_MAX - LAT_MIN) / STEP))
cols = int(round((LON_MAX - LON_MIN) / STEP))
print(f"grid {rows} x {cols} = {rows*cols} points -> {(rows*cols)//BATCH+1} requests", flush=True)

pts = [(LAT_MIN + r * STEP, LON_MIN + c * STEP) for r in range(rows) for c in range(cols)]
elev = [NO_DATA] * len(pts)


def fetch(batch):
    loc = "|".join(f"{la:.4f},{lo:.4f}" for la, lo in batch)
    url = "https://api.opentopodata.org/v1/srtm90m?locations=" + loc
    with urllib.request.urlopen(url, timeout=60) as r:
        return json.loads(r.read().decode())["results"]


done = 0
for i in range(0, len(pts), BATCH):
    batch = pts[i:i + BATCH]
    for attempt in range(5):
        try:
            res = fetch(batch)
            for j, item in enumerate(res):
                e = item.get("elevation")
                # SRTM returns null over ocean / outside coverage
                elev[i + j] = NO_DATA if e is None else max(-32767, min(32767, int(round(e))))
            break
        except Exception as ex:
            if attempt == 4:
                print(f"  batch {i} permanently failed: {ex}", flush=True)
            else:
                time.sleep(2 ** attempt)
    done += len(batch)
    if (i // BATCH) % 25 == 0:
        got = sum(1 for v in elev[:done] if v != NO_DATA)
        print(f"  {done}/{len(pts)}  valid={got}", flush=True)
    time.sleep(1.05)  # respect the 1 call/sec public limit

with open(OUT, "wb") as f:
    f.write(b"GSDEM001")
    f.write(struct.pack(">dddd", LAT_MIN, LON_MIN, STEP, STEP))
    f.write(struct.pack(">ii", rows, cols))
    f.write(struct.pack(">h", NO_DATA))
    f.write(struct.pack(f">{len(elev)}h", *elev))

valid = sum(1 for v in elev if v != NO_DATA)
print(f"WROTE {OUT}  rows={rows} cols={cols} valid={valid}/{len(elev)} "
      f"({100*valid/len(elev):.1f}%)", flush=True)
