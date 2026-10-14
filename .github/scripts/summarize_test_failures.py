#!/usr/bin/env python3
"""Print concise details from Gradle's XML test reports for CI annotations."""

from pathlib import Path
import xml.etree.ElementTree as ET


report_dir = Path("app/build/test-results/testDebugUnitTest")
failures: list[str] = []

for report in sorted(report_dir.glob("TEST-*.xml")):
    try:
        test_cases = ET.parse(report).getroot().iter("testcase")
        for case in test_cases:
            failure = case.find("failure")
            if failure is None:
                failure = case.find("error")
            if failure is None:
                continue

            name = f"{case.get('classname', 'test')}.{case.get('name', 'unknown')}"
            message = failure.get("message", "").strip()
            stack = "".join(failure.itertext()).strip()
            entry = f"{name}: {message}"
            if stack and stack != message:
                entry += f"\n{stack}"
            failures.append(entry)
    except ET.ParseError as error:
        failures.append(f"Could not read {report}: {error}")

print("\n\n".join(failures)[:3000])
