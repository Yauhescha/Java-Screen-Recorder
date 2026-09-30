@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-windows.ps1" -SkipInstaller
if errorlevel 1 (
  echo.
  echo Portable build failed.
  pause
  exit /b 1
)
echo.
echo Portable build finished.
pause
