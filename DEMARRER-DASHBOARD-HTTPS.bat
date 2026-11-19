@echo off
setlocal
cd /d "%~dp0"
echo ==========================================
echo Larry SMP - Dashboard HTTPS public
echo ==========================================
echo.
if not exist "cloudflared.exe" (
  echo Telechargement de cloudflared...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe' -OutFile 'cloudflared.exe'"
)
if not exist "cloudflared.exe" (
  echo ERREUR: impossible de telecharger cloudflared.exe
  pause
  exit /b 1
)
echo.
echo Le dashboard doit deja etre demarre sur ce serveur.
echo Une adresse HTTPS publique sera affichee ci-dessous.
echo Garde cette adresse et le jeton admin secrets.
echo Appuie sur CTRL+C pour fermer le tunnel.
echo.
cloudflared.exe tunnel --url http://127.0.0.1:8765
echo.
echo Le tunnel est ferme.
pause
