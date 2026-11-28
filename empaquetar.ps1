<#
  Genera la aplicacion para Windows en dist\SV-Cobranza\ (SV-Cobranza.exe con Java y JavaFX incluidos).
  Requisitos: JDK 17 o superior (con jpackage) y Maven en el PATH.
  Logo (opcional): copia el archivo a src\main\resources\logo.jpg antes de empaquetar; queda
  incluido solo en tu copia, porque git lo ignora.
  Uso:  powershell -ExecutionPolicy Bypass -File .\empaquetar.ps1
#>
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

mvn -B -q package
if ($LASTEXITCODE -ne 0) { throw "Fallo la compilacion o las pruebas." }

if (Test-Path dist) { Remove-Item dist -Recurse -Force }
New-Item -ItemType Directory dist | Out-Null

jpackage --type app-image `
    --name "SV-Cobranza" `
    --input target `
    --main-jar sv-cobranza.jar `
    --main-class mx.tecmilenio.svcobranza.Lanzador `
    --dest dist `
    --app-version 2.0.0 `
    --vendor "Soplando Vida A.C."
if ($LASTEXITCODE -ne 0) { throw "Fallo jpackage." }

Write-Host "Listo: dist\SV-Cobranza\SV-Cobranza.exe (copia la carpeta completa al equipo destino)."
