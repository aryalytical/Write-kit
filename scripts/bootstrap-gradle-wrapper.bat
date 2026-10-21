@echo off
setlocal
REM Bootstrap the official Gradle Wrapper when Gradle is installed on PATH.
REM Run this from the extracted OfflineIDE project root.
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle is not on PATH.
  echo Install Gradle 9.4.1, then run this script again.
  exit /b 1
)
gradle --version | findstr /C:"Gradle 9.4.1" >nul
if errorlevel 1 (
  echo Please install Gradle 9.4.1 and ensure that version is first on PATH.
  exit /b 1
)
gradle wrapper --gradle-version 9.4.1 --distribution-type bin
if errorlevel 1 exit /b 1
echo.
echo Wrapper generated. Verify with gradlew.bat --version
