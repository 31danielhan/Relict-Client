#!/usr/bin/env bash
# Builds one Relict jar per Minecraft version listed in versions/ and collects them in dist/.
# Gradle itself needs Java 25+ (Fabric Loom); the mod compiles to Java 21.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 25)"
  export JAVA_HOME
fi

rm -rf dist
mkdir -p dist

for props in versions/*.properties; do
  mc="$(basename "$props" .properties)"
  echo "== Building for Minecraft $mc"
  ./gradlew build -PmcVersion="$mc" --console=plain
  cp build/libs/relict-mc"$mc"-*.jar dist/
  rm -f dist/*-sources.jar
done

echo
echo "Jars in dist/:"
ls -1 dist
