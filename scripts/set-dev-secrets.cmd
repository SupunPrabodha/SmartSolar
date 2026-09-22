@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0set-dev-secrets.ps1" %*
exit /b %errorlevel%
