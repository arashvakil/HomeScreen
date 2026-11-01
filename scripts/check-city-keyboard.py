#!/usr/bin/env python3
"""Device regression: open city search first, then verify Back never traps the IME.

Run only against the separately installed preview. No preferences are changed.
"""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", required=True)
parser.add_argument("--package", default="nyc.arash.homescreen.studio.debug")
args = parser.parse_args()
if not args.package.endswith(".debug"):
    parser.error("Use a preview package ending in .debug")


def adb(*command):
    return subprocess.run(["adb", "-s", args.serial, *command], check=True, capture_output=True, text=True).stdout


def visible():
    result = re.search(r"mInputShown=(true|false)", adb("shell", "dumpsys", "input_method"))
    if not result:
        raise RuntimeError("Could not read keyboard visibility")
    return result.group(1) == "true"


def wait_visibility(expected):
    deadline = time.monotonic() + 6
    while time.monotonic() < deadline:
        if visible() == expected:
            return
        time.sleep(0.2)
    raise AssertionError(f"Keyboard visibility should be {expected}")


def hierarchy():
    path = "/data/local/tmp/homescreen-keyboard-check.xml"
    result = adb("shell", "uiautomator", "dump", path)
    if "dumped to" not in result:
        raise RuntimeError("UI hierarchy dump did not complete: " + result.strip())
    return ET.fromstring(adb("shell", "cat", path))


# Begin with a visible keyboard, selecting the editor explicitly if necessary.
if not visible():
    adb("shell", "input", "keyevent", "23")
wait_visibility(True)

# A real Back press must close the IME while keeping city search open.
adb("shell", "input", "keyevent", "4")
wait_visibility(False)
root = hierarchy()
editor = next((node for node in root.iter("node") if (node.get("class") == "android.widget.EditText" or node.get("content-desc") == "City or postal code") and node.get("package") == args.package), None)
if editor is None:
    raise AssertionError("Back must leave the city editor available")
time.sleep(1)
if visible():
    raise AssertionError("Keyboard reopened without selecting the editor")

# Explicitly selecting the editor can open it again, and Back must work again.
bounds = list(map(int, re.findall(r"\d+", editor.get("bounds", ""))))
if len(bounds) != 4:
    raise AssertionError("City editor has no usable bounds")
adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
if not visible():
    adb("shell", "input", "keyevent", "23")
wait_visibility(True)
adb("shell", "input", "keyevent", "4")
wait_visibility(False)
time.sleep(1)
if visible():
    raise AssertionError("Keyboard reopened after the second dismissal")

# Another Back returns to the settings screen instead of reopening the editor.
adb("shell", "input", "keyevent", "4")
root = hierarchy()
if not any(node.get("text") == "Home Settings" for node in root.iter("node")):
    raise AssertionError("Second Back did not return to Home Settings")
print("PASS: Back dismisses the keyboard, it stays closed, explicit reopen works, and Back returns to Settings.")
