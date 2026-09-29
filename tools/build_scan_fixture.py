#!/usr/bin/env python3
"""
Builds a multi-tile elevation mosaic fixture for RadiusScan tests and timing.

Same source and decoding as build_terrain_fixture.py (AWS Terrarium, public domain), written
as raw big-endian int16 metres with the tile origin, so the JVM test can reconstruct a
DemTileStore.Mosaic exactly as the app assembles one.

Layout:  magic "GSDEMMOS" | int32 zoom | int32 tileX0 | int32 tileY0 | int32 tilesX |
         int32 tilesY | (tilesX*256) * (tilesY*256) int16

Usage:   build_scan_fixture.py OUT LAT LON ZOOM TILES
  The committed test fixture:  app/src/test/resources/scan_boone_z12.bin 36.2 -81.67 12 2
  The timing fixture (not committed, 6x6 = the app's full 10-mile scan at z12):
                               <scratch>/scan_haywood_z12.bin 35.55 -82.95 12 6
"""
import struct
import sys
import urllib.request

import math
import zlib

BUCKET = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium"


# Copied from build_terrain_fixture.py rather than imported: that script does its work at
# import time, so importing it would download and write its own fixtures.
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


def main():
    out, lat, lon, z, n = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
    cx, cy = tile_xy(lat, lon, z)
    x0, y0 = cx - (n - 1) // 2, cy - (n - 1) // 2
    w = h = n * 256
    grid = [0] * (w * h)
    for ty in range(n):
        for tx in range(n):
            url = f"{BUCKET}/{z}/{x0 + tx}/{y0 + ty}.png"
            data = urllib.request.urlopen(url, timeout=30).read()
            tw, th, rgb = load_png_rgb(data)
            for py in range(th):
                for px in range(tw):
                    i = (py * tw + px) * 3
                    e = (rgb[i] * 256 + rgb[i + 1] + rgb[i + 2] / 256.0) - 32768.0
                    grid[(ty * 256 + py) * w + tx * 256 + px] = int(round(e))
    with open(out, "wb") as f:
        f.write(b"GSDEMMOS")
        f.write(struct.pack(">iiiii", z, x0, y0, n, n))
        f.write(struct.pack(f">{w * h}h", *grid))
    print(f"wrote {out}: z{z} tiles {x0},{y0} {n}x{n} ({w}x{h})")


if __name__ == "__main__":
    main()
