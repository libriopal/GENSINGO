# Third-party notices

Source code from other projects that is copied or ported into this repository, with the
licence notices those projects require. Library dependencies resolved by Gradle are not
listed here.

## maplibre-contour: isolines (marching-squares contour lines)

- **Used in:** `app/src/main/java/com/ginsengo/steward/terrain/Isolines.kt`, a Kotlin port
  (deviations are listed in that file's header).
- **Ported from:** `src/isolines.ts` at
  <https://github.com/onthegomap/maplibre-contour/blob/a3c4e3c683f34999af2bf4c7349c6e4cda345fcb/src/isolines.ts>
- **Version:** commit `a3c4e3c683f34999af2bf4c7349c6e4cda345fcb` (the `main` branch and tag
  `v0.1.1`, package version 0.1.1), fetched 2026-10-01. File SHA-256
  `0d0cc133ca38f90779313332b5cd76a662b4e44fa8c0764f9679d7328a0e2ed2`.
- **Licences:** BSD-3-Clause (maplibre-contour); that file is itself adapted from d3-contour,
  ISC. Both notices follow. The repository's LICENSE also carries an MIT notice for vt-pbf code;
  that code is in other files of maplibre-contour and was not ported, so it does not apply here.

### maplibre-contour (BSD-3-Clause)

<https://github.com/onthegomap/maplibre-contour/blob/a3c4e3c683f34999af2bf4c7349c6e4cda345fcb/LICENSE>

```
BSD 3-Clause License

Copyright (c) 2023, Michael Barry and maplibre-contour contributors

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

### d3-contour (ISC)

<https://github.com/d3/d3-contour>, credited in the header of `isolines.ts` and in
maplibre-contour's LICENSE ("Contains code from d3-contour").

```
Copyright 2012-2023 Mike Bostock

Permission to use, copy, modify, and/or distribute this software for any purpose
with or without fee is hereby granted, provided that the above copyright notice
and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS
OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
THIS SOFTWARE.
```
