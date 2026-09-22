@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0bootstrap-solution.ps1" %*
exit /b %errorlevel%
