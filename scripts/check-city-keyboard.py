#!/usr/bin/env python3
"""Open Home Preview's city entry, then verify it never opens/traps the system IME."""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", required=True)
args = parser.parse_args()


def adb(*command):
    return subprocess.run(["adb", "-s", args.serial, *command], check=True, capture_output=True, text=True).stdout


def assert_no_ime():
    state = adb("shell", "dumpsys", "input_method")
    if "mInputShown=false" not in state:
        raise AssertionError("City entry must not open the system keyboard")


def hierarchy():
    path = "/data/local/tmp/homescreen-keyboard-check.xml"
    result = adb("shell", "uiautomator", "dump", path)
    if "dumped to" not in result:
        raise RuntimeError("UI hierarchy dump did not complete: " + result.strip())
    return ET.fromstring(adb("shell", "cat", path))


def contains(root, text):
    return any(node.get("text") == text for node in root.iter("node"))


root = hierarchy()
if not contains(root, "Choose your city"):
    raise AssertionError("Open Home Preview → Settings → Choose a city first")
assert_no_ime()
adb("shell", "input", "keyevent", "22")
adb("shell", "input", "keyevent", "21")
adb("shell", "input", "keyevent", "4")
root = hierarchy()
if not contains(root, "Home Settings"):
    raise AssertionError("Back did not return directly to Home Settings")
assert_no_ime()

# Reopen the first weather setting (saved city or Choose a city), preserving its value.
weather = next(n for n in root.iter("node") if n.get("text") == "WEATHER")
weather_bottom = int(re.findall(r"\d+", weather.get("bounds"))[3])
buttons = [n for n in root.iter("node") if n.get("clickable") == "true" and n.get("bounds")]
buttons = [n for n in buttons if int(re.findall(r"\d+", n.get("bounds"))[1]) >= weather_bottom]
button = min(buttons, key=lambda n: int(re.findall(r"\d+", n.get("bounds"))[1]))
x1, y1, x2, y2 = map(int, re.findall(r"\d+", button.get("bounds")))
adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
root = hierarchy()
if not contains(root, "Choose your city"):
    raise AssertionError("City entry did not reopen")
time.sleep(1)
assert_no_ime()
adb("shell", "input", "keyevent", "4")
if not contains(hierarchy(), "Home Settings"):
    raise AssertionError("Repeated Back did not return to Settings")
assert_no_ime()
print("PASS: city entry/reopen never shows the system keyboard; Back repeatedly returns to Settings.")
