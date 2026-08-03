@echo off
setlocal
cd /d "%~dp0"

echo [RTSBuilding] Starting the clean Forge 1.12.2 development client...
echo [RTSBuilding] Game directory: %CD%\run\clean-client
echo [RTSBuilding] Gradle Daemon JVM is pinned to Java 25 by the repository.
call "%CD%\gradlew.bat" runClient --no-daemon --no-configuration-cache %*
set "RTS_EXIT_CODE=%ERRORLEVEL%"

if not "%RTS_EXIT_CODE%"=="0" (
    echo [RTSBuilding] runClient failed with exit code %RTS_EXIT_CODE%.
    pause
)
exit /b %RTS_EXIT_CODE%
