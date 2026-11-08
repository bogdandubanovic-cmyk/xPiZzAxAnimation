param([string]$ModsDirectory = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
$repo = 'bogdandubanovic-cmyk/xPiZzAxAnimation'
$api = "https://api.github.com/repos/$repo/releases/latest"
$mods = (Resolve-Path -LiteralPath $ModsDirectory).Path

if (Get-Process -Name java,javaw -ErrorAction SilentlyContinue) {
  throw 'Close Minecraft and the launcher before running this one-time update.'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem

$old = @()
foreach ($file in Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File) {
  try {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($file.FullName)
    try {
      $entry = $zip.GetEntry('fabric.mod.json')
      if ($null -eq $entry) { continue }
      $reader = New-Object System.IO.StreamReader($entry.Open())
      try { $meta = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
      if ($meta.id -in @('xpizzaxanimation','rpmmodels','xpizzax_emoticons')) { $old += $file.FullName }
    } finally { $zip.Dispose() }
  } catch { }
}
if ($old.Count -eq 0) { throw 'No old xPiZzAxAnimation/RPMRoleplay mod JAR found in this mods folder.' }

Write-Host 'Getting the latest stable release from GitHub...'
$release = Invoke-RestMethod -Uri $api -Headers @{ 'User-Agent'='xPiZzAxAnimation-Bootstrap/1.0'; 'Accept'='application/vnd.github+json' }
$asset = $release.assets | Where-Object { $_.name -eq 'xPiZzAxAnimation.jar' } | Select-Object -First 1
if ($null -eq $asset) { throw "Release $($release.tag_name) has no xPiZzAxAnimation.jar asset." }

$temp = Join-Path $mods 'xPiZzAxAnimation.jar.download'
$target = Join-Path $mods 'xPiZzAxAnimation.jar'
$backup = Join-Path $mods 'xPiZzAxAnimation.jar.previous'
Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
Write-Host "Downloading $($release.tag_name)..."
Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $temp -Headers @{ 'User-Agent'='xPiZzAxAnimation-Bootstrap/1.0' }

$zip = [System.IO.Compression.ZipFile]::OpenRead($temp)
try {
  $entry = $zip.GetEntry('fabric.mod.json')
  if ($null -eq $entry) { throw 'Downloaded file is not a Fabric mod JAR.' }
  $reader = New-Object System.IO.StreamReader($entry.Open())
  try { $meta = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
  if ($meta.id -ne 'xpizzaxanimation') { throw "Unexpected Fabric mod ID: $($meta.id)" }
  if ($meta.version -notlike "$($release.tag_name.TrimStart('v'))*") { throw 'JAR version does not match the release tag.' }
} finally { $zip.Dispose() }

if ($asset.digest -and $asset.digest.StartsWith('sha256:')) {
  $expected = $asset.digest.Substring(7).ToLowerInvariant()
  $actual = (Get-FileHash -LiteralPath $temp -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($actual -ne $expected) { throw 'SHA-256 verification failed; old files were not changed.' }
}

Remove-Item -LiteralPath $backup -Force -ErrorAction SilentlyContinue
if (Test-Path -LiteralPath $target) { Move-Item -LiteralPath $target -Destination $backup }
try {
  Move-Item -LiteralPath $temp -Destination $target
  foreach ($path in $old) {
    if ((Test-Path -LiteralPath $path) -and ($path -ne $target)) {
      $oldBackup = "$path.previous"
      Remove-Item -LiteralPath $oldBackup -Force -ErrorAction SilentlyContinue
      Move-Item -LiteralPath $path -Destination $oldBackup
    }
  }
  Remove-Item -LiteralPath $backup -Force -ErrorAction SilentlyContinue
} catch {
  if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Force -ErrorAction SilentlyContinue }
  if (Test-Path -LiteralPath $backup) { Move-Item -LiteralPath $backup -Destination $target -Force -ErrorAction SilentlyContinue }
  throw
}
Write-Host "Installed $($release.tag_name). Start Minecraft. Future updates will install automatically after you close the game."
