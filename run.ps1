param([switch]$Rebuild)
$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    $jar = Join-Path $PSScriptRoot 'backend/target/enterprise-kb-1.0.0.jar'
    if ($Rebuild -or !(Test-Path -LiteralPath $jar)) {
        $npm = if ($env:OS -eq 'Windows_NT') { 'npm.cmd' } else { 'npm' }
        & $npm ci --prefix frontend
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & $npm run build --prefix frontend
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & mvn -B -f backend/pom.xml clean package
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }

    $javaFlags = @()
    if ($env:OS -eq 'Windows_NT') {
        # Some Windows installations cannot connect local AF_UNIX sockets.
        # An unused socket directory lets Java 17 fall back to TCP loopback for this process.
        $socketDir = Join-Path ([IO.Path]::GetTempPath()) ("ekb-sockets-" + [Guid]::NewGuid().ToString('N'))
        $javaFlags += "-Djdk.net.unixdomain.tmpdir=$socketDir"
    }
    & java @javaFlags -jar $jar
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
