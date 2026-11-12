# Brings up the whole Daftari stack on this PC: MySQL, the M-Pesa reader, the web app on Tomcat,
# and an optional demo owner account. Safe to run again; each step skips work already done.
#
# Usage (from the repo root):
#   $env:DEMO_OWNER_PASSWORD = '<choose a password, 6+ characters>'
#   powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1
#
# Tool locations can be overridden with environment variables before running:
#   JAVA_HOME, MAVEN_HOME, TOMCAT_HOME, MYSQL_HOME, MINGW_BIN, DB_PASS
# DB_PASS is the MySQL root password. If it is not set, it is read from the user environment.
$ErrorActionPreference = 'Stop'

$repo    = Split-Path -Parent $PSScriptRoot
$java    = if ($env:JAVA_HOME)  { $env:JAVA_HOME }  else { 'C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot' }
$maven   = if ($env:MAVEN_HOME) { $env:MAVEN_HOME } else { 'C:\tools\apache-maven-3.9.16' }
$tomcat  = if ($env:TOMCAT_HOME){ $env:TOMCAT_HOME } else { 'C:\tools\apache-tomcat-10.1.61' }
$mysqlHome = if ($env:MYSQL_HOME) { $env:MYSQL_HOME } else { 'C:\Program Files\MySQL\MySQL Server 8.4' }
$mysqlData = 'C:\ProgramData\MySQL\MySQL Server 8.4'
$mingw   = if ($env:MINGW_BIN) { $env:MINGW_BIN } else { "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\BrechtSanders.WinLibs.POSIX.UCRT_Microsoft.Winget.Source_8wekyb3d8bbwe\mingw64\bin" }
$mysql   = Join-Path $mysqlHome 'bin\mysql.exe'
$base    = 'http://localhost:8080/daftari'

function Step($text) { Write-Output "`n== $text" }

function Get-DbPassword {
    if ($env:DB_PASS) { return $env:DB_PASS }
    $pw = [Environment]::GetEnvironmentVariable('DB_PASS', 'User')
    if (-not $pw) { throw 'DB_PASS is not set. Set it to the MySQL root password.' }
    return $pw
}

function Test-Port($port) {
    [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}

function Invoke-Mysql($sql) {
    $env:MYSQL_PWD = Get-DbPassword
    try { & $mysql -u root -N -B -e $sql 2>&1 } finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
}

# 1. MySQL
Step 'MySQL'
if (Test-Port 3306) {
    Write-Output 'MySQL already running.'
} else {
    $mysqld = Join-Path $mysqlHome 'bin\mysqld.exe'
    Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=`"$mysqlData\my.ini`"", '--console' -WindowStyle Hidden
    $up = $false
    for ($i = 0; $i -lt 30 -and -not $up; $i++) { Start-Sleep -Seconds 2; if (Test-Port 3306) { $up = $true } }
    if (-not $up) { throw 'MySQL did not start on port 3306.' }
    Write-Output 'MySQL started.'
}
$hasDb = (Invoke-Mysql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='daftari';") -match '1'
if (-not $hasDb) {
    $env:MYSQL_PWD = Get-DbPassword
    & $mysql -u root --execute="source $($repo -replace '\\','/')/sql/schema.sql" 2>&1 | Out-Null
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    Write-Output 'Schema loaded.'
} else {
    Write-Output 'Database daftari exists.'
}

# 2. M-Pesa reader (C++)
Step 'M-Pesa reader'
$env:Path = "$mingw;$env:Path"
& (Join-Path $repo 'native\build.ps1') | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'M-Pesa reader build or tests failed.' }
$reader = Join-Path $repo 'native\bin\mpesa_reader.exe'
[Environment]::SetEnvironmentVariable('MPESA_READER', $reader, 'User')
Write-Output "Built and tested: $reader"

# 3. Web app (Java)
Step 'Web app'
$env:JAVA_HOME = $java
$env:Path = "$java\bin;$maven\bin;$env:Path"
Push-Location $repo
try { & mvn -B -q package; if ($LASTEXITCODE -ne 0) { throw 'mvn package failed.' } } finally { Pop-Location }
Write-Output 'WAR built.'

# 4. Tomcat
Step 'Tomcat'
$env:CATALINA_HOME = $tomcat
$env:CATALINA_BASE = $tomcat
$env:MPESA_READER = $reader
$env:DB_PASS = Get-DbPassword
Copy-Item (Join-Path $repo 'target\daftari.war') (Join-Path $tomcat 'webapps\daftari.war') -Force
# catalina.bat start/stop must not be waited on: the start script stays attached, so we poll the port instead.
if (Test-Port 8080) {
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'apache-tomcat' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    for ($i = 0; $i -lt 15 -and (Test-Port 8080); $i++) { Start-Sleep -Seconds 2 }
}
Start-Process -FilePath (Join-Path $tomcat 'bin\catalina.bat') -ArgumentList 'start' -WindowStyle Hidden
$up = $false
for ($i = 0; $i -lt 45 -and -not $up; $i++) { Start-Sleep -Seconds 2; if (Test-Port 8080) { $up = $true } }
if (-not $up) { throw 'Tomcat did not start on port 8080.' }
Write-Output 'Tomcat running.'

# 5. Demo owner (only when the database has no users yet)
Step 'Demo owner'
$users = [int](Invoke-Mysql 'SELECT COUNT(*) FROM daftari.users;')
if ($users -gt 0) {
    Write-Output 'Users already exist; skipping demo owner.'
} elseif (-not $env:DEMO_OWNER_PASSWORD) {
    Write-Output 'DEMO_OWNER_PASSWORD not set; open /daftari/setup to create the first owner.'
} else {
    $form = @{ shop = 'Daftari Demo Shop'; location = 'Icipe Road, Kasarani'; name = 'Demo Owner'; phone = '0700000000'; password = $env:DEMO_OWNER_PASSWORD }
    Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$base/setup" -Body $form -MaximumRedirection 0 -ErrorAction SilentlyContinue | Out-Null
    Write-Output 'Demo owner created: phone 0700000000 (password from DEMO_OWNER_PASSWORD).'
}

Step 'Ready'
Write-Output "App: $base/"
Write-Output 'Log in with the owner account, or open /setup on a fresh database.'
