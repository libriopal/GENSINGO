#!/usr/bin/env python3
"""
Base44 dev server for GENSINGO — a native Android app that builds to an APK.

Since the app cannot run in a web browser, this script:
  1. Runs the Gradle build and JVM unit tests inside the container.
  2. Serves a live status page on port 3000 showing the results.

The page auto-refreshes while the build is running and stops once it finishes.
"""
import html
import subprocess
import threading
import time
from http.server import HTTPServer, BaseHTTPRequestHandler

PORT = 3000
BUILD_CMD = ["./gradlew", ":app:testDebugUnitTest", "--no-daemon", "--console=plain"]
BUILD_TIMEOUT = 900  # 15 minutes — first run downloads Gradle + all dependencies

# Shared state — None means "still building"
build_result = None
build_started = None


def run_build():
    global build_result, build_started
    build_started = time.time()
    try:
        result = subprocess.run(
            BUILD_CMD,
            capture_output=True,
            text=True,
            timeout=BUILD_TIMEOUT,
            cwd="/workspace",
        )
        output = result.stdout or ""
        if result.stderr:
            output += "\n" + result.stderr
        build_result = (result.returncode == 0, output)
    except subprocess.TimeoutExpired as e:
        out = e.stdout or "" if isinstance(e.stdout, str) else ""
        if e.stderr:
            out += "\n" + (e.stderr if isinstance(e.stderr, str) else "")
        build_result = (False, f"Build timed out after {BUILD_TIMEOUT}s\n{out}")
    except Exception as e:
        build_result = (False, f"Build error: {e}")


def count_tests(output):
    """Count tests from JUnit XML result files, falling back to Gradle output."""
    import glob
    import xml.etree.ElementTree as ET
    suites = glob.glob("/workspace/app/build/test-results/**/*.xml", recursive=True)
    total = failed = errors = skipped = 0
    for path in suites:
        try:
            root = ET.parse(path).getroot()
            total += int(root.get("tests", 0))
            failed += int(root.get("failures", 0))
            errors += int(root.get("errors", 0))
            skipped += int(root.get("skipped", 0))
        except Exception:
            pass
    if total > 0:
        passed = total - failed - errors - skipped
        return f"{passed} tests passed · {failed} failed · {errors} errors · {skipped} skipped (across {len(suites)} suites)"
    # Fallback: scan Gradle output
    for line in output.splitlines():
        low = line.lower()
        if "tests completed" in low or "tests passed" in low or "tests failed" in low:
            return line.strip()
    return ""


def generate_html():
    if build_result is None:
        elapsed = int(time.time() - build_started) if build_started else 0
        status = "🔄 Building & Testing…"
        status_class = "building"
        refresh = '<meta http-equiv="refresh" content="10">'
        body = f"<p>Compiling the project and running {339}+ JVM unit tests.</p>"
        body += f"<p>Elapsed: {elapsed}s — first run downloads Gradle and all dependencies, so this can take several minutes.</p>"
        output_text = "Build output will appear here when complete."
    else:
        success, output = build_result
        status = "✅ Build & Tests Passed" if success else "❌ Build Failed"
        status_class = "success" if success else "failure"
        refresh = ""
        test_line = count_tests(output)
        body = ""
        if test_line:
            body += f"<p class='test-count'><strong>{html.escape(test_line)}</strong></p>"
        body += "<div class='info'><h2>About This Project</h2>"
        body += "<p><strong>GENSINGO</strong> is a minimal, offline-first field app for wild American ginseng stewards: "
        body += "one map (flat or 3D), four buttons (Track, Find, Suggest, Layers), and a research model that can only "
        body += "talk about places the phone itself computed.</p>"
        body += "<p>This is a <strong>native Android app</strong> (Kotlin + Jetpack Compose) that builds to an APK. "
        body += "It cannot run in a web browser — it requires an Android device or emulator. "
        body += "This page shows the <strong>build and unit test results</strong> to verify the code compiles and the tests pass.</p>"
        body += "<p><strong>Stack:</strong> Kotlin 2.2.10 · Jetpack Compose · AGP 8.11.2 · Room 2.7.1 · MapLibre Android 13.6.1</p>"
        body += "<p><strong>Build APK:</strong> <code>./gradlew :app:assembleDebug</code> &nbsp; "
        body += "<strong>Run tests:</strong> <code>./gradlew :app:testDebugUnitTest</code></p>"
        body += "</div>"
        output_text = html.escape(output[-10000:])

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
{refresh}
<title>GENSINGO — Field Map</title>
<style>
  :root {{ --bg: #0f0f1e; --card: #1a1a2e; --text: #e0e0e0; --green: #4CAF50; --red: #f44336; --blue: #82b1ff; }}
  * {{ box-sizing: border-box; }}
  body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
         max-width: 920px; margin: 0 auto; padding: 32px 20px; background: var(--bg); color: var(--text); line-height: 1.6; }}
  h1 {{ color: var(--green); margin-bottom: 4px; }}
  .subtitle {{ color: #888; margin-top: 0; }}
  .status {{ font-size: 24px; font-weight: 700; padding: 18px 24px; border-radius: 10px; margin: 24px 0; text-align: center; }}
  .building {{ background: #1a1a3a; color: #82b1ff; border: 1px solid #304060; }}
  .success {{ background: #1b3a1b; color: var(--green); border: 1px solid #2d5a2d; }}
  .failure {{ background: #3a1b1b; color: var(--red); border: 1px solid #5a2d2d; }}
  .test-count {{ font-size: 18px; color: var(--green); }}
  .info {{ background: var(--card); padding: 20px 24px; border-radius: 10px; margin: 24px 0; }}
  .info h2 {{ color: var(--blue); margin-top: 0; }}
  .info p {{ margin: 10px 0; }}
  code {{ background: #0d0d1a; padding: 2px 8px; border-radius: 4px; font-size: 13px; color: #c5cae9; }}
  pre {{ background: #0d0d1a; padding: 20px; border-radius: 10px; overflow-x: auto;
         max-height: 520px; overflow-y: auto; font-size: 12.5px; line-height: 1.5;
         font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace; color: #b0b0b0;
         white-space: pre-wrap; word-break: break-word; }}
  h2 {{ color: var(--blue); }}
</style>
</head>
<body>
  <h1>🌿 GENSINGO</h1>
  <p class="subtitle">High-performance GIS visual field tool and AI micro-habitat prospector for wild American ginseng.</p>
  <div class="status {status_class}">{status}</div>
  {body}
  <h2>Build Output</h2>
  <pre>{output_text}</pre>
</body>
</html>"""


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        content = generate_html().encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    # Ensure local.properties points to the SDK in the container
    with open("/workspace/local.properties", "w") as f:
        f.write("sdk.dir=/opt/android-sdk\n")

    # Start the build in a background thread
    t = threading.Thread(target=run_build, daemon=True)
    t.start()

    print(f"Serving GENSINGO status page on 0.0.0.0:{PORT}")
    server = HTTPServer(("0.0.0.0", PORT), Handler)
    server.serve_forever()
