@echo off
setlocal
cd /d "%~dp0"

title RTSBuilding Fabric 1.21.1 Client

set "RTS_JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
if not exist "%RTS_JAVA_HOME%\bin\java.exe" (
    echo [RTSBuilding] Java 21 was not found at:
    echo [RTSBuilding] %RTS_JAVA_HOME%
    echo [RTSBuilding] Install Java 21 or update RTS_JAVA_HOME in this script.
    pause
    exit /b 1
)

set "JAVA_HOME=%RTS_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

for %%I in ("%~dp0..\RTSbuilding\.gradle-user-home") do set "RTS_SHARED_GRADLE_HOME=%%~fI"
if exist "%RTS_SHARED_GRADLE_HOME%" set "GRADLE_USER_HOME=%RTS_SHARED_GRADLE_HOME%"

echo [RTSBuilding] Starting the Fabric 1.21.1 development client...
echo [RTSBuilding] Game directory: %CD%\run\client
echo [RTSBuilding] Java: %JAVA_HOME%
if defined GRADLE_USER_HOME echo [RTSBuilding] Gradle cache: %GRADLE_USER_HOME%

call "%CD%\gradlew.bat" runClient --no-daemon --no-configuration-cache %*
set "RTS_EXIT_CODE=%ERRORLEVEL%"

if not "%RTS_EXIT_CODE%"=="0" (
    echo [RTSBuilding] runClient failed with exit code %RTS_EXIT_CODE%.
    pause
)

exit /b %RTS_EXIT_CODE%
