@echo off
setlocal EnableExtensions

set "PACK_URL=%~1"
if not defined PACK_URL set "PACK_URL=https://amoxcine.github.io/pack.toml"

set "SCRIPT_DIR=%~dp0"
set "OUTPUT_DIR=%SCRIPT_DIR%server-upload"
set "CACHE_DIR=%SCRIPT_DIR%.cache"
set "INSTALLER=%CACHE_DIR%\packwiz-installer-bootstrap.jar"
set "INSTALLER_URL=https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/packwiz-installer-bootstrap.jar"
set "INSTALLER_SHA256=A8FBB24DC604278E97F4688E82D3D91A318B98EFC08D5DBFCBCBCAB6443D116C"

where java >nul 2>nul
if errorlevel 1 (
  echo [ERREUR] Java est introuvable dans le PATH.
  echo Installe Java puis relance ce script.
  exit /b 1
)

set "JAVA_VERSION="
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i /c:"version"') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~V"
echo Java detecte : %JAVA_VERSION%
echo Note : le serveur Minecraft NeoForge 1.21.1 doit etre lance avec Java 21.

if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
if errorlevel 1 exit /b 1

if not exist "%INSTALLER%" (
  echo Telechargement de packwiz-installer-bootstrap...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing -Uri '%INSTALLER_URL%' -OutFile '%INSTALLER%'"
  if errorlevel 1 (
    echo [ERREUR] Impossible de telecharger l'installateur Packwiz.
    exit /b 1
  )
)

powershell -NoProfile -Command "$sha=[Security.Cryptography.SHA256]::Create(); $stream=[IO.File]::OpenRead('%INSTALLER%'); try { $actual=[BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','') } finally { $stream.Dispose(); $sha.Dispose() }; if ($actual -ne '%INSTALLER_SHA256%') { exit 1 }"
if errorlevel 1 (
  echo [ERREUR] La somme SHA-256 de l'installateur Packwiz est incorrecte.
  del /q "%INSTALLER%" >nul 2>nul
  exit /b 1
)

if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
if errorlevel 1 exit /b 1

echo.
echo Preparation des fichiers serveur depuis :
echo %PACK_URL%
echo.

pushd "%OUTPUT_DIR%"
java -jar "%INSTALLER%" -g -s server "%PACK_URL%"
set "INSTALL_RESULT=%ERRORLEVEL%"
popd

if not "%INSTALL_RESULT%"=="0" (
  echo [ERREUR] Packwiz n'a pas pu preparer le dossier serveur.
  exit /b %INSTALL_RESULT%
)

echo.
echo [OK] Dossier serveur prepare :
echo %OUTPUT_DIR%
echo.
echo Transfere le contenu de ce dossier avec WinSCP vers la racine du serveur.
exit /b 0
