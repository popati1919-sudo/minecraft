$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$servers = @(
    @{ Name = "Lobby"; Dir = "Lobby"; Jar = "paper.jar"; Heap = "-Xms512M -Xmx1G"; Nogui = $true },
    @{ Name = "SMP"; Dir = "SMP"; Jar = "paper.jar"; Heap = "-Xms1G -Xmx4G"; Nogui = $true },
    @{ Name = "Velocity"; Dir = "Velocity"; Jar = "velocity.jar"; Heap = "-Xms512M -Xmx1G"; Nogui = $false }
)
$processes = @{}

function Start-One($server) {
    $folder = Join-Path $root $server.Dir
    $jar = Join-Path $folder $server.Jar
    if (!(Test-Path $jar)) { throw "JAR introuvable: $jar" }
    $logDir = Join-Path $folder "logs"
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $log = Join-Path $logDir "superviseur-console.log"
    $nogui = if ($server.Nogui) { " --nogui" } else { "" }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "cmd.exe"
    $psi.Arguments = '/c java ' + $server.Heap + ' -jar "' + $jar + '"' + $nogui + ' >> "' + $log + '" 2>&1'
    $psi.WorkingDirectory = $folder
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.CreateNoWindow = $true
    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi
    [void]$proc.Start()
    Write-Host ("Demarrage {0} (PID {1})" -f $server.Name, $proc.Id)
    return $proc
}

function Stop-All {
    foreach ($server in $servers) {
        $proc = $processes[$server.Name]
        if ($null -ne $proc -and !$proc.HasExited) {
            try { $proc.StandardInput.WriteLine("stop"); $proc.StandardInput.Flush() } catch {}
        }
    }
    $deadline = (Get-Date).AddSeconds(60)
    do {
        $alive = @($processes.Values | Where-Object { $null -ne $_ -and !$_.HasExited })
        if ($alive.Count -eq 0) { break }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    foreach ($proc in $processes.Values) {
        if ($null -ne $proc -and !$proc.HasExited) { try { $proc.Kill() } catch {} }
        if ($null -ne $proc) { $proc.Dispose() }
    }
    $processes.Clear()
}

try {
    while ($true) {
        foreach ($server in $servers) {
            $processes[$server.Name] = Start-One $server
            Start-Sleep -Seconds 8
        }
        Write-Host "Reseau actif. Ctrl+C arrete le superviseur."
        $restart = $false
        while (!$restart) {
            $request = Join-Path $root "restart.request"
            if (Test-Path $request) {
                Remove-Item $request -Force
                Write-Host "Commande /redemarrer recue."
                $restart = $true
                break
            }
            foreach ($server in $servers) {
                $proc = $processes[$server.Name]
                if ($null -eq $proc -or $proc.HasExited) {
                    Write-Warning ($server.Name + " s'est arrete; redemarrage complet.")
                    $restart = $true
                    break
                }
            }
            Start-Sleep -Seconds 1
        }
        Stop-All
        Write-Host "Tous les serveurs sont arretes. Redemarrage dans 5 secondes..."
        Start-Sleep -Seconds 5
    }
}
finally {
    Stop-All
}
