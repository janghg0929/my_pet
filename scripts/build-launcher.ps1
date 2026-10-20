# 윈도우 실행기(MyPet.exe) 만들기: 처음 실행 때 앱을 %LOCALAPPDATA%\MyPet 에 설치하고, 그다음부터는 바로 실행한다.
# 윈도우에 기본으로 들어 있는 .NET Framework 4 의 C# 컴파일러를 쓴다 (따로 설치할 것 없음).
# 사용법: .\scripts\build-launcher.ps1        결과: build\release\MyPet.exe
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path $csc)) {
    throw ".NET Framework 4 C# 컴파일러가 없습니다: $csc"
}
$release = Join-Path $root 'build\release'
New-Item -ItemType Directory -Force $release | Out-Null
$output = Join-Path $release 'MyPet.exe'
& $csc /nologo /target:winexe /optimize+ /codepage:65001 `
    /reference:System.dll /reference:System.Drawing.dll /reference:System.Windows.Forms.dll `
    /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll `
    "/out:$output" (Join-Path $root 'launcher\MyPetLauncher.cs')
if ($LASTEXITCODE -ne 0) { throw "실행기 빌드 실패" }
Write-Host "완료: $output" -ForegroundColor Green
