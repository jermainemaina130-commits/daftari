# Builds the M-Pesa reader and runs its tests. Needs g++ on PATH (WinLibs / MinGW-w64).
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $here 'bin'
New-Item -ItemType Directory -Force -Path $out | Out-Null

$common = @('-std=c++17', '-O2', '-Wall', '-Wextra', '-Werror')
$lib = Join-Path $here 'mpesa_parser.cpp'

# -static: the reader runs as a separate process from Tomcat, so it must not need MinGW DLLs on PATH.
& g++ @common -static -o (Join-Path $out 'mpesa_reader.exe') (Join-Path $here 'main.cpp') $lib
if ($LASTEXITCODE -ne 0) { throw "build of mpesa_reader.exe failed" }

& g++ @common -o (Join-Path $out 'test_parser.exe') (Join-Path $here 'tests\test_parser.cpp') $lib
if ($LASTEXITCODE -ne 0) { throw "build of test_parser.exe failed" }

& (Join-Path $out 'test_parser.exe')
if ($LASTEXITCODE -ne 0) { throw "unit tests failed" }
Write-Output "BUILD AND TESTS OK"
