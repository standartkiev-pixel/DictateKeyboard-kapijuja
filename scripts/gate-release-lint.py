#!/usr/bin/env python3
"""Fail an RC when Android lint reports permission, lifecycle, or resource-leak risks.

The phone module inherits a large localization/API lint backlog from the upstream keyboard. The full
report is still generated on every RC, but unrelated historical findings must not hide the smaller set
that can crash a background service, leak an Android resource, or retain a UI/process object forever.
"""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET


REPORT = Path("app/build/reports/lint-results-debug.xml")
BLOCKING_IDS = {
    "CloseCursor",
    "CoroutineCreationDuringComposition",
    "ForegroundServicePermission",
    "HandlerLeak",
    "MissingPermission",
    "NotificationPermission",
    "Recycle",
    "SensorManagerLeak",
    "StaticFieldLeak",
    "Wakelock",
    "WifiManagerLeak",
}


def main() -> int:
    if not REPORT.is_file():
        print(f"Required lint report was not generated: {REPORT}", file=sys.stderr)
        return 1

    issues = ET.parse(REPORT).getroot().findall("issue")
    errors = [issue for issue in issues if issue.get("severity", "").lower() in {"error", "fatal"}]
    blocking = [issue for issue in issues if issue.get("id") in BLOCKING_IDS]

    print(f"Full phone lint report: {len(errors)} inherited error(s), {len(issues)} total finding(s).")
    if not blocking:
        print("Release-critical permission, lifecycle, and resource-leak lint gate passed.")
        return 0

    print("Release-critical lint findings:", file=sys.stderr)
    for issue in blocking:
        location = issue.find("location")
        where = "unknown location"
        if location is not None:
            where = f"{location.get('file', 'unknown')}:{location.get('line', '?')}"
        print(f"- {issue.get('id')}: {where}: {issue.get('message', '')}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
