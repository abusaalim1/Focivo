#!/usr/bin/env python3
"""Manifest risk-profile gate (Focivo v1.3.3).

Parses the MERGED AndroidManifest.xml produced by Gradle
(processDebugMainManifest / processReleaseMainManifest) and exits non-zero
if any high-risk permission from the banned list is present — including
permissions merged in silently from library dependencies.

Usage:
    python3 tools/manifest_risk_check.py <merged-manifest-path>

This is the CI companion of ManifestPermissionAuditTest.kt (unit test that
checks the SOURCE manifest). Both must agree.
"""

import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"

# High-risk permissions that must never ship in Focivo. Removing any of these
# from this list requires the owner's explicit sign-off — each one raises the
# chance of a Google Play Protect block on sideloaded installs.
BANNED_PERMISSIONS = {
    "android.permission.QUERY_ALL_PACKAGES",       # removed v1.3.3 (queries cover picker)
    "android.permission.KILL_BACKGROUND_PROCESSES", # removed v1.3.3 (unused)
    "android.permission.REQUEST_INSTALL_PACKAGES",  # removed v1.3.3 (system installer flow)
    "android.permission.REQUEST_DELETE_PACKAGES",
    "android.permission.CALL_PHONE",
    "android.permission.SEND_SMS",
    "android.permission.READ_SMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.SEND_RESPOND_VIA_MESSAGE",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.GET_ACCOUNTS",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.RECORD_AUDIO",
    "android.permission.CAMERA",
    "android.permission.READ_CALL_LOG",
    "android.permission.WRITE_CALL_LOG",
    "android.permission.READ_PHONE_STATE",
    "android.permission.READ_PHONE_NUMBERS",
    "android.permission.ADD_VOICEMAIL",
    "android.permission.PROCESS_OUTGOING_CALLS",
    "android.permission.ANSWER_PHONE_CALLS",
    "android.permission.BODY_SENSORS",
    "android.permission.ACTIVITY_RECOGNITION",
}

# Sensitive-but-legitimate: declared because the guard genuinely needs them.
# Printed for visibility only — they do NOT fail the check.
WATCHLIST_PERMISSIONS = {
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.ACCESS_NOTIFICATION_POLICY",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
}


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: manifest_risk_check.py <merged-manifest-path>", file=sys.stderr)
        return 2
    path = sys.argv[1]
    try:
        root = ET.parse(path).getroot()
    except Exception as e:  # noqa: BLE001
        print(f"ERROR: could not parse {path}: {e}", file=sys.stderr)
        return 2

    declared = set()
    for perm in root.iter("uses-permission"):
        name = perm.get(f"{{{ANDROID_NS}}}name")
        if name:
            declared.add(name)

    print(f"Manifest risk check — {path}")
    print(f"Declared permissions ({len(declared)}):")
    for name in sorted(declared):
        marker = " [WATCHLIST - sensitive but justified]" if name in WATCHLIST_PERMISSIONS else ""
        print(f"  - {name}{marker}")

    violations = sorted(declared & BANNED_PERMISSIONS)
    if violations:
        print("\nFAIL: high-risk permissions present in merged manifest:")
        for name in violations:
            print(f"  !! {name}")
        print(
            "\nThese permissions were removed in v1.3.3 to reduce the Play "
            "Protect risk profile. Do NOT ship with them.",
            file=sys.stderr,
        )
        return 1

    # Sanity: no debuggable in release-type merged manifests.
    app = root.find("application")
    if app is not None and app.get(f"{{{ANDROID_NS}}}debuggable") == "true":
        print("\nFAIL: android:debuggable=\"true\" in merged manifest.", file=sys.stderr)
        return 1

    print("\nPASS: no high-risk permissions in merged manifest.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
