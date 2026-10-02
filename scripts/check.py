#!/usr/bin/env python3
"""Run the repository checks, including Robolectric behind an HTTP(S) proxy.

Set JAVA_HOME to a JDK 17 installation and ANDROID_HOME to an SDK containing
platforms;android-35 and build-tools;35.0.0. No signing credentials are required
for tests or the unsigned development APK. Additional arguments select tasks.
"""

import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlparse


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    environment = os.environ.copy()
    proxy = environment.get("HTTPS_PROXY") or environment.get("https_proxy")
    if proxy:
        parsed = urlparse(proxy)
        if parsed.scheme != "http" or not parsed.hostname:
            print("Java checks require an HTTP CONNECT proxy URL in HTTPS_PROXY.", file=sys.stderr)
            return 2
        # JAVA_TOOL_OPTIONS reaches forked test JVMs; Gradle-only properties do not.
        # Do not copy proxy userinfo into properties or print credential values.
        options = environment.get("JAVA_TOOL_OPTIONS", "")
        for protocol in ("https", "http"):
            options += f" -D{protocol}.proxyHost={parsed.hostname} -D{protocol}.proxyPort={parsed.port or 80}"
        environment["JAVA_TOOL_OPTIONS"] = options.strip()
    tasks = sys.argv[1:] or [
        ":foundation-contracts:check",
        ":app:testDebugUnitTest",
        ":app:assembleDebug",
    ]
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    return subprocess.call([wrapper, "--no-daemon", *tasks], cwd=root, env=environment)


if __name__ == "__main__":
    raise SystemExit(main())
