#!/usr/bin/env bash
# Compile the terrain overlay's GLSL with glslangValidator.
#
# A shader that fails to compile on a device gives a black overlay and one line in logcat.
# Since this project has no emulator or handset available, compiling the sources offline is
# the only evaluator that actually executes the shader code rather than reading it.
#
#   apt-get install glslang-tools
#   tools/validate_shaders.sh
set -euo pipefail

SRC="app/src/main/java/com/ginsengo/steward/terrain3d/TerrainShaders.kt"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

python3 - "$SRC" "$OUT" <<'PY'
import re, sys
src, out = sys.argv[1], sys.argv[2]
text = open(src).read()
# Each shader is a triple-quoted constant starting with the #version directive.
found = dict(re.findall(r'const val (VERTEX|FRAGMENT) = """(.*?)"""', text, re.S))
if set(found) != {"VERTEX", "FRAGMENT"}:
    sys.exit(f"expected VERTEX and FRAGMENT in {src}, found {sorted(found)}")
for name, ext in (("VERTEX", "vert"), ("FRAGMENT", "frag")):
    body = found[name]
    if "#version" not in body:
        sys.exit(f"{name} has no #version directive")
    open(f"{out}/terrain.{ext}", "w").write(body)
print("extracted VERTEX and FRAGMENT")
PY

fail=0
for stage in vert frag; do
  echo "--- $stage ---"
  # No -G: that targets SPIR-V, which requires ES 3.10+. Android GLES3 consumes GLSL ES
  # 3.00 source directly, so validate against the declared version instead.
  if glslangValidator "$OUT/terrain.$stage"; then
    echo "OK $stage"
  else
    echo "FAILED $stage"
    fail=1
  fi
done

exit $fail
