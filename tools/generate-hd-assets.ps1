# Generates the HD textures used by the code-built weapon/aircraft models and the FX renderer.
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File tools/generate-hd-assets.ps1
# Procedural and original - no third-party assets.

param([string]$OutRoot = '')
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = if ($OutRoot) { $OutRoot } else { [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\src\main\resources\assets\aegiscommand')) }

function Ensure($p) { $d = Split-Path $p -Parent; if (-not (Test-Path $d)) { New-Item -ItemType Directory -Force $d | Out-Null } }
function Clamp([double]$v) { if ($v -lt 0) { return 0 } if ($v -gt 255) { return 255 } return [int]$v }
function SavePng($bmp, $rel) {
    $p = Join-Path $root $rel; Ensure $p
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    [System.IO.File]::WriteAllBytes($p, $ms.ToArray()); $ms.Dispose(); $bmp.Dispose()
}
$rng = New-Object System.Random 4242

# Panel: every face of a model maps this whole texture, so the border reads as a machined bevel
# (dark seam, bright chamfer highlight) and the inside as fine brushed metal.
function Panel($size, $base, $rel) {
    $b = New-Object System.Drawing.Bitmap $size, $size
    for ($y = 0; $y -lt $size; $y++) { for ($x = 0; $x -lt $size; $x++) {
        $v = $base + $rng.Next(-5, 6) + [Math]::Sin($y * 0.9 + $x * 0.07) * 3
        $e = [Math]::Min([Math]::Min($x, $y), [Math]::Min($size - 1 - $x, $size - 1 - $y))
        if ($e -eq 0) { $v = $base - 95 }
        elseif ($e -eq 1) { $v = $base - 40 }
        elseif ($e -eq 2) { if ($x -lt $size / 2 -or $y -lt $size / 2) { $v += 42 } else { $v += 18 } }
        elseif ($e -eq 3) { $v += 12 }
        # soft ambient occlusion toward the edges
        $v -= [Math]::Max(0, 8 - $e) * 1.5
        $c = Clamp $v
        $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c, $c, $c)) } }
    SavePng $b $rel
}
Panel 64 222 'textures/item/weapon_panel.png'

# Emissive: bright core with slightly dimmer rim, so glow strips read as lit tubes
$b = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $dx = ($x - 7.5) / 7.5; $dy = ($y - 7.5) / 7.5
    $d = [Math]::Max([Math]::Abs($dx), [Math]::Abs($dy))
    $c = Clamp (255 - 70 * [Math]::Pow($d, 3))
    $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c, $c, $c)) } }
SavePng $b 'textures/item/weapon_emissive.png'

# Aircraft skin: panel lines, rivet rows and subtle weathering
$s = 128
$b = New-Object System.Drawing.Bitmap $s, $s
for ($y = 0; $y -lt $s; $y++) { for ($x = 0; $x -lt $s; $x++) {
    $v = 206 + $rng.Next(-6, 7) + [Math]::Sin($x * 0.05) * 4
    if (($x % 32) -eq 0 -or ($y % 42) -eq 0) { $v -= 70 }
    if ((($x % 32) -eq 3) -and (($y % 4) -eq 2)) { $v -= 30 }
    if ((($y % 42) -eq 3) -and (($x % 4) -eq 2)) { $v -= 30 }
    $e = [Math]::Min([Math]::Min($x, $y), [Math]::Min($s - 1 - $x, $s - 1 - $y))
    if ($e -eq 0) { $v -= 60 }
    if ($rng.Next(0, 40) -eq 0) { $v -= 14 }
    $c = Clamp $v
    $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c, $c, $c)) } }
SavePng $b 'textures/entity/aircraft_skin.png'

# FX sprites (white, tinted at render time, drawn additively)
function Sprite($w, $h, $rel, $fn) {
    $b = New-Object System.Drawing.Bitmap $w, $h
    for ($y = 0; $y -lt $h; $y++) { for ($x = 0; $x -lt $w; $x++) {
        $u = ($x + 0.5) / $w * 2 - 1; $v = ($y + 0.5) / $h * 2 - 1
        $a = [Math]::Max(0, [Math]::Min(1, (& $fn $u $v)))
        $c = Clamp (255 * $a)
        $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c, $c, $c)) } }
    SavePng $b $rel
}
Sprite 64 64 'textures/entity/fx_glow.png' { param($u, $v) $r = [Math]::Sqrt($u * $u + $v * $v); [Math]::Pow([Math]::Max(0, 1 - $r), 2.2) }
Sprite 128 128 'textures/entity/fx_ring.png' { param($u, $v) $r = [Math]::Sqrt($u * $u + $v * $v); $d = [Math]::Abs($r - 0.86); [Math]::Exp(-$d * $d * 900) + 0.25 * [Math]::Exp(-$d * $d * 60) * [Math]::Max(0, 1 - $r) }
Sprite 64 16 'textures/entity/fx_streak.png' { param($u, $v) [Math]::Exp(-$v * $v * 9) * [Math]::Pow([Math]::Max(0, 1 - [Math]::Abs($u)), 0.6) }
Sprite 64 64 'textures/entity/fx_star.png' { param($u, $v) $r = [Math]::Sqrt($u * $u + $v * $v); $a = [Math]::Exp(-[Math]::Abs($u) * 22) + [Math]::Exp(-[Math]::Abs($v) * 22); [Math]::Max(0, 1 - $r) * $a * 1.2 + [Math]::Pow([Math]::Max(0, 1 - $r * 2.2), 2) }
Write-Host 'HD assets written.'
