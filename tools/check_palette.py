#!/usr/bin/env python3
"""Measures the two things the design spec asserted and never computed.

1. WCAG 2.1 contrast ratios for the GENSINGO palette. PRD section 5.1 claims 8.2:1, 11.4:1 and
   6.1:1. Those numbers were written down, not measured, and a stated accessibility ratio that
   is wrong is worse than no claim at all.

2. Whether the suitability heatmap ramp survives colour-vision deficiency. Contrast ratio
   cannot answer this: a ramp can pass every contrast check and still be unreadable to roughly
   8% of male users, because contrast is computed against the background and CVD confusion
   happens between two foreground colours. The ramp runs dark teal -> luminous green -> field
   amber, and green against amber is the canonical deuteranope confusion pair.

   Simulation is Vienot, Brettel & Mollon (1999) - the standard LMS-plane projection - applied
   in linear light, which is the part naive implementations get wrong.
"""

import itertools
import math

# ---------------------------------------------------------------- sRGB / WCAG

def srgb_to_linear(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def linear_to_srgb(c):
    c = max(0.0, min(1.0, c))
    return c * 12.92 if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def relative_luminance(rgb):
    r, g, b = (srgb_to_linear(v) for v in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast_ratio(fg, bg):
    l1, l2 = relative_luminance(fg), relative_luminance(bg)
    lo, hi = sorted((l1, l2))
    return (hi + 0.05) / (lo + 0.05)


# ---------------------------------------------------------------- CVD simulation

# sRGB linear -> LMS (Hunt-Pointer-Estevez, normalised to D65), per Vienot et al. 1999.
RGB_TO_LMS = (
    (0.31399022, 0.63951294, 0.04649755),
    (0.15537241, 0.75789446, 0.08670142),
    (0.01775239, 0.10944209, 0.87256922),
)
LMS_TO_RGB = (
    (5.47221206, -4.64196010, 0.16963708),
    (-1.12524190, 2.29317094, -0.16789520),
    (0.02980165, -0.19318073, 1.16364789),
)


def _mul(m, v):
    return tuple(sum(m[i][j] * v[j] for j in range(3)) for i in range(3))


def simulate_cvd(rgb, kind):
    """kind: 'protan' (no L cone), 'deutan' (no M cone), 'tritan' (no S cone)."""
    lin = tuple(srgb_to_linear(v) for v in rgb)
    l, m, s = _mul(RGB_TO_LMS, lin)
    if kind == "protan":
        l = 2.02344 * m - 2.52581 * s
    elif kind == "deutan":
        m = 0.494207 * l + 1.24827 * s
    elif kind == "tritan":
        s = -0.395913 * l + 0.801109 * m
    out = _mul(LMS_TO_RGB, (l, m, s))
    return tuple(int(round(255 * linear_to_srgb(c))) for c in out)


# ---------------------------------------------------------------- CIE Lab / dE2000

def rgb_to_lab(rgb):
    r, g, b = (srgb_to_linear(v) for v in rgb)
    x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
    y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
    xn, yn, zn = 0.95047, 1.0, 1.08883

    def f(t):
        return t ** (1 / 3) if t > 216 / 24389 else (841 / 108) * t + 4 / 29

    fx, fy, fz = f(x / xn), f(y / yn), f(z / zn)
    return (116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))


def delta_e_2000(lab1, lab2):
    l1, a1, b1 = lab1
    l2, a2, b2 = lab2
    c1 = math.hypot(a1, b1)
    c2 = math.hypot(a2, b2)
    cb = (c1 + c2) / 2
    g = 0.5 * (1 - math.sqrt(cb ** 7 / (cb ** 7 + 25 ** 7))) if cb > 0 else 0
    a1p, a2p = a1 * (1 + g), a2 * (1 + g)
    c1p, c2p = math.hypot(a1p, b1), math.hypot(a2p, b2)
    h1p = math.degrees(math.atan2(b1, a1p)) % 360
    h2p = math.degrees(math.atan2(b2, a2p)) % 360
    dlp = l2 - l1
    dcp = c2p - c1p
    if c1p * c2p == 0:
        dhp = 0.0
    elif abs(h2p - h1p) <= 180:
        dhp = h2p - h1p
    else:
        dhp = h2p - h1p - 360 if h2p > h1p else h2p - h1p + 360
    dHp = 2 * math.sqrt(c1p * c2p) * math.sin(math.radians(dhp) / 2)
    lb = (l1 + l2) / 2
    cbp = (c1p + c2p) / 2
    if c1p * c2p == 0:
        hbp = h1p + h2p
    elif abs(h1p - h2p) <= 180:
        hbp = (h1p + h2p) / 2
    elif h1p + h2p < 360:
        hbp = (h1p + h2p + 360) / 2
    else:
        hbp = (h1p + h2p - 360) / 2
    t = (1 - 0.17 * math.cos(math.radians(hbp - 30)) + 0.24 * math.cos(math.radians(2 * hbp))
         + 0.32 * math.cos(math.radians(3 * hbp + 6))
         - 0.20 * math.cos(math.radians(4 * hbp - 63)))
    dtheta = 30 * math.exp(-(((hbp - 275) / 25) ** 2))
    rc = 2 * math.sqrt(cbp ** 7 / (cbp ** 7 + 25 ** 7)) if cbp > 0 else 0
    sl = 1 + (0.015 * (lb - 50) ** 2) / math.sqrt(20 + (lb - 50) ** 2)
    sc = 1 + 0.045 * cbp
    sh = 1 + 0.015 * cbp * t
    rt = -math.sin(math.radians(2 * dtheta)) * rc
    return math.sqrt((dlp / sl) ** 2 + (dcp / sc) ** 2 + (dHp / sh) ** 2
                     + rt * (dcp / sc) * (dHp / sh))


# ---------------------------------------------------------------- the palette

PALETTE = {
    "Base":          (0x06, 0x10, 0x0B),
    "Surface":       (0x0D, 0x1B, 0x13),
    "Primary":       (0x00, 0xFF, 0x88),
    "Warning":       (0xFF, 0xC8, 0x57),
    "Alert":         (0xFF, 0x4D, 0x6D),
    "TextPrimary":   (0xE6, 0xF4, 0xEC),
    "TextSecondary": (0x8B, 0xA8, 0x97),
    "Hairline":      (0x1A, 0x33, 0x24),
}

# PRD section 5.1's asserted ratios. The pairing is the natural reading of the spec:
# body text on the base, and the two accents on the surface they actually sit on.
ASSERTED = [
    ("TextPrimary", "Base", 8.2),
    ("Primary", "Base", 11.4),
    ("TextSecondary", "Base", 6.1),
]


def ramp_colour(t):
    """Mirrors SuitabilityRasterizer.colourFor exactly."""
    def lerp(a, b, u):
        return int(round(a + (b - a) * u))
    if t < 0.5:
        u = t / 0.5
        return (lerp(0x0E, 0x00, u), lerp(0x4A, 0xFF, u), lerp(0x5A, 0x88, u))
    u = (t - 0.5) / 0.5
    return (lerp(0x00, 0xFF, u), lerp(0xFF, 0xC8, u), lerp(0x88, 0x57, u))


def main():
    print("=" * 78)
    print("1. WCAG 2.1 CONTRAST - asserted vs measured")
    print("=" * 78)
    worst = None
    for fg, bg, claimed in ASSERTED:
        actual = contrast_ratio(PALETTE[fg], PALETTE[bg])
        err = actual - claimed
        verdict = "OK" if abs(err) < 0.3 else "WRONG"
        if verdict == "WRONG":
            worst = True
        print(f"  {fg:14s} on {bg:9s}  claimed {claimed:5.1f}:1   "
              f"measured {actual:6.2f}:1   {verdict} ({err:+.2f})")

    print()
    print("  Full matrix against the two backgrounds (AA body text needs 4.5, large 3.0):")
    for name, rgb in PALETTE.items():
        if name in ("Base", "Surface"):
            continue
        cb = contrast_ratio(rgb, PALETTE["Base"])
        cs = contrast_ratio(rgb, PALETTE["Surface"])
        flag = "" if min(cb, cs) >= 4.5 else ("  <- fails AA body" if min(cb, cs) < 4.5 else "")
        print(f"    {name:14s} Base {cb:6.2f}:1   Surface {cs:6.2f}:1{flag}")

    print()
    print("=" * 78)
    print("2. HEATMAP RAMP UNDER COLOUR-VISION DEFICIENCY")
    print("=" * 78)
    steps = [i / 10 for i in range(11)]
    print("  score   normal        deuteranope   protanope     L*(norm) L*(deut)")
    for t in steps:
        c = ramp_colour(t)
        d = simulate_cvd(c, "deutan")
        p = simulate_cvd(c, "protan")
        print(f"  {t:4.1f}   #{c[0]:02X}{c[1]:02X}{c[2]:02X}       "
              f"#{d[0]:02X}{d[1]:02X}{d[2]:02X}       #{p[0]:02X}{p[1]:02X}{p[2]:02X}       "
              f"{rgb_to_lab(c)[0]:6.1f}  {rgb_to_lab(d)[0]:6.1f}")

    print()
    print("  Is the ramp monotonic in perceived lightness? If it is, a CVD viewer can still")
    print("  read it as an ordering even when hue is unavailable.")
    for kind in ("normal", "deutan", "protan"):
        ls = []
        for t in steps:
            c = ramp_colour(t)
            if kind != "normal":
                c = simulate_cvd(c, kind)
            ls.append(rgb_to_lab(c)[0])
        drops = [(steps[i], ls[i], ls[i + 1]) for i in range(len(ls) - 1) if ls[i + 1] < ls[i] - 0.5]
        span = max(ls) - min(ls)
        state = "MONOTONIC" if not drops else f"NOT monotonic ({len(drops)} reversals)"
        print(f"    {kind:8s} L* span {span:5.1f}  {state}")
        for at, a, b in drops[:4]:
            print(f"        reverses after score {at:.1f}: L* {a:.1f} -> {b:.1f}")

    print()
    print("  Worst confusable pair among the 5 legend bands (dE2000; below ~10 is a problem")
    print("  for categorical reading, below ~3 is indistinguishable):")
    bands = [0.1, 0.3, 0.5, 0.7, 0.9]
    for kind in ("normal", "deutan", "protan"):
        worst_pair, worst_de = None, 1e9
        for a, b in itertools.combinations(bands, 2):
            ca, cb2 = ramp_colour(a), ramp_colour(b)
            if kind != "normal":
                ca, cb2 = simulate_cvd(ca, kind), simulate_cvd(cb2, kind)
            de = delta_e_2000(rgb_to_lab(ca), rgb_to_lab(cb2))
            if de < worst_de:
                worst_de, worst_pair = de, (a, b)
        note = "" if worst_de >= 10 else "   <- NOT SAFELY DISTINGUISHABLE"
        print(f"    {kind:8s} worst dE2000 {worst_de:6.2f} between bands "
              f"{worst_pair[0]:.1f} and {worst_pair[1]:.1f}{note}")

    print()
    print("  Adjacent-band separation, the case that actually matters on a map:")
    for kind in ("normal", "deutan", "protan"):
        des = []
        for a, b in zip(bands, bands[1:]):
            ca, cb2 = ramp_colour(a), ramp_colour(b)
            if kind != "normal":
                ca, cb2 = simulate_cvd(ca, kind), simulate_cvd(cb2, kind)
            des.append(delta_e_2000(rgb_to_lab(ca), rgb_to_lab(cb2)))
        print(f"    {kind:8s} " + "  ".join(f"{d:5.1f}" for d in des)
              + f"   min {min(des):5.1f}")


if __name__ == "__main__":
    main()
