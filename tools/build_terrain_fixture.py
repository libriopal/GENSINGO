#!/usr/bin/env python3
"""
Builds the real-terrain test fixtures in app/src/test/resources/.

Downloads Terrarium elevation tiles from the AWS Open Data terrain bucket (public domain)
and writes them as raw big-endian int16 metres.

Why not just commit the PNGs: Android unit tests run against android.jar, which carries no
javax.imageio, so a PNG fixture would force an image decoder into the test just to read
terrain. The raw form is the same elevations with the decoding done once, here.

Layout:  magic "GSDEMTIL" (8 bytes) | int32 width | int32 height | width*height int16

Default location is Boone, North Carolina — genuine Appalachian ginseng country inside
3DEP lidar coverage, which is what makes the z15 tile a fair test of ~3.9 m detail.
"""
import math
import struct
import sys
import urllib.request
import zlib

LAT = 36.2
LON = -81.67
BUCKET = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium"
OUT_DIR = sys.argv[1] if len(sys.argv) > 1 else "app/src/test/resources"


def tile_xy(lat, lon, z):
    n = 2 ** z
    x = int((lon + 180.0) / 360.0 * n)
    y = int((1.0 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2.0 * n)
    return x, y


def load_png_rgb(data):
    """Minimal PNG reader: 8-bit RGB, which is all Terrarium ever serves."""
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
    pos, idat, w, h = 8, b"", None, None
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + ln]
        pos += 12 + ln
        if typ == b"IHDR":
            w, h, _bd, ct = struct.unpack(">IIBB", chunk[:10])
            assert ct == 2, f"expected RGB, got colour type {ct}"
        elif typ == b"IDAT":
            idat += chunk
        elif typ == b"IEND":
            break
    raw = zlib.decompress(idat)
    ch, stride = 3, w * 3
    out, prev, i = bytearray(), bytearray(stride), 0
    for _ in range(h):
        f = raw[i]; i += 1
        line = bytearray(raw[i:i + stride]); i += stride
        for x in range(stride):
            a = line[x - ch] if x >= ch else 0
            b = prev[x]
            c = prev[x - ch] if x >= ch else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + ((a + b) >> 1)) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        out += line
        prev = line
    return w, h, bytes(out)


for z, name in ((14, "terrain_boone_z14"), (15, "terrain_boone_z15")):
    x, y = tile_xy(LAT, LON, z)
    url = f"{BUCKET}/{z}/{x}/{y}.png"
    with urllib.request.urlopen(url, timeout=90) as r:
        blob = r.read()
    w, h, px = load_png_rgb(blob)
    vals = []
    for i in range(w * h):
        R, G, B = px[i * 3], px[i * 3 + 1], px[i * 3 + 2]
        e = (R * 256 + G + B / 256) - 32768
        vals.append(max(-32767, min(32767, int(round(e)))))
    out = f"{OUT_DIR}/{name}.bin"
    with open(out, "wb") as f:
        f.write(b"GSDEMTIL")
        f.write(struct.pack(">ii", w, h))
        f.write(struct.pack(f">{len(vals)}h", *vals))
    res = 156543.03392 * math.cos(math.radians(LAT)) / (2 ** z)
    print(f"{out}: z{z} {w}x{h} @ {res:.2f} m/px, elev {min(vals)}..{max(vals)} m")
