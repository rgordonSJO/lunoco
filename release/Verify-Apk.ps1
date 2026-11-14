param(
 [Parameter(Mandatory=$true)][string]$Apk,
 [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$ExpectedSHA256,
 [Parameter(Mandatory=$true)][int]$ExpectedVersionCode,
 [int]$PreviousVersionCode=0,
 [Parameter(Mandatory=$true)][string]$BuildTools,
 [Parameter(Mandatory=$true)][string]$Java
)
$ErrorActionPreference='Stop'
if($ExpectedVersionCode -le $PreviousVersionCode){throw 'versionCode must exceed the previous public release'}
$hash=(Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
if($hash -ne $ExpectedSHA256.ToLowerInvariant()){throw 'APK checksum mismatch'}
$aapt=Join-Path $BuildTools 'aapt.exe'
if(!(Test-Path $aapt)){$aapt=Join-Path $BuildTools 'aapt'}
$badging=(& $aapt dump badging $Apk)-join [Environment]::NewLine
if($LASTEXITCODE -ne 0){throw 'Cannot inspect APK manifest'}
if($badging -notmatch "package: name='com\.ryan\.lunatv\.jellyfin\.performance'"){throw 'Package mismatch'}
if($badging -notmatch "versionCode='$ExpectedVersionCode'"){throw 'versionCode mismatch'}
if($badging -notmatch "application-label:'Lunoco'"){throw 'Label mismatch'}
$cert=(& $Java --enable-native-access=ALL-UNNAMED -jar (Join-Path $BuildTools 'lib/apksigner.jar') verify --print-certs $Apk 2>&1)-join [Environment]::NewLine
if($LASTEXITCODE -ne 0){throw 'Android signature verification failed'}
if($cert -notmatch 'certificate SHA-256 digest: 57a0d2a501b9a679311a88aad8d9b41a1b605119ce2cf638694dae2563cf1045'){throw 'Original signer mismatch'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $Apk))
try {
 $native=@($zip.Entries | Where-Object {$_.FullName -match '^lib/[^/]+/[^/]+\.so$'})
 if($native.Count -lt 23){throw 'Native payload count below initial release; review dependencies'}
 if(!($zip.Entries | Where-Object {$_.FullName -match '^classes[0-9]*\.dex$'})){throw 'APK DEX missing'}
 Write-Output "PASS sha256=$hash;package=com.ryan.lunatv.jellyfin.performance;versionCode=$ExpectedVersionCode;original_signer=PASS;native_libraries=$($native.Count)"
} finally {$zip.Dispose()}
