@ECHO OFF
SETLOCAL
SET "BASE_DIR=%~dp0"
SET "MAVEN_VERSION=3.9.9"
SET "WRAPPER_DIR=%BASE_DIR%.mvn\wrapper"
SET "MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%"
SET "ARCHIVE=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%-bin.zip"
SET "URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"

IF NOT EXIST "%MAVEN_HOME%\bin\mvn.cmd" (
  IF NOT EXIST "%WRAPPER_DIR%" MKDIR "%WRAPPER_DIR%"
  powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -UseBasicParsing '%URL%' -OutFile '%ARCHIVE%'; Expand-Archive -LiteralPath '%ARCHIVE%' -DestinationPath '%WRAPPER_DIR%' -Force; Remove-Item -LiteralPath '%ARCHIVE%' -Force"
  IF ERRORLEVEL 1 EXIT /B 1
)

CALL "%MAVEN_HOME%\bin\mvn.cmd" %*
ENDLOCAL
