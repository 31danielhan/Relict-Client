#!/usr/bin/env bash
# Packages the installer with jpackage, which bundles its own Java runtime so players need nothing installed.
#
#   installer/package.sh            builds for the system you run it on:
#     macOS    -> installer/build/package/Relict Installer.app and Relict-Installer-<version>.dmg
#     Windows  -> installer/build/package/Relict Installer/Relict Installer.exe (zipped by CI)
#
# jpackage cannot cross-compile: build the Windows version on Windows (see .github/workflows/release.yml).
# Run scripts/build-all.sh first so the Relict jars exist, and scripts/gen-installer-manifest.py if versions changed.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 25)"
  export JAVA_HOME
fi

# The installer app's own version (macOS rejects a leading 0), kept in installer.properties; unrelated to Relict's version.
VERSION="$(sed -n 's/^version=//p' installer/src/main/resources/installer.properties)"
NAME="Relict Installer"
BRANDING="$PWD/branding"
OUT="installer/build/package"

./gradlew -p installer test fatJar --console=plain
rm -rf "$OUT"
mkdir -p "$OUT"

# jpackage runs in a scratch folder: macOS's codesign rejects the Finder/iCloud metadata that folders
# such as ~/Desktop pick up. The finished files are copied into $OUT afterwards.
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
COMMON=(--name "$NAME" --app-version "$VERSION" --input installer/build/fat --main-jar RelictInstaller.jar
        --main-class relict.installer.MainKt --dest "$STAGE" --vendor Relict
        --description "Installs Relict Client into your Minecraft launchers"
        --java-options --enable-native-access=ALL-UNNAMED
        --add-modules java.base,java.desktop,java.logging,java.net.http,java.sql,java.prefs,java.naming,java.xml,jdk.unsupported)

case "$(uname -s)" in
  Darwin)
    "$JAVA_HOME/bin/jpackage" "${COMMON[@]}" --icon "$BRANDING/relict.icns" --type app-image --mac-package-name Relict
    "$JAVA_HOME/bin/jpackage" "${COMMON[@]}" --icon "$BRANDING/relict.icns" --type dmg --mac-package-name Relict
    ;;
  MINGW*|MSYS*|CYGWIN*)
    "$JAVA_HOME/bin/jpackage" "${COMMON[@]}" --icon "$BRANDING/relict.ico" --type app-image
    ;;
  *)
    "$JAVA_HOME/bin/jpackage" "${COMMON[@]}" --type app-image
    ;;
esac

ditto --noextattr "$STAGE" "$OUT" 2>/dev/null || cp -R "$STAGE"/. "$OUT"/

echo
echo "Packaged:"
ls -1 "$OUT"
