#!/usr/bin/env bash
# Builds "UENav Admin" and "UENav Server" as apps with Java built in (jpackage app-image).
# On Windows each folder contains an .exe; on Linux/macOS a launcher for that system.
# jpackage only builds for the system it runs on, so the Windows .exe files are made by
# .github/workflows/windows.yml on a Windows machine at GitHub. Needs JDK 17+ and Maven.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=0.1.0-SNAPSHOT
rm -rf target/package && mkdir -p target/package/admin-input target/package/server-input target/package/dist

mvn -B -q install -DskipTests

# Admin app: its jar plus every library (JavaFX for this system included)
mvn -B -q -pl uenav-client dependency:copy-dependencies -DincludeScope=runtime \
    -DoutputDirectory="$PWD/target/package/admin-input"
cp "uenav-client/target/uenav-client-$VERSION.jar" target/package/admin-input/

# Server: the single runnable Spring Boot jar
cp "uenav-server/target/uenav-server-$VERSION.jar" target/package/server-input/

WIN_ADMIN=()
WIN_SERVER=()
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) WIN_SERVER=(--win-console) ;;   # the server shows its log in a window
esac

jpackage --type app-image --name "UENav Admin" --app-version 0.1.0 --vendor UENav \
    --input target/package/admin-input --main-jar "uenav-client-$VERSION.jar" \
    --main-class com.uenav.client.Launcher --dest target/package/dist "${WIN_ADMIN[@]}"

jpackage --type app-image --name "UENav Server" --app-version 0.1.0 --vendor UENav \
    --input target/package/server-input --main-jar "uenav-server-$VERSION.jar" \
    --dest target/package/dist "${WIN_SERVER[@]}"

echo "Done: target/package/dist"
ls target/package/dist
