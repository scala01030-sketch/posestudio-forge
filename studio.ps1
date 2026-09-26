param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Tasks=@('build'))
$ErrorActionPreference='Stop'
$projectRoot=$PSScriptRoot
if([IO.Path]::GetPathRoot($projectRoot) -ne 'E:\') { throw 'This project is configured to write only on E:. Move it to E: before running.' }
$env:JAVA_HOME=Join-Path $projectRoot '.toolchain\jdk17'
$env:GRADLE_USER_HOME=Join-Path $projectRoot '.toolchain\gradle-user-home'
$env:TEMP=Join-Path $projectRoot '.toolchain\temp'
$env:TMP=$env:TEMP
$taskUserHome=Join-Path $projectRoot '.toolchain\user-home'
New-Item -ItemType Directory -Force $env:TEMP,$taskUserHome | Out-Null
$env:JAVA_OPTS="-Djava.io.tmpdir=$env:TEMP -Duser.home=$taskUserHome -Dfile.encoding=UTF-8"
Push-Location $projectRoot
try { & .\gradlew.bat @Tasks --no-daemon; exit $LASTEXITCODE } finally { Pop-Location }
