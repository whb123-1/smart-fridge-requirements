@echo off
setlocal
set "MVN_VERSION=3.9.9"
set "MVN_DIR=%~dp0.mvn\apache-maven-%MVN_VERSION%"
if not exist "%MVN_DIR%\bin\mvn.cmd" (
  echo [mvnw] Downloading Apache Maven %MVN_VERSION% ...
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ProgressPreference='SilentlyContinue';" ^
    "$url='https://archive.apache.org/dist/maven/maven-3/%MVN_VERSION%/binaries/apache-maven-%MVN_VERSION%-bin.zip';" ^
    "$zip=Join-Path $env:TEMP 'apache-maven-%MVN_VERSION%-bin.zip';" ^
    "Invoke-WebRequest -Uri $url -OutFile $zip;" ^
    "Expand-Archive -Path $zip -DestinationPath '%~dp0.mvn' -Force"
)
call "%MVN_DIR%\bin\mvn.cmd" %*
