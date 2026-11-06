#!/usr/bin/env python3
"""A kiadás-repóba kerülő jelzőfájl (JSON) elkészítése.

Használat: kiadas.py <apk> <csomagnév> <verzió> <versionCode> <apk-url> <InfoActivity.java> <kimenet.json>

A változások listáját az InfoActivity.CHANGES tömb adott verziójú bejegyzéséből veszi.
Ha a verzió nincs benne, hibával leáll (így nem jelenhet meg változásnapló nélküli kiadás).
"""
import hashlib
import json
import os
import re
import sys

apk, app, ver, code, url, info, out = sys.argv[1:8]

src = open(info, encoding="utf-8").read()
start = src.find("CHANGES")
if start < 0:
    sys.exit("Az InfoActivity.java-ban nincs CHANGES tömb.")
m = re.search(r'\{\s*"' + re.escape(ver) + r'"\s*,(.*?)\}', src[start:], re.S)
if not m:
    sys.exit(f"A(z) {ver} verzió hiányzik az InfoActivity.CHANGES-ből.")
strings = [re.sub(r'\\(.)', r'\1', s) for s in re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))]
if len(strings) < 2:
    sys.exit(f"A(z) {ver} verzió bejegyzésében nincs dátum vagy változás.")
date, changes = strings[0], strings[1:]

h = hashlib.sha256()
with open(apk, "rb") as f:
    for chunk in iter(lambda: f.read(1 << 16), b""):
        h.update(chunk)

data = {
    "app": app,
    "version": ver,
    "versionCode": int(code),
    "date": date,
    "apk": url,
    "size": os.path.getsize(apk),
    "sha256": h.hexdigest(),
    "changes": changes,
}
with open(out, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
    f.write("\n")
print(json.dumps(data, ensure_ascii=False, indent=2))
