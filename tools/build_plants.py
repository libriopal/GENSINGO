#!/usr/bin/env python3
"""
Build-time authoring of companion-plant reference photos (PRD_CC.md §8.4).

Searches Wikimedia Commons for each species, then downloads the first candidate whose
licence and attribution can actually be read from the API. Records the real licence,
author and source URL so the in-app "VERIFIED - botanical reference (Wikimedia Commons)"
tag is backed by retrievable attribution rather than a hardcoded string.

A species whose licence cannot be read is SKIPPED, not shipped with a guessed label.
AI-generated imagery is never used (PRD §8.4).
"""
import json, os, re, sys, time, urllib.parse, urllib.request

OUT_IMG, OUT_JSON = sys.argv[1], sys.argv[2]
os.makedirs(OUT_IMG, exist_ok=True)
UA = "GENSINGO-build/1.0 (offline field companion; botanical reference assets)"
API = "https://commons.wikimedia.org/w/api.php?"

PLANTS = [
    ("bloodroot", "Bloodroot", "Sanguinaria canadensis", "strong",
     "Single deeply-lobed leaf wrapped around one white spring flower. Shares the rich, "
     "moist, shaded slopes ginseng favours; one of the most reliable companions."),
    ("trillium", "Trillium", "Trillium grandiflorum", "strong",
     "Three leaves, three petals, in one whorl. Dense trillium on a north- or east-facing "
     "slope is a strong sign of undisturbed rich woods."),
    ("trout_lily", "Trout Lily", "Erythronium americanum", "moderate",
     "Mottled brown-green basal leaves, nodding yellow flower. Indicates moist, deep "
     "woodland soil, though it tolerates wetter ground than ginseng."),
    ("cutleaf_toothwort", "Cutleaf Toothwort", "Cardamine concatenata", "strong",
     "Deeply cut, toothed whorled leaves with white-to-pink flowers. Strongly tied to the "
     "same rich cove-hardwood soils as ginseng."),
    ("spicebush", "Spicebush", "Lindera benzoin", "strong",
     "Shrub; crushed twigs and leaves smell sharply of citrus and spice. Marks the moist, "
     "fertile lower slopes ginseng grows on."),
    ("christmas_fern", "Christmas Fern", "Polystichum acrostichoides", "moderate",
     "Evergreen fern; each leaflet has a small ear at its base, like a Christmas stocking. "
     "Marks suitable slope and drainage — but ferns are reported to exude compounds that "
     "harm ginseng growing right beside them, so read it as the right hillside rather than "
     "the right square foot."),
    ("carpet_moss", "Carpet Moss", "Thuidium delicatulum", "moderate",
     "Fern-like feathery moss forming loose mats. Signals consistently damp, shaded, "
     "undisturbed ground."),
    # Calcium indicators. Soil calcium is among the strongest published predictors of
    # ginseng site quality (Burkhart, Penn State: ~3,360 kg/ha marks promising ground) and
    # is the one major factor the terrain heatmap cannot see. These species are how a
    # digger reads it on foot, so they belong in the field checklist.
    ("jack_in_the_pulpit", "Jack-in-the-Pulpit", "Arisaema triphyllum", "strong",
     "Hooded green-and-purple flower over three-part leaves. A calcium-loving species and "
     "one of the most-cited indicators of rich ginseng ground."),
    ("maidenhair_fern", "Maidenhair Fern", "Adiantum pedatum", "strong",
     "Delicate fan of leaflets on wiry black stalks. Strongly tied to calcium-rich, "
     "well-drained slopes — the soil chemistry ginseng wants."),
    ("blue_cohosh", "Blue Cohosh", "Caulophyllum thalictroides", "strong",
     "Blue-green compound leaves, later deep blue seeds. Another calcium indicator of "
     "rich cove-hardwood soils."),
    ("mayapple", "Mayapple", "Podophyllum peltatum", "moderate",
     "Umbrella-like leaves in colonies. Common in the same rich woods, though it tolerates "
     "more light and disturbance than ginseng does."),
    ("black_cohosh", "Black Cohosh", "Actaea racemosa", "strong",
     "Tall white flower spikes over divided leaves. Named repeatedly in extension guidance "
     "as an understory species marking ginseng-suitable habitat."),
]

# Licences acceptable for redistribution inside the APK, with attribution retained.
OK_LIC = re.compile(r"(public domain|cc0|cc[- ]by)", re.I)
BAD_NAME = re.compile(r"(ai[-_ ]generated|midjourney|stable[-_ ]diffusion|dall[-_ ]?e)", re.I)

# Scanned book plates and engravings dominate Commons search for binomials because the
# species name sits in the book title. They are legitimate botanical references but they
# are not photographs, and PRD §8.4 asks for photographs for field identification.
# Rank them last rather than excluding them, so a species with no free photo still ships
# something usable - with media_type recorded honestly either way.
PLATE = re.compile(
    r"(flora of|medical botany|medicinal plants|\bpl\.|\bplate\b|\btable\b|BHL|"
    r"engr|lithograph|illustration|drawing|herbarium|specimen|\b1[6-9]\d\d\b|"
    # Scanned-plate collections are filed under a catalogue code, not a description:
    # "BB-0034", "AMP-011-0071". Any uppercase sigil followed by a digit block is a
    # scan ID, and a file named by catalogue number is never somebody's field photo.
    r"\b[A-Z]{2,4}-\d{2,4}\b)", re.I)


def is_plate(title):
    return bool(PLATE.search(title))


def _get(url, timeout=60):
    """Commons rate-limits hard. Back off rather than silently dropping a species."""
    last = None
    for attempt in range(6):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.read()
        except Exception as e:
            last = e
            code = getattr(e, "code", None)
            if code in (429, 503):
                time.sleep(5 * (attempt + 1))
            else:
                time.sleep(2 * (attempt + 1))
    raise last


def api(params):
    return json.loads(_get(API + urllib.parse.urlencode(params)).decode())


def strip(v):
    return re.sub(r"<[^>]+>", "", v or "").strip()


def search(sci):
    d = api({"action": "query", "format": "json", "generator": "search",
             "gsrsearch": f'filetype:bitmap "{sci}"', "gsrnamespace": "6",
             "gsrlimit": "20", "prop": "imageinfo",
             "iiprop": "url|extmetadata|mime", "iiurlwidth": "900"})
    return list(d.get("query", {}).get("pages", {}).values())


records = []
existing = {}
if os.path.exists(OUT_JSON):
    try:
        existing = {r["slug"]: r for r in json.load(open(OUT_JSON))}
    except Exception:
        existing = {}

for slug, name, sci, strength, desc in PLANTS:
    prior = existing.get(slug)
    if prior and os.path.exists(os.path.join(OUT_IMG, f"{slug}.jpg")):
        # Already have a licensed photo for this species; refresh only the text fields.
        prior.update(name=name, scientific_name=sci, description=desc,
                     indicator_strength=strength)
        records.append(prior)
        print(f"KEEP {slug}: {prior.get('photo_license')}", flush=True)
        continue
    chosen = None
    try:
        pages = search(sci)
    except Exception as e:
        print(f"SKIP {slug}: search error {e}", flush=True)
        continue
    candidates = []
    for page in pages:
        title = page.get("title", "")
        if BAD_NAME.search(title):
            continue
        ii = (page.get("imageinfo") or [{}])[0]
        if ii.get("mime") not in ("image/jpeg", "image/png"):
            continue
        em = ii.get("extmetadata", {})
        lic = strip(em.get("LicenseShortName", {}).get("value"))
        if not lic or not OK_LIC.search(lic):
            continue
        url = ii.get("thumburl") or ii.get("url")
        if not url:
            continue
        candidates.append({
            "title": title, "url": url, "license": lic,
            "author": strip(em.get("Artist", {}).get("value")) or
                      strip(em.get("Credit", {}).get("value")) or "See Commons file page",
            "descpage": ii.get("descriptionurl", ""),
            "media_type": "illustration" if is_plate(title) else "photograph",
        })
    # photographs first, original search relevance preserved within each group
    candidates.sort(key=lambda c: c["media_type"] != "photograph")
    chosen = candidates[0] if candidates else None
    if not chosen:
        print(f"SKIP {slug}: no candidate with a readable acceptable licence", flush=True)
        continue
    try:
        blob = _get(chosen["url"], timeout=90)
    except Exception as e:
        print(f"SKIP {slug}: download error {e}", flush=True)
        continue
    fname = f"{slug}.jpg"
    with open(os.path.join(OUT_IMG, fname), "wb") as f:
        f.write(blob)
    records.append({
        "slug": slug, "name": name, "scientific_name": sci,
        "photo_asset": f"companion_plants/{fname}",
        "description": desc, "indicator_strength": strength,
        "photo_license": chosen["license"], "photo_author": chosen["author"],
        "photo_source": chosen["descpage"], "photo_title": chosen["title"],
        "media_type": chosen["media_type"],
        "provenance": "verified_reference",
    })
    print(f"OK   {slug}: {len(blob)//1024} KB [{chosen['media_type']}] "
          f"[{chosen['license']}] {chosen['title']}", flush=True)
    time.sleep(1.5)

with open(OUT_JSON, "w") as f:
    json.dump(records, f, indent=2)
print(f"\nWROTE {OUT_JSON} with {len(records)}/{len(PLANTS)} plants", flush=True)
