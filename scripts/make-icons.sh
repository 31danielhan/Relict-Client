#!/usr/bin/env bash
# Turns branding/relict-logo.svg into the files that need a picture:
#   branding/relict.icns   the macOS app icon (jpackage)
#   branding/relict.ico    the Windows app icon (jpackage)
#   branding/relict-256.png
#   src/main/resources/assets/relict/icon.png   the icon the mod shows in Mod Menu
# Needs macOS (iconutil, sips). The results are committed, so other systems don't have to run it.
# Run scripts/gen-logo.py first if the logo itself changed.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 25)"
  export JAVA_HOME
fi

./gradlew -p installer fatJar --console=plain -q
JAR=installer/build/fat/RelictInstaller.jar
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

render() { "$JAVA_HOME/bin/java" -Djava.awt.headless=true --enable-native-access=ALL-UNNAMED -jar "$JAR" --export-icon "$1" "$2"; }

ICONSET="$WORK/relict.iconset"
mkdir -p "$ICONSET"
for size in 16 32 128 256 512; do
  render "$ICONSET/icon_${size}x${size}.png" "$size"
  render "$ICONSET/icon_${size}x${size}@2x.png" "$((size * 2))"
done
iconutil -c icns "$ICONSET" -o branding/relict.icns

# A .ico can hold PNG images directly (Windows Vista and later).
for size in 16 32 48 64 128 256; do render "$WORK/ico-$size.png" "$size"; done
python3 - "$WORK" <<'PY'
import struct, sys, pathlib
work = pathlib.Path(sys.argv[1])
sizes = [16, 32, 48, 64, 128, 256]
images = [(s, (work / f"ico-{s}.png").read_bytes()) for s in sizes]
header = struct.pack("<HHH", 0, 1, len(images))
offset = 6 + 16 * len(images)
entries, data = b"", b""
for s, png in images:
    entries += struct.pack("<BBBBHHII", s % 256, s % 256, 0, 0, 1, 32, len(png), offset + len(data))
    data += png
pathlib.Path("branding/relict.ico").write_bytes(header + entries + data)
PY

render branding/relict-256.png 256
mkdir -p src/main/resources/assets/relict
render src/main/resources/assets/relict/icon.png 128
ls -la branding src/main/resources/assets/relict
