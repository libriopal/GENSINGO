#!/usr/bin/env python3
"""
Checks a Base44 handoff archive before Claude Code builds an APK from it (base44.md, B12 and
Part 5 step 1). Exactly the rules of base44.md B12, nothing more:

  1. the top level is HANDOFF.json, singnav-web/, gensingo/ (optionally inside one folder);
  2. HANDOFF.json parses and has every field of template T8, with handoff_version 1;
  3. gensingo/ has the files an Android build and this repository's governance need, plus the
     blueprint HANDOFF.json names;
  4. singnav-web/ has a package.json that parses and at least one JS/TS source file;
  5. no build output, dependencies, keystores, local config, env files, APKs, or (in
     singnav-web/) data exports;
  6. no text file holds a secret.

Prints one line per rule and PASS or FAIL; exits 0 only on PASS. Never prints file contents
(a matched secret is reported by file and kind, not by value).

Usage: tools/verify_handoff.py <archive.zip>
"""
import hashlib
import json
import re
import sys
import zipfile

TOP = {"HANDOFF.json", "singnav-web", "gensingo"}

GENSINGO_REQUIRED = [
    "settings.gradle.kts", "gradlew", "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml", "ARCHITECT.md", "base44.md",
]

# Template T8: field -> type (dict = nested object checked below).
T8 = {
    "handoff_version": int, "created": str, "singnav": dict, "gensingo": dict,
    "blueprint": str, "audit": dict, "web_polish": dict, "open": list,
    "attest_no_data_no_secrets": bool,
}
T8_NESTED = {
    "singnav": {"base44_app_name": str, "export_method": str, "exported_at": str},
    "gensingo": {"base_commit": str, "branch": str, "head_commit": (str, type(None)),
                 "contracts": list, "tests_added": list, "compiled": bool},
    "audit": {"done": bool, "by": str},
    "web_polish": {"done": list, "open": list},
}

FORBIDDEN_DIRS = re.compile(r"(^|/)(node_modules|\.gradle|build|\.idea)/")
FORBIDDEN_FILES = re.compile(
    r"(\.jks|\.keystore|\.apk|\.aab)$|(^|/)(keystore\.properties|local\.properties)$|(^|/)\.env(\.[^/]*)?$")
DATA_FILES = re.compile(r"\.(csv|sqlite|sqlite3|db)$", re.I)
SECRETS = {
    "Anthropic key": re.compile(rb"sk-ant-[A-Za-z0-9_\-]{20,}"),
    "Google key": re.compile(rb"AIza[0-9A-Za-z_\-]{35}"),
    "GitHub token": re.compile(rb"gh[pousr]_[A-Za-z0-9]{36,}"),
    "private key": re.compile(rb"-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
}
TEXT_EXT = re.compile(r"\.(kt|kts|java|js|jsx|ts|tsx|json|md|txt|xml|yml|yaml|properties|gradle|html|css|py|sh|toml|env|cfg|ini)$", re.I)
COORD_KEYS = re.compile(rb'"(lat|latitude)"\s*:\s*-?\d+(\.\d+)?\s*,\s*"(lng|lon|long|longitude)"\s*:', re.I)


def strip_root(names):
    """The archive's entries relative to its handoff root (the three top-level items)."""
    tops = {n.split("/", 1)[0] for n in names if n}
    if TOP <= tops or tops <= TOP:
        return "", names
    if len(tops) == 1:
        root = tops.pop() + "/"
        return root, [n[len(root):] for n in names if n.startswith(root)]
    return "", names


def check(path):
    results = []

    def rule(n, ok, what):
        results.append((n, ok, what))

    try:
        z = zipfile.ZipFile(path)
    except (OSError, zipfile.BadZipFile) as e:
        print(f"FAIL 1 archive does not open ({e.__class__.__name__})")
        return False
    with z:
        names = [i.filename for i in z.infolist()]
        root, rel = strip_root(names)
        tops = {n.split("/", 1)[0] for n in rel if n}
        rule(1, tops == TOP, f"top level is {sorted(tops)}" + (f" (inside {root})" if root else ""))

        def read(name):
            return z.read(root + name)

        # 2. HANDOFF.json
        h = None
        try:
            h = json.loads(read("HANDOFF.json"))
        except (KeyError, ValueError) as e:
            rule(2, False, f"HANDOFF.json missing or not JSON ({e.__class__.__name__})")
        if h is not None:
            bad = [k for k, t in T8.items() if not isinstance(h.get(k), t) or isinstance(h.get(k), bool) and t is int]
            for k, fields in T8_NESTED.items():
                if isinstance(h.get(k), dict):
                    bad += [f"{k}.{f}" for f, t in fields.items() if f not in h[k] or not isinstance(h[k][f], t)]
            if h.get("handoff_version") != 1:
                bad.append("handoff_version != 1")
            if h.get("attest_no_data_no_secrets") is not True:
                bad.append("attest_no_data_no_secrets is not true")
            rule(2, not bad, "HANDOFF.json fields " + ("complete" if not bad else "wrong: " + ", ".join(bad)))

        # 3. gensingo/
        have = set(rel)
        need = list(GENSINGO_REQUIRED)
        if h is not None and isinstance(h.get("blueprint"), str):
            need.append(h["blueprint"])
        missing = [f for f in need if "gensingo/" + f not in have]
        rule(3, not missing, "gensingo/ has " + ("every required file" if not missing else "missing: " + ", ".join(missing)))

        # 4. singnav-web/
        pkg_ok = False
        try:
            json.loads(read("singnav-web/package.json"))
            pkg_ok = True
        except (KeyError, ValueError):
            pass
        src = [n for n in rel if n.startswith("singnav-web/") and re.search(r"\.(js|jsx|ts|tsx)$", n)
               and "node_modules/" not in n]
        rule(4, pkg_ok and bool(src),
             f"singnav-web/ package.json {'parses' if pkg_ok else 'missing or invalid'}, {len(src)} source files")

        # 5. forbidden content
        forbidden = []
        for n in rel:
            if FORBIDDEN_DIRS.search(n):
                forbidden.append(f"{n.split('/')[0]}/…/{FORBIDDEN_DIRS.search(n).group(2)}/")
            elif FORBIDDEN_FILES.search(n) and not n.endswith(".env.example"):
                forbidden.append(n)
            elif n.startswith("singnav-web/") and DATA_FILES.search(n):
                forbidden.append(n + " (data export)")
            elif n.startswith("singnav-web/") and n.endswith(".json") and not n.endswith("package-lock.json"):
                body = read(n)
                if len(COORD_KEYS.findall(body)) >= 5:
                    forbidden.append(n + " (rows with latitude and longitude)")
        forbidden = sorted(set(forbidden))
        rule(5, not forbidden, "no forbidden content" if not forbidden else "forbidden: " + "; ".join(forbidden[:12]))

        # 6. secrets in text files
        leaks = []
        for info in z.infolist():
            n = info.filename[len(root):]
            if info.is_dir() or not (TEXT_EXT.search(n) or n.rsplit("/", 1)[-1].startswith(".env")):
                continue
            if info.file_size > 5_000_000:
                continue
            body = z.read(info.filename)
            for kind, rx in SECRETS.items():
                if rx.search(body):
                    leaks.append(f"{n} ({kind})")
        rule(6, not leaks, "no secrets found" if not leaks else "secrets in: " + "; ".join(leaks[:12]))

    ok = all(r[1] for r in results)
    for n, good, what in sorted(results):
        print(f"{'ok  ' if good else 'FAIL'} {n} {what}")
    with open(path, "rb") as f:
        digest = hashlib.sha256(f.read()).hexdigest()
    print(f"sha256 {digest}")
    print("PASS" if ok else "FAIL")
    return ok


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__.strip().splitlines()[-1])
        sys.exit(2)
    sys.exit(0 if check(sys.argv[1]) else 1)
