#!/usr/bin/env python3
"""Stops a release whose version numbers disagree: the tag, gradle.properties and the installer's manifest.

    python3 scripts/check-release.py v0.2.0

The jars are named after mod_version, the installer compares versions against the manifest's relictVersion, and the
update feed is built from that manifest, so all of them have to say the same thing. Run
scripts/gen-installer-manifest.py (needs network) after changing mod_version, and commit the result.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent


def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: check-release.py <tag, e.g. v0.2.0>")
    tag = sys.argv[1]
    mod_version = re.search(r"^mod_version=(\S+)", (ROOT / "gradle.properties").read_text(), re.M).group(1)
    manifest = json.loads((ROOT / "installer/src/main/resources/manifest.json").read_text())

    problems = []
    if tag != f"v{mod_version}":
        problems.append(f"the tag is {tag} but gradle.properties has mod_version={mod_version}")
    if manifest["relictVersion"] != mod_version:
        problems.append(f"the installer manifest says {manifest['relictVersion']}: run scripts/gen-installer-manifest.py")
    for entry in manifest["versions"]:
        if mod_version not in entry["relictJar"]:
            problems.append(f"{entry['relictJar']} is not a {mod_version} jar: run scripts/gen-installer-manifest.py")
    if problems:
        raise SystemExit("Release check failed:\n- " + "\n- ".join(problems))
    print(f"Release {tag} is consistent ({len(manifest['versions'])} Minecraft versions).")


if __name__ == "__main__":
    main()
