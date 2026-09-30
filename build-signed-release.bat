@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-release-package.ps1" -RequireSignature
if errorlevel 1 (
  echo.
  echo Signed release package build failed.
  pause
  exit /b 1
)
echo.
echo Signed release ZIP + update.json finished in release-out.
pause
