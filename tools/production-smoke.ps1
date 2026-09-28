param([Parameter(Mandatory=$true)][string]$VersionJson,[Parameter(Mandatory=$true)][string]$MinecraftRoot,[ValidateSet('en_us','zh_cn')][string]$Language='en_us',[string]$CompatibilityInstance,[string]$ModVersion='0.1.12',[switch]$FullPack,[ValidateSet('default','slim')][string]$Skin='default',[switch]$ExactSettings,[switch]$InPlace,[string]$EvidencePath,[switch]$Diverse,[switch]$EntryTest,[switch]$Usability,[switch]$FinalTest,[switch]$ItemsTest,[switch]$PlacementTest)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$runRoot=Join-Path $projectRoot ('build\production-run-'+$ModVersion+'-'+$Language)
if($CompatibilityInstance) { $runRoot=Join-Path $projectRoot ('build\compatibility-'+$ModVersion+'-'+$Language) }
if($FullPack) {
    if(!$CompatibilityInstance) { throw 'FullPack requires CompatibilityInstance.' }
    $runRoot=Join-Path $projectRoot ('build\compatibility-full-'+$ModVersion+'-'+$Language)
}
if($Skin -eq 'slim') { $runRoot+='-slim' }
if($ExactSettings) { if(!$FullPack) {throw 'ExactSettings requires FullPack'}; $runRoot+='-actual-settings' }
if($InPlace) {if(!$ExactSettings) {throw 'InPlace requires ExactSettings'};$runRoot=$CompatibilityInstance}
if([IO.Path]::GetPathRoot($projectRoot) -ne 'E:\') { throw 'E: workspace required.' }
$env:JAVA_HOME=Join-Path $projectRoot '.toolchain\jdk17'
$env:TEMP=Join-Path $projectRoot '.toolchain\temp';$env:TMP=$env:TEMP
$env:GRADLE_USER_HOME=Join-Path $projectRoot '.toolchain\gradle-user-home'
New-Item -ItemType Directory -Force $runRoot,(Join-Path $runRoot 'mods'),(Join-Path $runRoot 'natives'),$env:TEMP | Out-Null
if(!$InPlace) {[IO.File]::WriteAllLines((Join-Path $runRoot 'options.txt'),@(('lang:'+$Language),'guiScale:2','onboardAccessibility:false'),[Text.UTF8Encoding]::new($false))}
if($ExactSettings -and !$InPlace) {
    Copy-Item -LiteralPath (Join-Path $CompatibilityInstance 'options.txt') -Destination (Join-Path $runRoot 'options.txt') -Force
    foreach($folder in @('config','resourcepacks','tacz')) {
        $source=Join-Path $CompatibilityInstance $folder
        if(Test-Path -LiteralPath $source) {Copy-Item -LiteralPath $source -Destination $runRoot -Recurse -Force}
    }
}
$profile=Get-Content -LiteralPath $VersionJson -Raw | ConvertFrom-Json
if($profile.mainClass -ne 'cpw.mods.bootstraplauncher.BootstrapLauncher') { throw 'Expected a complete Forge 1.20.1 profile.' }
function Test-Rules($Rules) {
    if(-not $Rules) { return $true }
    $allowed=$false
    foreach($rule in $Rules) {
        $matches=$true
        if($rule.os -and $rule.os.name -and $rule.os.name -ne 'windows') { $matches=$false }
        if($rule.os -and $rule.os.arch -and $rule.os.arch -ne 'x86_64') { $matches=$false }
        if($rule.features) { $matches=$false }
        if($matches) { $allowed=$rule.action -eq 'allow' }
    }
    return $allowed
}
$libraryRoot=Join-Path $MinecraftRoot 'libraries'
$classPaths=[Collections.Generic.List[string]]::new()
foreach($library in $profile.libraries) {
    if(-not (Test-Rules $library.rules)) { continue }
    $relative=$library.downloads.artifact.path
    if(-not $relative) {
        $coordinate=$library.name.Split(':')
        $suffix=if($coordinate.Length -gt 3) {'-'+$coordinate[3]} else {''}
        $relative=$coordinate[0].Replace('.','/')+'/'+$coordinate[1]+'/'+$coordinate[2]+'/'+$coordinate[1]+'-'+$coordinate[2]+$suffix+'.jar'
    }
    $path=Join-Path $libraryRoot $relative
    if(-not (Test-Path -LiteralPath $path)) { throw "Required cached library missing: $relative" }
    $classPaths.Add($path)
}
$versionFolder=Split-Path -Parent $VersionJson
$gameJar=Join-Path $MinecraftRoot 'versions\1.20.1\1.20.1.jar'
if(-not (Test-Path -LiteralPath $gameJar)) { $gameJar=Join-Path $versionFolder ($profile.id+'.jar') }
if(-not (Test-Path -LiteralPath $gameJar)) { throw 'Minecraft 1.20.1 client jar missing.' }
$classPaths.Add($gameJar)
$variables=@{
    natives_directory=(Join-Path $runRoot 'natives');launcher_name='PoseStudioAcceptance';launcher_version='1'
    classpath=($classPaths -join ';');classpath_separator=';';library_directory=$libraryRoot
    auth_player_name='StudioTest';version_name='1.20.1';game_directory=$runRoot
    assets_root=(Join-Path $env:GRADLE_USER_HOME 'caches\forge_gradle\assets');assets_index_name='5'
    auth_uuid='dc48559733fe399da3451193bc4763c1';auth_access_token='0';clientid='0';auth_xuid='0'
    user_type='legacy';version_type='release'
}
function Expand-Argument([string]$Argument) {
    foreach($key in $variables.Keys) { $Argument=$Argument.Replace('${'+$key+'}',[string]$variables[$key]) }
    if($Argument.Contains('${')) { throw "Unresolved launch placeholder: $Argument" }
    return $Argument
}
function Read-Arguments($Arguments) {
    foreach($argument in $Arguments) {
        if($argument -is [string]) { Expand-Argument $argument }
        elseif(Test-Rules $argument.rules) { foreach($value in $argument.value) { Expand-Argument $value } }
    }
}
$launchArguments=[Collections.Generic.List[string]]::new()
$launchArguments.Add('-Xmx2G')
$launchArguments.Add('-Djava.io.tmpdir='+$env:TEMP)
$launchArguments.Add('-Duser.home='+(Join-Path $projectRoot '.toolchain\user-home'))
$launchArguments.Add('-Dfile.encoding=UTF-8')
$launchArguments.Add('-Dposestudio.acceptance=true')
if($Diverse) {$launchArguments.Add('-Dposestudio.acceptance.diverse=true')}
if($EntryTest) {$launchArguments.Add('-Dposestudio.acceptance.entry=true')}
if($Usability -or $FinalTest) {$launchArguments.Add('-Dposestudio.acceptance.usability=true')}
if($FinalTest) {$launchArguments.Add('-Dposestudio.acceptance.final=true')}
if($ItemsTest) {$launchArguments.Add('-Dposestudio.acceptance.items=true')}
if($PlacementTest) {$launchArguments.Add('-Dposestudio.acceptance.placement=true')}
$launchArguments.Add('-Dposestudio.acceptance.language='+$Language)
$launchArguments.Add('-Dposestudio.acceptance.skin='+$Skin)
if($ExactSettings) { $launchArguments.Add('-Dposestudio.acceptance.exact=true') }
if($ExactSettings) {
    $packLine=Get-Content -LiteralPath (Join-Path $runRoot 'options.txt') | Where-Object {$_.StartsWith('resourcePacks:')}
    $currentPacks=@($packLine.Substring(14)|ConvertFrom-Json)
    $expectedPackRoot=$runRoot;if($EvidencePath) {$expectedPackRoot=$EvidencePath}
    $expectedPackFile=Join-Path $expectedPackRoot 'expected-resource-packs.txt'
    [IO.File]::WriteAllLines($expectedPackFile,$currentPacks,[Text.UTF8Encoding]::new($false))
    $launchArguments.Add('-Dposestudio.acceptance.expectedPacksFile='+$expectedPackFile)
}
if($EvidencePath) {$launchArguments.Add('-Dposestudio.acceptance.evidence='+$EvidencePath)}
if($CompatibilityInstance) { $launchArguments.Add('-Dmixin.debug.export=true') }
foreach($arg in Read-Arguments $profile.arguments.jvm) { $launchArguments.Add($arg) }
$launchArguments.Add($profile.mainClass)
foreach($arg in Read-Arguments $profile.arguments.game) { $launchArguments.Add($arg) }
$launchArguments.Add('--width');$launchArguments.Add('1280');$launchArguments.Add('--height');$launchArguments.Add('720')
$argFile=Join-Path $runRoot 'launch.args'
$quoted=foreach($arg in $launchArguments) { '"'+$arg.Replace('\','/').Replace('"','\"')+'"' }
[IO.File]::WriteAllLines($argFile,$quoted,[Text.UTF8Encoding]::new($false))
if($CompatibilityInstance -and !$InPlace) {
    Get-ChildItem -LiteralPath (Join-Path $CompatibilityInstance 'mods') -Filter '*.jar' | Where-Object {($_.Name -notmatch '^posestudio-') -and ($FullPack -or $_.Name -match '^(embeddium|oculus)|entityculling|entity_model_features|entity_texture_features|skinlayers3d')} | Copy-Item -Destination (Join-Path $runRoot 'mods') -Force
    $configRoot=Join-Path $runRoot 'config';New-Item -ItemType Directory -Force $configRoot | Out-Null
    foreach($name in @('entityculling.json','oculus.properties')) {
        $source=Join-Path $CompatibilityInstance ('config\'+$name)
        if(Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination $configRoot -Force }
    }
    $shader=Join-Path $CompatibilityInstance 'shaderpacks\ComplementaryShaders_v4.7.2'
    if(Test-Path -LiteralPath $shader) { $shaderRoot=Join-Path $runRoot 'shaderpacks';New-Item -ItemType Directory -Force $shaderRoot | Out-Null;Copy-Item -LiteralPath $shader -Destination $shaderRoot -Recurse -Force }
}
Copy-Item -LiteralPath (Join-Path $projectRoot ('build\libs\posestudio-'+$ModVersion+'.jar')),(Join-Path $projectRoot ('build\libs\posestudio-'+$ModVersion+'-verification-fixtures.jar')) -Destination (Join-Path $runRoot 'mods') -Force
Push-Location $runRoot
try { & (Join-Path $env:JAVA_HOME 'bin\java.exe') ('@'+$argFile); exit $LASTEXITCODE } finally { Pop-Location }
