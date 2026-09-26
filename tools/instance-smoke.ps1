param([string]$ModVersion='0.1.7',[ValidateSet('default','slim')][string]$Skin='default',[switch]$UserSkin,[switch]$Diverse,[switch]$EntryTest,[switch]$Usability,[switch]$FinalTest)
$ErrorActionPreference='Stop'
$projectRoot='E:\PoseStudio'
$env:TEMP=Join-Path $projectRoot '.toolchain\temp';$env:TMP=$env:TEMP
$instance='E:\minecrate\.minecraft\versions\1.20.1-Forge_47.4.22 Vanilla-tacz'
$running=Get-CimInstance Win32_Process | Where-Object {$_.Name -eq 'java.exe' -and $_.CommandLine -like ('*'+$instance+'*')}
if($running) {throw 'The specified game instance is already running. No files changed.'}
$skinPack=Join-Path $instance 'resourcepacks\PoseStudioAcceptanceSkin.zip'
if(Test-Path -LiteralPath $skinPack) {throw 'Existing acceptance skin pack must be inspected before replacement.'}
$backup=Join-Path $projectRoot ('build\instance-check-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+$Skin)
New-Item -ItemType Directory -Force $backup,(Join-Path $backup 'mods'),(Join-Path $backup 'evidence') | Out-Null
Copy-Item -LiteralPath (Join-Path $instance 'options.txt'),(Join-Path $instance 'config') -Destination $backup -Recurse -Force
Copy-Item -LiteralPath (Join-Path $instance 'logs\latest.log') -Destination (Join-Path $backup 'original-latest.log')
$oldMods=@(Get-ChildItem -LiteralPath (Join-Path $instance 'mods') -Filter 'posestudio-*.jar')
$oldMods | ForEach-Object {Move-Item -LiteralPath $_.FullName -Destination (Join-Path $backup 'mods')}
$success=$false
try {
    if($UserSkin) {
        $stage=Join-Path $backup 'skin-pack';$textures=Join-Path $stage 'assets\posefixtures\textures'
        New-Item -ItemType Directory -Force $textures | Out-Null
        Copy-Item -LiteralPath (Join-Path $instance 'ETF_player_skin_printout.png') -Destination (Join-Path $textures 'test_skin.png')
        [IO.File]::WriteAllText((Join-Path $stage 'pack.mcmeta'),'{"pack":{"pack_format":15,"description":"Temporary local skin acceptance texture"}}',[Text.UTF8Encoding]::new($false))
        Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $skinPack
        $options=Get-Content -LiteralPath (Join-Path $instance 'options.txt')
        $options=$options | ForEach-Object {if($_.StartsWith('resourcePacks:')) {'resourcePacks:'+((@($_.Substring(14)|ConvertFrom-Json)+@('file/PoseStudioAcceptanceSkin.zip'))|ConvertTo-Json -Compress)}else{$_}}
        [IO.File]::WriteAllLines((Join-Path $instance 'options.txt'),$options,[Text.UTF8Encoding]::new($false))
    }
    & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File (Join-Path $PSScriptRoot 'production-smoke.ps1') -VersionJson (Join-Path $instance '1.20.1-Forge_47.4.22 Vanilla-tacz.json') -MinecraftRoot 'E:\minecrate\.minecraft' -Language zh_cn -CompatibilityInstance $instance -FullPack -ExactSettings -InPlace -ModVersion $ModVersion -Skin $Skin -Diverse:$Diverse -EntryTest:$EntryTest -Usability:$Usability -FinalTest:$FinalTest -EvidencePath (Join-Path $backup 'evidence') *> (Join-Path $backup 'game.log')
    $results=Get-Content -LiteralPath (Join-Path $backup 'evidence\acceptance.txt')
    if(!$results -or @($results | Where-Object {$_ -notlike 'PASS *'}).Count -gt 0) {throw 'Actual-instance acceptance failed. See saved evidence.'}
    $success=$true
} finally {
    Copy-Item -LiteralPath (Join-Path $backup 'options.txt') -Destination (Join-Path $instance 'options.txt') -Force
    Copy-Item -Path (Join-Path $backup 'config\*') -Destination (Join-Path $instance 'config') -Recurse -Force
    $fixture=Join-Path $instance ('mods\posestudio-'+$ModVersion+'-verification-fixtures.jar')
    if(Test-Path -LiteralPath $fixture) {Move-Item -LiteralPath $fixture -Destination (Join-Path $backup 'mods')}
    if(Test-Path -LiteralPath $skinPack) {Move-Item -LiteralPath $skinPack -Destination $backup}
    if(!$success) {
        $newJar=Join-Path $instance ('mods\posestudio-'+$ModVersion+'.jar')
        if(Test-Path -LiteralPath $newJar) {Move-Item -LiteralPath $newJar -Destination (Join-Path $backup 'failed-posestudio.jar')}
        foreach($old in $oldMods) {Move-Item -LiteralPath (Join-Path $backup ('mods\'+$old.Name)) -Destination (Join-Path $instance 'mods')}
    }
}
'ACTUAL_INSTANCE_PASS evidence='+$backup+' checks='+$results.Count
