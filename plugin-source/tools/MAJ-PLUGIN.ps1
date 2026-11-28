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
# Conserver les options et les personnalisations des lanceurs existants.
$utf8 = New-Object System.Text.UTF8Encoding($false)
foreach ($launcher in @('START-NETWORK.bat','REDEMARRER-SERVEUR.ps1')) {
    $path = Join-Path $oldRoot $launcher
    if (!(Test-Path -LiteralPath $path)) { continue }
    $original = [IO.File]::ReadAllText($path)
    $updated = $original
    foreach ($server in $targets) {
        $heap = Join-Path "$oldRoot/$server" 'larry-heap.args'
        $pattern = "(-Dlarpers\.server=$server\s+)-Xms\d+[kKmMgG]?\s+(-Xmx\d+[kKmMgG]?)"
        $match = [regex]::Match($updated,$pattern)
        if (!(Test-Path -LiteralPath $heap)) {
            $setting = if ($match.Success) { $match.Groups[2].Value } else { [IO.File]::ReadAllText("$PSScriptRoot/$server/larry-heap.args").Trim() }
            [IO.File]::WriteAllText($heap,$setting,$utf8)
        }
        $updated = [regex]::Replace($updated,$pattern,'${1}-Xms512M @larry-heap.args')
    }
    if ($updated -ne $original) {
        [IO.Directory]::CreateDirectory($backup) | Out-Null
        Copy-Item -LiteralPath $path -Destination "$backup/$launcher"
        [IO.File]::WriteAllText($path,$updated,$utf8)
    }
}
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
