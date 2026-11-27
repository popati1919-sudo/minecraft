param([Parameter(Mandatory=$true)][string]$ReseauExistant)
$ErrorActionPreference = 'Stop'
$oldRoot = (Resolve-Path -LiteralPath $ReseauExistant).Path
if ($oldRoot -eq $PSScriptRoot) { throw 'Extraire le nouveau ZIP dans un dossier distinct du reseau existant.' }
$targets = @('Lobby','SMP')
foreach ($server in $targets) {
    $old = Join-Path $oldRoot $server
    foreach ($path in @("$old/plugins/LarpersCoin.jar","$old/plugins/LarpersCoin","$PSScriptRoot/$server/plugins/LarpersCoin.jar")) {
        if (!(Test-Path -LiteralPath $path)) { throw "Element requis absent: $path" }
    }
    if (Test-Path "$old/world/session.lock") {
        $lock = [System.IO.File]::Open("$old/world/session.lock",'Open','ReadWrite','None')
        $lock.Dispose()
    }
}
$backup = Join-Path $oldRoot ("Sauvegardes/Plugin-" + [guid]::NewGuid().ToString('N'))
foreach ($server in $targets) {
    $dest = New-Item -ItemType Directory -Path "$backup/$server" -Force
    Copy-Item -LiteralPath "$oldRoot/$server/plugins/LarpersCoin" -Destination $dest.FullName -Recurse
    Copy-Item -LiteralPath "$oldRoot/$server/plugins/LarpersCoin.jar" -Destination $dest.FullName
}
foreach ($server in $targets) {
    $jar = "$oldRoot/$server/plugins/LarpersCoin.jar"
    $tmp = "$jar.new"
    Copy-Item -LiteralPath "$PSScriptRoot/$server/plugins/LarpersCoin.jar" -Destination $tmp -Force
    [System.IO.File]::Replace($tmp,$jar,"$backup/$server/remplacement.jar")
}
Write-Host "Mise a jour terminee. Sauvegarde: $backup"
