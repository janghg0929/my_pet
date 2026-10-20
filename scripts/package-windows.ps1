# 윈도우 배포 패키지(MyPet-windows-x64.zip) 만들기
# 사용법: $env:JAVA_HOME = '<Liberica Full JDK 21 경로>'; .\scripts\package-windows.ps1 -Version 2.0.0
# 결과: build\release\MyPet-windows-x64.zip  (압축을 풀고 MyPet\MyPet.exe 실행, 자바 설치 불필요)
param(
    [Parameter(Mandatory = $true)][string]$Version
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# 실행 중인 MyPet 종료 (파일 잠금 방지)
Get-Process -Name MyPet -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

& .\gradlew.bat fatJar "-PappVersion=$Version" --console=plain
if ($LASTEXITCODE -ne 0) { throw "gradle build failed" }

$jpackage = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\jpackage.exe' } else { 'jpackage' }
# 설정 창(HTML)에 JavaFX WebView 가 필요하다: JavaFX 가 포함된 JDK(예: Liberica Full JDK 21)로 JAVA_HOME 을 지정할 것
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'jmods\javafx.web.jmod'))) {
    throw "JAVA_HOME 이 JavaFX 포함 JDK 가 아닙니다 (jmods\javafx.web.jmod 없음). Liberica Full JDK 21 을 설치하고 JAVA_HOME 을 지정하세요."
}
$out = Join-Path $root 'build\jpackage'
$release = Join-Path $root 'build\release'
Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $release | Out-Null

$modules = 'java.base,java.desktop,java.logging,java.net.http,java.prefs,java.management,jdk.crypto.ec,jdk.charsets,jdk.localedata,jdk.unsupported,jdk.zipfs,javafx.base,javafx.graphics,javafx.controls,javafx.media,javafx.web,javafx.swing,jdk.jsobject'
& $jpackage --type app-image `
    --name MyPet `
    --app-version $Version `
    --vendor 'MY_PET' `
    --input (Join-Path $root 'build\dist') `
    --main-jar MyPet.jar `
    --main-class com.mypet.Launcher `
    --add-modules $modules `
    --dest $out
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

$zip = Join-Path $release 'MyPet-windows-x64.zip'
Remove-Item -Force $zip -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $out 'MyPet') -DestinationPath $zip
Copy-Item -Force (Join-Path $root 'build\dist\MyPet.jar') (Join-Path $release 'MyPet.jar')

# 사용자가 받는 실행기 MyPet.exe (처음 실행 때 위 zip 을 받아 %LOCALAPPDATA%\MyPet 에 설치)
& (Join-Path $PSScriptRoot 'build-launcher.ps1')
Write-Host "완료: $zip, $(Join-Path $release 'MyPet.exe')" -ForegroundColor Green
