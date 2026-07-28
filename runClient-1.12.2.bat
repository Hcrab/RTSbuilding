@echo off
setlocal
cd /d "%~dp0"

set "RTS_GRADLE_JAVA=C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot"
if not exist "%RTS_GRADLE_JAVA%\bin\java.exe" (
    echo [RTSBuilding] Missing Gradle JDK: %RTS_GRADLE_JAVA%
    echo Install Microsoft OpenJDK 25 or update RTS_GRADLE_JAVA in this file.
    pause
    exit /b 1
)

set "JAVA_HOME=%RTS_GRADLE_JAVA%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GRADLE_USER_HOME=E:\RTSbuilding\.gradle-user-home"
set "RTS_GRADLE_BIN=C:\Users\ping\.gradle\wrapper\dists\gradle-9.6.1-bin\4qom42a8kjpr8i5tyvfeebwkm\gradle-9.6.1\bin\gradle.bat"
set "RTS_TOOLCHAINS=E:/RTSbuilding/.gradle-user-home/jdks/eclipse_adoptium-17-amd64-windows/jdk-17.0.19+10,C:/Program Files/Microsoft/jdk-21.0.11.10-hotspot,E:/RTSbuilding-1.12.2-forge/.toolchains/zulu8.96.0.19-ca-jdk8.0.502-win_x64"
if not exist "%RTS_GRADLE_BIN%" set "RTS_GRADLE_BIN=%CD%\gradlew.bat"

echo [RTSBuilding] Starting the clean Forge 1.12.2 development client...
echo [RTSBuilding] Game directory: %CD%\run\clean-client
call "%RTS_GRADLE_BIN%" ^
    "-Dorg.gradle.java.installations.paths=%RTS_TOOLCHAINS%" ^
    "-Dorg.gradle.java.installations.auto-download=false" ^
    runClient --no-daemon --no-configuration-cache %*
set "RTS_EXIT_CODE=%ERRORLEVEL%"

if not "%RTS_EXIT_CODE%"=="0" (
    echo [RTSBuilding] runClient failed with exit code %RTS_EXIT_CODE%.
    pause
)
exit /b %RTS_EXIT_CODE%
