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

# The installer wears the client's version (mod_version). macOS rejects an app version starting with 0, so the app bundle's own
# metadata says 1.x.y for 0.x.y; the number shown in the window and in the update feed is the real one.
VERSION="$(sed -n 's/^mod_version=//p' gradle.properties)"
BUNDLE_VERSION="$VERSION"
if [[ "$(uname -s)" == "Darwin" && "$VERSION" == 0.* ]]; then BUNDLE_VERSION="1.${VERSION#0.}"; fi
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
COMMON=(--name "$NAME" --app-version "$BUNDLE_VERSION" --input installer/build/fat --main-jar RelictInstaller.jar
        --main-class relict.installer.MainKt --dest "$STAGE" --vendor Relict
        --description "Installs Relict Client into your Minecraft launchers"
        --java-options --enable-native-access=ALL-UNNAMED
        --add-modules java.base,java.desktop,java.logging,java.management,java.net.http,java.sql,java.prefs,java.naming,java.xml,jdk.unsupported,jdk.jsobject,jdk.xml.dom,jdk.jfr)

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
