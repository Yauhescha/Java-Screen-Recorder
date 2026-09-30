@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\prepare-ffmpeg.ps1"
if errorlevel 1 pause & exit /b 1
mvn clean package
if errorlevel 1 pause & exit /b 1
java --enable-native-access=ALL-UNNAMED -jar target\java-screen-recorder-0.10.0-all.jar
