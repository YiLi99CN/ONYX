@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
echo ============================================================
echo   [onyx - EY.ONYX.PE] One-Click APK Builder and Installer
echo ============================================================

if not exist "local.properties" (
    if exist "%~dp0\OpenOnyxAndroidProject\local.properties" (
        copy /y "%~dp0\OpenOnyxAndroidProject\local.properties" "local.properties" >nul
        echo [OK] Copied local.properties from previous project.
    ) else if exist "%LOCALAPPDATA%\Android\Sdk" (
        set "SDK_ESCAPED=%LOCALAPPDATA:\=\\%\\Android\\Sdk"
        echo sdk.dir=!SDK_ESCAPED!> "local.properties"
        echo [OK] Generated local.properties with %LOCALAPPDATA%\Android\Sdk
    )
)

if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    echo [OK] Found JAVA_HOME: !JAVA_HOME!
) else if exist "D:\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=D:\Android\Android Studio\jbr"
    echo [OK] Found JAVA_HOME: !JAVA_HOME!
)

set "GRADLE_CMD="
for /d %%D in ("%USERPROFILE%\.gradle\wrapper\dists\gradle-*") do (
    for /d %%H in ("%%D\*") do (
        for /d %%G in ("%%H\gradle-*") do (
            if exist "%%G\bin\gradle.bat" (
                set "GRADLE_CMD=%%G\bin\gradle.bat"
            )
        )
    )
)

if "!GRADLE_CMD!"=="" (
    echo [ERROR] Could not find gradle.bat in %USERPROFILE%\.gradle\wrapper\dists
    pause
    exit /b 1
)
echo [OK] Using Gradle: !GRADLE_CMD!

echo.
echo [STEP 1/2] Compiling onyx (EY.ONYX.PE) APK...
call "!GRADLE_CMD!" assembleDebug

if not exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo.
    echo [FAILED] APK was not generated. Please check the build output above.
    pause
    exit /b 1
)

echo.
echo [SUCCESS] APK Built: %~dp0app\build\outputs\apk\debug\app-debug.apk

set "ADB_EXE=adb"
if exist "C:\Users\admin\Downloads\platform-tools\adb.exe" (
    set "ADB_EXE=C:\Users\admin\Downloads\platform-tools\adb.exe"
) else if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB_EXE=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
)

echo.
echo [STEP 2/2] Installing EY.ONYX.PE to connected Emulator / Phone...
"!ADB_EXE!" install -r "app\build\outputs\apk\debug\app-debug.apk"
"!ADB_EXE!" shell am start -n EY.ONYX.PE/EY.ONYX.PE.MainActivity

explorer /select,"%~dp0app\build\outputs\apk\debug\app-debug.apk"
echo.
echo [DONE] APK folder opened and onyx launched!
pause
