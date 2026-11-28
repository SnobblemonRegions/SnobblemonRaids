param([ValidateRange(0,600)][int]$GatheringSeconds = 45,
      [ValidateRange(8,16)][int]$ViewDistance = 8)
# Prepares and launches a fresh loopback-only disposable fixture from the tested artifact.
$ErrorActionPreference = 'Stop'
$sealWorkspace = 'C:\Users\Facel\SnobblemonRaids'
$sealRuntime = Join-Path $sealWorkspace 'build\latest-mods-2026-11-26\runtime'
$sealCore = Join-Path $sealWorkspace 'build\latest-mods-2026-11-26\SnobblemonRaids\build\libs\CobbleRaids-0.8.211-worldboss-playtest.jar'
$sealAscension = Join-Path $sealWorkspace 'build\latest-mods-2026-11-26\AscensionLib\ascensionlib\build\libs\AscensionLib-1.0.0+fabric.jar'
$sealRunner = Join-Path $sealWorkspace 'build\latest-mods-2026-11-26\SnobblemonRaids\validation\smoke\worldboss_rehearsal.py'
$sealJava = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.1+1\bin\java.exe'
$sealClient = Join-Path $sealWorkspace 'build\latest-mods-2026-11-26\client'
$sealClientRecord = Get-Content -LiteralPath (Join-Path $sealClient 'client-process.json') -Raw | ConvertFrom-Json
if ($sealClientRecord.server -ne '127.0.0.1:25741' -or $sealClientRecord.username -ne 'WorldBossTester') { throw 'Unexpected preview client identity.' }
$sealClientProcess = Get-CimInstance Win32_Process -Filter ('ProcessId=' + [int]$sealClientRecord.pid) -ErrorAction SilentlyContinue
if ($sealClientProcess -and $sealClientProcess.CommandLine -and $sealClientProcess.CommandLine.Contains($sealClientRecord.arguments_file)) { throw 'Close the private preview client before running this launcher.' }
$sealArtifacts = Get-Content -LiteralPath (Join-Path $sealWorkspace 'build\latest-mods-2026-11-26\artifacts.json') -Raw | ConvertFrom-Json
$sealCoreRecord = @($sealArtifacts | Where-Object { $_.id -eq 'cobbleraids' })
if ($sealCoreRecord.Count -ne 1 -or (Get-FileHash -LiteralPath $sealCore -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sealCoreRecord[0].sha256) {
    throw 'The built source artifact does not match the recorded test artifact.'
}
$sealPrepared = & python $sealRunner prepare --runtime $sealRuntime --jar $sealCore --ascensionlib-jar $sealAscension --root (Join-Path $sealWorkspace 'build\native-rehearsals') --game-port 25741 --rcon-port 25742
if ($LASTEXITCODE -ne 0) { throw 'Private fixture preparation failed.' }
$sealRig = [IO.Path]::GetFullPath(([string](@($sealPrepared)[-1])).Trim())
$sealAllowedRoot = [IO.Path]::GetFullPath((Join-Path $sealWorkspace 'build\native-rehearsals')) + [IO.Path]::DirectorySeparatorChar
if (-not $sealRig.StartsWith($sealAllowedRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Prepared fixture is outside the private workspace.' }
$sealManifest = Get-Content -LiteralPath (Join-Path $sealRig 'rehearsal.json') -Raw | ConvertFrom-Json
if ($sealManifest.throwaway -ne $true -or $sealManifest.game_port -ne 25741 -or $sealManifest.rcon_port -ne 25742 -or $null -ne $sealManifest.pid) { throw 'Private fixture identity does not match.' }
$sealEncoding = [Text.UTF8Encoding]::new($false)
$sealProperties = Join-Path $sealRig 'server.properties'
[IO.File]::WriteAllText($sealProperties, ([IO.File]::ReadAllText($sealProperties).Replace('view-distance=4', ('view-distance=' + $ViewDistance))), $sealEncoding)
$sealWorldConfigPath = Join-Path $sealRig 'config\cobbleraids\world_boss.json'
$sealWorldConfig = Get-Content -LiteralPath $sealWorldConfigPath -Raw | ConvertFrom-Json
$sealWorldConfig.spawn.join_countdown_seconds = $GatheringSeconds
$sealWorldConfig.visual | Add-Member -NotePropertyName 'duel_boss_invisible' -NotePropertyValue $true -Force
[IO.File]::WriteAllText($sealWorldConfigPath, ($sealWorldConfig | ConvertTo-Json -Depth 20), $sealEncoding)
$sealPolymer = Join-Path $sealRig 'config\polymer'
New-Item -ItemType Directory -Path $sealPolymer -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $sealPolymer 'auto-host.json'), '{"enabled":true,"required":true,"type":"polymer:automatic","settings":{},"external_resource_packs":[],"setup_early":false}', $sealEncoding)
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'last-sovereign-rig.txt'), $sealRig, $sealEncoding)
$sealJar = Join-Path $sealRig ('mods\' + $sealManifest.jar)
if ((Get-FileHash -LiteralPath $sealJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sealManifest.jar_sha256) {
    throw 'The prepared artifact changed.'
}
Copy-Item -LiteralPath $sealJar -Destination (Join-Path $sealClient ('mods\' + $sealManifest.jar))
$sealPreviousOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = ($sealPreviousOptions + ' -Dcobbleraids.worldboss.sovereignPrototype=true').Trim()
    & python $sealRunner launch --rig $sealRig --java $sealJava
    if ($LASTEXITCODE -ne 0) { throw 'The ownership-checked server launcher refused to start.' }
} finally {
    if ($null -eq $sealPreviousOptions) { Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue }
    else { $env:JAVA_TOOL_OPTIONS = $sealPreviousOptions }
}

$sealDeadline = [DateTime]::UtcNow.AddSeconds(180)
do {
    $sealLog = Join-Path $sealRig 'logs\latest.log'
    $sealReady = (Test-Path -LiteralPath $sealLog) -and (Select-String -LiteralPath $sealLog -SimpleMatch 'World-boss private simulator ready' -Quiet)
    if (-not $sealReady) { Start-Sleep -Seconds 2 }
} while (-not $sealReady -and [DateTime]::UtcNow -lt $sealDeadline)
if (-not $sealReady) { throw 'Server readiness timed out; inspect its private logs. The launcher does not kill processes.' }
& python (Join-Path $sealWorkspace 'build\setup_worldboss_playtest.py') $sealRig
if ($LASTEXITCODE -ne 0) { throw 'Arena setup failed.' }
& python $sealRunner command --rig $sealRig 'polymer generate-pack'
if ($LASTEXITCODE -ne 0) { throw 'Could not request the server pack.' }
$sealPackReady = $false
for ($sealAttempt = 0; $sealAttempt -lt 60 -and -not $sealPackReady; $sealAttempt++) {
    & python (Join-Path $PSScriptRoot 'validate-sovereign-pack.py') $sealWorkspace $sealRig >$null
    $sealPackReady = $LASTEXITCODE -eq 0
    if (-not $sealPackReady) { Start-Sleep -Seconds 1 }
}
if (-not $sealPackReady) { throw 'Pack validation timed out; the graphical client was not launched.' }
$sealClientOptions = Join-Path $sealClient 'options.txt'
$sealOptionsText = [IO.File]::ReadAllText($sealClientOptions)
$sealOptionsText = [regex]::Replace($sealOptionsText, '(?m)^renderDistance:\d+\r?$', ('renderDistance:' + $ViewDistance))
[IO.File]::WriteAllText($sealClientOptions, $sealOptionsText, $sealEncoding)
& python (Join-Path $sealWorkspace 'build\relaunch_worldboss_graphical_client.py')
if ($LASTEXITCODE -ne 0) { throw 'The private client could not launch.' }

$sealJoined = $false
$sealDeadline = [DateTime]::UtcNow.AddSeconds(180)
do {
    $sealList = & python $sealRunner command --rig $sealRig 'list'
    $sealJoined = $LASTEXITCODE -eq 0 -and ($sealList -match 'WorldBossTester')
    if (-not $sealJoined) { Start-Sleep -Seconds 3 }
} while (-not $sealJoined -and [DateTime]::UtcNow -lt $sealDeadline)
if (-not $sealJoined) { throw 'Accept the server resource pack and inspect the private client; no encounter was started.' }
foreach ($sealCommand in @('op WorldBossTester', 'givepokemonother WorldBossTester glaceon level=100',
        'execute as WorldBossTester at @s run cr worldboss test start')) {
    & python $sealRunner command --rig $sealRig $sealCommand
    if ($LASTEXITCODE -ne 0) { throw 'A preview setup command failed.' }
}
Start-Sleep -Seconds 2
& python $sealRunner command --rig $sealRig 'execute as WorldBossTester at @s run cr worldboss warp'
Start-Sleep -Seconds 2
& python $sealRunner command --rig $sealRig 'execute as WorldBossTester at @s run tp @s ~ ~ ~ 90 0'
$sealStatus = & python $sealRunner command --rig $sealRig 'cr worldboss status'
if ($LASTEXITCODE -ne 0 -or -not ($sealStatus -match 'TEST encounter')) { throw 'A TEST encounter could not be confirmed.' }
$sealRendererReady = $false
$sealDeadline = [DateTime]::UtcNow.AddSeconds(15)
do {
    $sealRendererReady = Select-String -LiteralPath (Join-Path $sealRig 'logs\latest.log') -SimpleMatch 'Sovereign Seal prototype for TEST World Boss' -Quiet
    if (-not $sealRendererReady) { Start-Sleep -Seconds 1 }
} while (-not $sealRendererReady -and [DateTime]::UtcNow -lt $sealDeadline)
if (-not $sealRendererReady) { throw 'The prototype renderer did not confirm construction; inspect its private logs.' }
Write-Host 'Private TEST prototype started. Dismiss the Pokemon reveal with Continue. No live rewards are enabled.'
