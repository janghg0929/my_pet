# 윈도우 설치 프로그램(MyPet-Setup.exe) 만들기: 비트 프로그램(zip)을 exe 안에 넣는다.
# 실행하면 안에 든 프로그램을 %LOCALAPPDATA%\MyPet 에 풀어 설치하고(인터넷 필요 없음) MY_PET 바로가기를 만든다.
# 윈도우에 기본으로 들어 있는 .NET Framework 4 의 C# 컴파일러를 쓴다 (따로 설치할 것 없음).
# 사용법: .\scripts\build-launcher.ps1 [-Package build\release\MyPet-windows-x64.zip]
#         (package-windows.ps1 이 zip 을 만든 뒤 자동으로 부른다)   결과: build\release\MyPet-Setup.exe
param(
    [string]$Package
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $Package) { $Package = Join-Path $root 'build\release\MyPet-windows-x64.zip' }
if (-not (Test-Path $Package)) {
    throw "exe 에 넣을 프로그램 zip 이 없습니다: $Package (먼저 package-windows.ps1 로 만드세요)"
}
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path $csc)) {
    throw ".NET Framework 4 C# 컴파일러가 없습니다: $csc"
}
$release = Join-Path $root 'build\release'
New-Item -ItemType Directory -Force $release | Out-Null
$output = Join-Path $release 'MyPet-Setup.exe'
& $csc /nologo /target:winexe /optimize+ /codepage:65001 `
    /reference:System.dll /reference:System.Drawing.dll /reference:System.Windows.Forms.dll `
    /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll `
    "/resource:$Package,MyPet-windows-x64.zip" `
    "/win32icon:$(Join-Path $root 'packaging\MyPet.ico')" `
    "/out:$output" (Join-Path $root 'launcher\MyPetSetup.cs')
if ($LASTEXITCODE -ne 0) { throw "설치 프로그램 빌드 실패" }
Write-Host ("완료: {0} ({1:N1} MB)" -f $output, ((Get-Item $output).Length / 1MB)) -ForegroundColor Green
