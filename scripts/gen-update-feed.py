#!/usr/bin/env python3
"""Writes dist/update.json, the file the installer reads to find newer Relict builds.

It is the installer's manifest plus, for every Minecraft version, where its Relict jar is downloaded from and the jar's
SHA-512. Run scripts/build-all.sh first (it fills dist/), then upload dist/update.json and the jars to the same https
host, and put the address of update.json in installer/src/main/resources/update.properties before building the installer.

    python3 scripts/gen-update-feed.py https://example.com/relict [--notes "What changed"]

The first argument is the folder the jars will be uploaded to (no trailing slash).

To announce a newer installer app as well, pass the files package.sh made (the CI run's artifacts) and upload them to
the same folder. The installer then offers them to people running an older installer:

    python3 scripts/gen-update-feed.py https://example.com/relict \\
        --mac "installer/build/package/Relict Installer-1.3.0.dmg" --windows Relict-Installer-windows.zip

The installer's version is the one in installer/src/main/resources/installer.properties.
"""
import argparse
import hashlib
import json
import pathlib
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "installer" / "src" / "main" / "resources" / "manifest.json"
DIST = ROOT / "dist"
INSTALLER_PROPERTIES = ROOT / "installer" / "src" / "main" / "resources" / "installer.properties"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("base_url", help="https folder the jars are uploaded to")
    parser.add_argument("--notes", default=None, help="one line shown in the installer's update banner")
    parser.add_argument("--mac", type=pathlib.Path, help="the installer .dmg for macOS")
    parser.add_argument("--windows", type=pathlib.Path, help="the zipped installer for Windows")
    args = parser.parse_args()
    if not args.base_url.startswith("https://"):
        raise SystemExit("The base URL must start with https://")

    manifest = json.loads(MANIFEST.read_text())
    for entry in manifest["versions"]:
        jar = DIST / entry["relictJar"]
        if not jar.is_file():
            raise SystemExit(f"{jar} is missing. Run scripts/build-all.sh first.")
        entry["relictUrl"] = f"{args.base_url.rstrip('/')}/{entry['relictJar']}"
        entry["relictSha512"] = hashlib.sha512(jar.read_bytes()).hexdigest()
    if args.notes:
        manifest["notes"] = args.notes

    files = {}
    for platform, path in (("mac", args.mac), ("windows", args.windows)):
        if path is None:
            continue
        if not path.is_file():
            raise SystemExit(f"{path} is missing.")
        files[platform] = {
            "file": path.name,
            "url": f"{args.base_url.rstrip('/')}/{urllib.parse.quote(path.name)}",
            "sha512": hashlib.sha512(path.read_bytes()).hexdigest(),
        }
    if files:
        version = next(line.split("=", 1)[1].strip() for line in INSTALLER_PROPERTIES.read_text().splitlines() if line.startswith("version="))
        manifest["installer"] = {"version": version, "files": files}

    out = DIST / "update.json"
    out.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Wrote {out} for Relict {manifest['relictVersion']} ({len(manifest['versions'])} versions)")


if __name__ == "__main__":
    main()
