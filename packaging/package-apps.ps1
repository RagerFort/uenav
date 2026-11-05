# Builds "UENav Admin" and "UENav Server" for Windows, each a folder with an .exe and Java built in.
# Run from the project folder in PowerShell:   .\packaging\package-apps.ps1
# Result: target\package\dist\UENav Admin\UENav Admin.exe and target\package\dist\UENav Server\UENav Server.exe
# Needs JDK 17+ (jpackage is included in the JDK) and Maven.
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$version = "0.1.0-SNAPSHOT"
$pkg = "target\package"
if (Test-Path $pkg) { Remove-Item -Recurse -Force $pkg }
New-Item -ItemType Directory -Force "$pkg\admin-input", "$pkg\server-input", "$pkg\dist" | Out-Null

mvn -B -q install "-DskipTests"
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
mvn -B -q -pl uenav-client dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=$PWD\$pkg\admin-input"
if ($LASTEXITCODE -ne 0) { throw "Copying the admin app libraries failed" }
Copy-Item "uenav-client\target\uenav-client-$version.jar" "$pkg\admin-input\"
Copy-Item "uenav-server\target\uenav-server-$version.jar" "$pkg\server-input\"

jpackage --type app-image --name "UENav Admin" --app-version 0.1.0 --vendor UENav `
    --input "$pkg\admin-input" --main-jar "uenav-client-$version.jar" `
    --main-class com.uenav.client.Launcher --dest "$pkg\dist"
if ($LASTEXITCODE -ne 0) { throw "jpackage (admin) failed" }

jpackage --type app-image --name "UENav Server" --app-version 0.1.0 --vendor UENav `
    --input "$pkg\server-input" --main-jar "uenav-server-$version.jar" `
    --win-console --dest "$pkg\dist"
if ($LASTEXITCODE -ne 0) { throw "jpackage (server) failed" }

Write-Host "Done. Open target\package\dist"
