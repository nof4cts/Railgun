@echo off
setlocal enabledelayedexpansion
title Iron Lung - build and install
cd /d "%~dp0"

echo.
echo  IRON LUNG  ^|  Forge 1.20.1 mod build
echo  ------------------------------------
echo  The first build downloads Forge and Minecraft and decompiles them.
echo  That can take 5-15 minutes. Later builds take under a minute.
echo.

rem ---- find a Java runtime to launch Gradle (Gradle fetches JDK 17 itself for compiling)
set "JAVA_OK="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_OK=1"
if not defined JAVA_OK (
    where java >nul 2>nul && set "JAVA_OK=1"
)
if not defined JAVA_OK (
    for %%D in (
        "%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime\java-runtime-gamma\windows-x64\java-runtime-gamma"
        "%ProgramFiles(x86)%\Minecraft Launcher\runtime\java-runtime-gamma\windows-x64\java-runtime-gamma"
        "%ProgramFiles%\Minecraft Launcher\runtime\java-runtime-gamma\windows-x64\java-runtime-gamma"
        "%APPDATA%\.minecraft\runtime\java-runtime-gamma\windows-x64\java-runtime-gamma"
        "%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-gamma\windows-x64\java-runtime-gamma"
    ) do (
        if not defined JAVA_OK if exist "%%~D\bin\java.exe" (
            set "JAVA_HOME=%%~D"
            set "JAVA_OK=1"
            echo  Using Minecraft's bundled Java: %%~D
        )
    )
)
if not defined JAVA_OK (
    echo  Could not find Java. Install Java 17 from https://adoptium.net and run this again.
    pause
    exit /b 1
)

call gradlew.bat build --no-daemon
if errorlevel 1 (
    echo.
    echo  BUILD FAILED. Copy the error text above and send it back so it can be fixed.
    pause
    exit /b 1
)

set "MODS=%APPDATA%\.minecraft\mods"
if not exist "%MODS%" mkdir "%MODS%"
for %%F in (build\libs\ironlung-*.jar) do (
    echo %%~nF | findstr /i "sources slim" >nul || (
        copy /y "%%F" "%MODS%\" >nul
        echo.
        echo  Installed %%~nxF into %MODS%
    )
)
echo.
echo  Launch your forge 1.20.1 profile, craft or grab "Dive Orders", and use them.
pause
