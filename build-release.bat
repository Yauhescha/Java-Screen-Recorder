@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-release-package.ps1"
if errorlevel 1 (
  echo.
  echo Release package build failed.
  pause
  exit /b 1
)
echo.
echo Release ZIP + update.json finished in release-out.
pause
