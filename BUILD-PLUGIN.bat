@echo off
setlocal
title LarpersCoin - Compilation du plugin
cd /d "%~dp0"
echo ==========================================
echo       LARPERS COIN - BUILD WINDOWS
echo ==========================================
echo.
where java >nul 2>nul
if errorlevel 1 (echo ERREUR : Java est introuvable. Installe un JDK 25. & goto :fail)
java -version
powershell -NoProfile -ExecutionPolicy Bypass -Command "$v = (& java -XshowSettings:properties -version 2>&1 | Select-String 'java.specification.version'); if ($v -notmatch '=\s*25') { Write-Host 'ERREUR : un JDK 25 est requis.'; exit 1 }"
if errorlevel 1 (echo ERREUR : installe un JDK 25 et configure JAVA_HOME. & goto :fail)
if not exist "plugin-source\build.gradle" (echo ERREUR : plugin-source\build.gradle introuvable. & goto :fail)
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle absent : telechargement de Gradle 9.2.1...
  if not exist ".tools" mkdir ".tools"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $z=Join-Path (Get-Location) '.tools\gradle-9.2.1-bin.zip'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-9.2.1-bin.zip' -OutFile $z; Expand-Archive -LiteralPath $z -DestinationPath (Join-Path (Get-Location) '.tools') -Force"
  if errorlevel 1 goto :fail
  set "GRADLE=%CD%\.tools\gradle-9.2.1\bin\gradle.bat"
) else (set "GRADLE=gradle")
echo.
echo Compilation en cours...
call "%GRADLE%" -p plugin-source --no-daemon clean build --stacktrace > BUILD-PLUGIN.log 2>&1
if errorlevel 1 (echo ECHEC. Consulte BUILD-PLUGIN.log & type BUILD-PLUGIN.log & goto :fail)
if not exist "plugins" mkdir "plugins"
copy /Y "plugin-source\build\libs\LarpersCoin-*.jar" "plugins\LarpersCoin.jar" >nul
if errorlevel 1 goto :fail
echo SUCCES : plugins\LarpersCoin.jar
echo Journal : BUILD-PLUGIN.log
goto :end
:fail
echo.
echo La compilation a echoue. La fenetre reste ouverte.
pause
exit /b 1
:end
echo.
pause
exit /b 0
