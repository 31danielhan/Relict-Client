#!/usr/bin/env python3
"""Writes installer/src/main/resources/manifest.json.

For every Minecraft version in versions/*.properties it pins the exact dependency files the installer
downloads (Fabric API, Fabric Language Kotlin, MCEF Modern) with the SHA-512 hashes Modrinth publishes,
and records which bundled Relict jar belongs to that version. Re-run it after changing a version number.

    python3 scripts/gen-installer-manifest.py
"""
import json
import pathlib
import subprocess
import time
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "installer" / "src" / "main" / "resources" / "manifest.json"


def read_properties(path):
    props = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


_project_versions = {}


def project_versions(slug):
    """Every fabric version of a Modrinth project, fetched once (and retried: the API rate-limits bursts)."""
    if slug not in _project_versions:
        # No game-version filter: the exact version number pins the file, and a dependency built for "26.1"
        # is still the right one for 26.1.2.
        query = urllib.parse.urlencode({"loaders": json.dumps(["fabric"])})
        url = f"https://api.modrinth.com/v2/project/{slug}/version?{query}"
        for attempt in range(5):
            # curl rather than urllib: it uses the system certificate store, which python.org builds of Python lack on macOS.
            result = subprocess.run(
                ["curl", "-fsSL", "-H", "User-Agent: relict-installer-manifest/1.0", url],
                capture_output=True, text=True,
            )
            if result.returncode == 0:
                _project_versions[slug] = json.loads(result.stdout)
                break
            time.sleep(3 * (attempt + 1))
        else:
            raise SystemExit(f"Could not fetch {slug} from Modrinth (curl exit {result.returncode})")
    return _project_versions[slug]


def modrinth_file(slug, version_number, minecraft):
    versions = project_versions(slug)
    for version in versions:
        if version["version_number"] == version_number:
            files = [f for f in version["files"] if f["primary"]] or version["files"]
            f = files[0]
            return {
                "id": slug,
                "file": f["filename"],
                "url": f["url"],
                "sha512": f["hashes"]["sha512"],
                "size": f["size"],
            }
    raise SystemExit(f"{slug} {version_number} not found on Modrinth (needed for Minecraft {minecraft})")


def main():
    defaults = read_properties(ROOT / "gradle.properties")
    entries = []
    for props_file in sorted((ROOT / "versions").glob("*.properties")):
        mc = props_file.stem
        props = {**defaults, **read_properties(props_file)}
        print(f"Minecraft {mc}")
        mods = [
            modrinth_file("fabric-api", props["fabric_api_version"], mc),
            modrinth_file("fabric-language-kotlin", props["fabric_kotlin_version"], mc),
            # MCEF Modern or CinemaMod's MCEF depending on the Minecraft version; mcef_id is its Modrinth slug.
            modrinth_file(props["mcef_id"], props["mcef_version"], mc),
        ]
        for mod in mods:
            print(f"  {mod['file']}")
        entries.append({
            "minecraft": mc,
            "fabricLoader": props["loader_version"],
            "relictJar": f"relict-mc{mc}-{props['mod_version']}.jar",
            "mods": mods,
        })

    manifest = {
        "relictVersion": defaults["mod_version"],
        "versions": sorted(entries, key=lambda e: [int(p) for p in e["minecraft"].split(".")], reverse=True),
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
