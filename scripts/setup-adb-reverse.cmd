@echo off
setlocal

set "ADB="

:: 1. Check if adb is directly on PATH
where adb >nul 2>&1
if %ERRORLEVEL% equ 0 (
    set "ADB=adb"
    goto :found_adb
)

:: 2. Check default Windows Android Studio SDK path for current user
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set ADB="%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    goto :found_adb
)

:: 3. Check ANDROID_HOME environment variable if set
if defined ANDROID_HOME (
    if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
        set ADB="%ANDROID_HOME%\platform-tools\adb.exe"
        goto :found_adb
    )
)

:: 4. Check ANDROID_SDK_ROOT environment variable if set
if defined ANDROID_SDK_ROOT (
    if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" (
        set ADB="%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
        goto :found_adb
    )
)

echo [ERROR] adb.exe could not be found.
echo Please ensure Android Studio / Android SDK is installed.
echo Expected location: %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
pause
exit /b 1

:found_adb
echo Finding all connected Android devices...

for /f "skip=1 tokens=1,2" %%a in ('%ADB% devices') do (
    if "%%b"=="device" (
        echo Forwarding ports 5000 and 7001 to device %%a...
        %ADB% -s %%a reverse tcp:5000 tcp:5000 >nul 2>&1
        %ADB% -s %%a reverse tcp:7001 tcp:7001 >nul 2>&1
    )
)

echo.
echo [SUCCESS] Reverse port forwarding applied to connected devices:
%ADB% devices -l
endlocal
