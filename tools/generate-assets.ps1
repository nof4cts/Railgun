# Generates every texture and JSON model the mod uses.
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File tools/generate-assets.ps1
# Everything is procedural and original, so there are no third-party asset licences to track.

param([string]$OutRoot = '')
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
# -OutRoot lets you generate into a short path first (GDI+ / .NET Framework dislike paths over 260 chars)
$root = if ($OutRoot) { $OutRoot } else { [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\src\main\resources\assets\aegiscommand')) }
$inv = [System.Globalization.CultureInfo]::InvariantCulture

function Ensure($p) { $d = Split-Path $p -Parent; if (-not (Test-Path $d)) { New-Item -ItemType Directory -Force $d | Out-Null } }
function Clamp([double]$v) { if ($v -lt 0) { return 0 } if ($v -gt 255) { return 255 } return [int]$v }
function SavePng($bmp, $rel) {
    $p = Join-Path $root $rel; Ensure $p
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    [System.IO.File]::WriteAllBytes($p, $ms.ToArray()); $ms.Dispose(); $bmp.Dispose()
}
function WriteText($rel, $text) { $p = Join-Path $root $rel; Ensure $p; [System.IO.File]::WriteAllText($p, $text, (New-Object System.Text.UTF8Encoding($false))) }
function F([double]$v) { return $v.ToString('0.###', $inv) }

$rng = New-Object System.Random 1337

# ---------------------------------------------------------------- textures
function MetalTexture($size, $base, $panel, $rel) {
    $b = New-Object System.Drawing.Bitmap $size, $size
    for ($y = 0; $y -lt $size; $y++) {
        for ($x = 0; $x -lt $size; $x++) {
            $v = $base + $rng.Next(-9, 10)
            # brushed streaks
            $v += [Math]::Sin(($y * 1.7) + ($x * 0.15)) * 3
            # panel seams
            if (($x % $panel) -eq 0 -or ($y % $panel) -eq 0) { $v -= 38 }
            if ((($x % $panel) -eq 1) -or (($y % $panel) -eq 1)) { $v += 16 }
            # rivets
            if ((($x % $panel) -eq 3) -and (($y % $panel) -eq 3)) { $v += 40 }
            $c = Clamp $v
            $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c, $c, $c))
        }
    }
    SavePng $b $rel
}

MetalTexture 32 168 8 'textures/item/weapon_metal.png'
MetalTexture 32 92 16 'textures/item/weapon_metal_dark.png'

# energy glow: white with scanline bands, tinted at render time
$b = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $v = 228 + [Math]::Round(27 * [Math]::Abs([Math]::Sin($y * 0.8)))
    if (($y % 4) -eq 0) { $v = 200 }
    $c = Clamp $v
    $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c, $c, $c)) } }
SavePng $b 'textures/item/weapon_glow.png'

# laptop body
$b = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $v = 46 + $rng.Next(-4, 5); if ($x -eq 0 -or $y -eq 0) { $v += 22 }; if ($x -eq 15 -or $y -eq 15) { $v -= 14 }
    $c = Clamp $v; $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c, (Clamp ($c + 2)), (Clamp ($c + 5)))) } }
SavePng $b 'textures/block/laptop_body.png'

# laptop keyboard
$b = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $key = (($x % 2) -eq 0) -and (($y % 3) -ne 0)
    $v = if ($key) { 30 } else { 58 }
    if ($key -and $y -ge 13 -and $x -ge 4 -and $x -le 11) { $v = 30 }
    $c = Clamp ($v + $rng.Next(-2, 3))
    $g = if ($key -and $rng.Next(0, 9) -eq 0) { 90 } else { $c }
    $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c, $g, $c)) } }
SavePng $b 'textures/block/laptop_keys.png'

# animated laptop screen: 8 frames of a radar sweep
$frames = 8
$b = New-Object System.Drawing.Bitmap 16, (16 * $frames)
for ($f = 0; $f -lt $frames; $f++) {
    $ang = ($f / $frames) * 2 * [Math]::PI
    for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        $r = 4; $g = 14; $bb = 9
        if ($y -le 1) { $r = 10; $g = 60; $bb = 30 }
        $dx = $x - 7.5; $dy = $y - 9
        $d = [Math]::Sqrt($dx * $dx + $dy * $dy)
        if ([Math]::Abs($d - 5.5) -lt 0.55 -or [Math]::Abs($d - 3) -lt 0.45) { $g = 70; $r = 10; $bb = 30 }
        if ($x -eq 7 -or $y -eq 9) { if ($d -lt 6) { $g = [Math]::Max($g, 45) } }
        $a = [Math]::Atan2($dy, $dx); $diff = ($ang - $a) % (2 * [Math]::PI); if ($diff -lt 0) { $diff += 2 * [Math]::PI }
        if ($d -lt 6 -and $diff -lt 1.2) { $k = 1 - $diff / 1.2; $g = [Math]::Max($g, [int](70 + 160 * $k)); $r = [Math]::Max($r, [int](20 * $k)) }
        if (($x -eq 10 -and $y -eq 7) -or ($x -eq 5 -and $y -eq 11)) { $r = 255; $g = 170; $bb = 40 }
        if ($y -eq 15 -and ($x % 3) -ne 0) { $g = 80 }
        $b.SetPixel($x, $y + 16 * $f, [System.Drawing.Color]::FromArgb(255, (Clamp $r), (Clamp $g), (Clamp $bb))) } }
}
SavePng $b 'textures/block/laptop_screen.png'
WriteText 'textures/block/laptop_screen.png.mcmeta' '{ "animation": { "frametime": 3 } }'

# aircraft hull (tinted per aircraft at render time)
$b = New-Object System.Drawing.Bitmap 256, 256
for ($y = 0; $y -lt 256; $y++) { for ($x = 0; $x -lt 256; $x++) {
    $v = 178 + $rng.Next(-7, 8)
    $px = [int][Math]::Floor($x / 24); $py = [int][Math]::Floor($y / 12)
    $v += ((($px * 7 + $py * 13) % 5) - 2) * 4
    if (($x % 24) -eq 0 -or ($y % 12) -eq 0) { $v -= 45 }
    if ((($x % 24) -eq 2) -and (($y % 12) -eq 2)) { $v -= 30 }
    if ((($x % 48) -eq 24) -and (($y % 6) -eq 3)) { $v -= 20 }
    $c = Clamp $v; $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c, $c, $c)) } }
SavePng $b 'textures/entity/aircraft.png'

# additive glows (black = transparent under the "eyes" render type)
function RadialGlow($size, $rel, $falloff) {
    $b = New-Object System.Drawing.Bitmap $size, $size
    $h = ($size - 1) / 2.0
    for ($y = 0; $y -lt $size; $y++) { for ($x = 0; $x -lt $size; $x++) {
        $d = [Math]::Sqrt(($x - $h) * ($x - $h) + ($y - $h) * ($y - $h)) / $h
        $k = [Math]::Max(0, 1 - $d); $k = [Math]::Pow($k, $falloff)
        $c = Clamp (255 * $k)
        $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c, $c, $c)) } }
    SavePng $b $rel
}
RadialGlow 32 'textures/entity/energy_bolt.png' 1.6
$b = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $c = Clamp (200 + $rng.Next(0, 55)); $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c, $c, $c)) } }
SavePng $b 'textures/entity/engine_glow.png'

# ---------------------------------------------------------------- models
# A box: from, to, texture key, optional rotation @(axis, angle, ox, oy, oz), optional full-uv faces
function Box($from, $to, $tex, $rot = $null, $shade = $true) { return @{ f = $from; t = $to; tex = $tex; rot = $rot; shade = $shade } }

function FaceUv($a, $b, $seed) {
    $a = [Math]::Min([Math]::Abs($a), 16); $b = [Math]::Min([Math]::Abs($b), 16)
    $u = 0; $v = 0
    if ($a -lt 16) { $u = [Math]::Floor(($seed * 3.7) % (16 - $a)) }
    if ($b -lt 16) { $v = [Math]::Floor(($seed * 5.3) % (16 - $b)) }
    return "[$(F $u), $(F $v), $(F ($u + $a)), $(F ($v + $b))]"
}

function ElementJson($e, $i) {
    $f = $e.f; $t = $e.t
    $w = $t[0] - $f[0]; $h = $t[1] - $f[1]; $d = $t[2] - $f[2]
    $tex = $e.tex
    $faces = @(
        "`"north`": {`"uv`": $(FaceUv $w $h ($i + 1)), `"texture`": `"#$tex`"}",
        "`"south`": {`"uv`": $(FaceUv $w $h ($i + 2)), `"texture`": `"#$tex`"}",
        "`"east`": {`"uv`": $(FaceUv $d $h ($i + 3)), `"texture`": `"#$tex`"}",
        "`"west`": {`"uv`": $(FaceUv $d $h ($i + 4)), `"texture`": `"#$tex`"}",
        "`"up`": {`"uv`": $(FaceUv $w $d ($i + 5)), `"texture`": `"#$tex`"}",
        "`"down`": {`"uv`": $(FaceUv $w $d ($i + 6)), `"texture`": `"#$tex`"}"
    ) -join ', '
    $s = "    {`"from`": [$(F $f[0]), $(F $f[1]), $(F $f[2])], `"to`": [$(F $t[0]), $(F $t[1]), $(F $t[2])]"
    if (-not $e.shade) { $s += ", `"shade`": false" }
    if ($e.rot) { $r = $e.rot; $s += ", `"rotation`": {`"axis`": `"$($r[0])`", `"angle`": $(F $r[1]), `"origin`": [$(F $r[2]), $(F $r[3]), $(F $r[4])]}" }
    $s += ", `"faces`": {$faces}}"
    return $s
}

function ModelJson($textures, $boxes) {
    $texJson = ($textures.GetEnumerator() | Sort-Object Name | ForEach-Object { "    `"$($_.Name)`": `"$($_.Value)`"" }) -join ",`n"
    $i = 0
    $els = ($boxes | ForEach-Object { $i++; ElementJson $_ $i }) -join ",`n"
    return "{`n  `"textures`": {`n$texJson`n  },`n  `"elements`": [`n$els`n  ]`n}`n"
}

$bodyTex = @{ metal = 'aegiscommand:item/weapon_metal'; dark = 'aegiscommand:item/weapon_metal_dark'; particle = 'aegiscommand:item/weapon_metal' }
$glowTex = @{ glow = 'aegiscommand:item/weapon_glow'; particle = 'aegiscommand:item/weapon_glow' }
$gripRot = { param($oz, $oy) @('x', -22.5, 8, $oy, $oz) }

# ---- PHANTOM-9 : sleek black energy rifle, cyan core
$p9 = @(
    (Box @(6.5, 7, 2) @(9.5, 8, 14) 'metal'),
    (Box @(6.5, 9.6, 2) @(9.5, 10, 14) 'metal'),
    (Box @(6.5, 8, 10) @(9.5, 9.6, 14) 'metal'),
    (Box @(6.5, 8, 2) @(9.5, 9.6, 4) 'metal'),
    (Box @(6.8, 10, 0) @(9.2, 11, 13) 'dark'),
    (Box @(7, 7.5, -8) @(9, 9.8, 2) 'metal'),
    (Box @(7.25, 9.8, -6) @(8.75, 10.4, 0) 'dark'),
    (Box @(7.5, 8, -13) @(8.5, 9, -8) 'dark'),
    (Box @(7.2, 7.7, -14) @(8.8, 9.3, -12.5) 'metal'),
    (Box @(6.2, 8.4, -4) @(7, 9.4, 2) 'dark'),
    (Box @(9, 8.4, -4) @(9.8, 9.4, 2) 'dark'),
    (Box @(7.2, 3.4, 10) @(8.8, 7, 12.4) 'dark' (& $gripRot 11 7)),
    (Box @(7.6, 6, 7) @(8.4, 6.5, 10) 'dark'),
    (Box @(7, 4.2, 5) @(9, 7, 8) 'metal' @('x', 22.5, 8, 7, 6.5)),
    (Box @(7, 7.6, 14) @(9, 9.8, 20) 'metal'),
    (Box @(7.3, 6.2, 15) @(8.7, 7.6, 19.5) 'dark'),
    (Box @(6.8, 5.8, 19.5) @(9.2, 10.2, 21.5) 'dark'),
    (Box @(7.3, 11, 4) @(8.7, 12.4, 10) 'dark'),
    (Box @(7.6, 10.8, 5) @(8.4, 11, 9) 'metal')
)
$p9g = @(
    (Box @(6.6, 8, 4) @(9.4, 9.6, 10) 'glow' $null $false),
    (Box @(6.9, 8.5, -7.5) @(6.98, 8.8, 1.5) 'glow' $null $false),
    (Box @(9.02, 8.5, -7.5) @(9.1, 8.8, 1.5) 'glow' $null $false),
    (Box @(7.6, 8.1, -14.15) @(8.4, 8.9, -14.02) 'glow' $null $false),
    (Box @(6.95, 5, 5.5) @(9.05, 5.5, 7.5) 'glow' @('x', 22.5, 8, 7, 6.5) $false),
    (Box @(7.4, 11.1, 3.92) @(8.6, 12.3, 3.98) 'glow' $null $false),
    (Box @(6.75, 10.3, 1) @(6.8, 10.6, 12) 'glow' $null $false),
    (Box @(9.2, 10.3, 1) @(9.25, 10.6, 12) 'glow' $null $false)
)

# ---- TITAN-X : heavy industrial cannon, orange chambers
$tx = @(
    (Box @(5, 6, -2) @(11, 7.5, 14) 'metal'),
    (Box @(5, 9.5, -2) @(11, 11, 14) 'metal'),
    (Box @(5, 7.5, -2) @(11, 9.5, 0) 'metal'),
    (Box @(5, 7.5, 3) @(11, 9.5, 4) 'dark'),
    (Box @(5, 7.5, 7) @(11, 9.5, 8) 'dark'),
    (Box @(5, 7.5, 11) @(11, 9.5, 14) 'metal'),
    (Box @(5.5, 11, 0) @(10.5, 12, 12) 'dark'),
    (Box @(4.4, 6.4, 0.5) @(5, 10.6, 12) 'dark'),
    (Box @(11, 6.4, 0.5) @(11.6, 10.6, 12) 'dark'),
    (Box @(6.5, 7, -12) @(9.5, 10, -2) 'metal'),
    (Box @(6, 6.5, -10) @(10, 10.5, -9) 'dark'),
    (Box @(6, 6.5, -6) @(10, 10.5, -5) 'dark'),
    (Box @(5.8, 6.3, -14) @(10.2, 10.7, -12) 'metal'),
    (Box @(7, 4.8, -9) @(9, 6, -1) 'dark'),
    (Box @(5.5, 6.5, 14) @(10.5, 10.5, 19) 'metal'),
    (Box @(6, 7, 19) @(10, 10, 20) 'dark'),
    (Box @(7.3, 12, 3) @(8.7, 13.2, 9) 'dark'),
    (Box @(7.2, 2.4, 8) @(8.8, 6, 10.6) 'dark' (& $gripRot 9.3 6)),
    (Box @(7.3, 3, -4) @(8.7, 6, -2) 'dark')
)
$txg = @(
    (Box @(5.1, 7.5, 0) @(10.9, 9.5, 3) 'glow' $null $false),
    (Box @(5.1, 7.5, 4) @(10.9, 9.5, 7) 'glow' $null $false),
    (Box @(5.1, 7.5, 8) @(10.9, 9.5, 11) 'glow' $null $false),
    (Box @(7, 7.5, -14.12) @(9, 9.5, -14.02) 'glow' $null $false),
    (Box @(7.25, 4.7, -8) @(8.75, 4.8, -2) 'glow' $null $false),
    (Box @(5.4, 7.6, 15) @(5.5, 8.6, 18) 'glow' $null $false),
    (Box @(10.5, 7.6, 15) @(10.6, 8.6, 18) 'glow' $null $false)
)

# ---- SPECTRE-7 : compact plasma SMG, violet circuitry
$s7 = @(
    (Box @(6.8, 7, 0) @(9.2, 9.5, 10) 'metal'),
    (Box @(7, 9.5, -2) @(9, 10.3, 9) 'dark'),
    (Box @(7, 7.3, -6) @(9, 9.3, 0) 'metal'),
    (Box @(7.6, 7.9, -9) @(8.4, 8.7, -6) 'dark'),
    (Box @(7.2, 7.6, -8.8) @(8.8, 9, -8) 'metal'),
    (Box @(7.3, 3.6, 6) @(8.7, 7, 8) 'dark' (& $gripRot 7 7)),
    (Box @(7.4, 4, 1.5) @(8.6, 7, 3.5) 'dark'),
    (Box @(7.5, 7.5, 10) @(8.5, 8.5, 14.5) 'dark'),
    (Box @(7.2, 6.4, 14.5) @(8.8, 9, 15.5) 'metal'),
    (Box @(7.5, 10.3, 1) @(8.5, 11.3, 1.4) 'dark'),
    (Box @(7.5, 10.3, 2.6) @(8.5, 11.3, 3) 'dark'),
    (Box @(6.5, 7.6, -5) @(7, 8.6, -1) 'dark'),
    (Box @(9, 7.6, -5) @(9.5, 8.6, -1) 'dark')
)
$s7g = @(
    (Box @(6.72, 8, 2) @(6.8, 9, 8) 'glow' $null $false),
    (Box @(9.2, 8, 2) @(9.28, 9, 8) 'glow' $null $false),
    (Box @(7.6, 10.3, 4) @(8.4, 10.45, 8.5) 'glow' $null $false),
    (Box @(7.4, 7.7, -8.9) @(8.6, 8.9, -8.82) 'glow' $null $false),
    (Box @(7.35, 4.5, 2) @(8.65, 6.5, 3) 'glow' $null $false),
    (Box @(7.6, 10.4, 1.9) @(8.4, 11.2, 2.1) 'glow' $null $false),
    (Box @(6.95, 7.3, -5.5) @(7, 7.6, -0.5) 'glow' $null $false),
    (Box @(9, 7.3, -5.5) @(9.05, 7.6, -0.5) 'glow' $null $false)
)

WriteText 'models/weapon/phantom_9_body.json' (ModelJson $bodyTex $p9)
WriteText 'models/weapon/phantom_9_glow.json' (ModelJson $glowTex $p9g)
WriteText 'models/weapon/titan_x_body.json' (ModelJson $bodyTex $tx)
WriteText 'models/weapon/titan_x_glow.json' (ModelJson $glowTex $txg)
WriteText 'models/weapon/spectre_7_body.json' (ModelJson $bodyTex $s7)
WriteText 'models/weapon/spectre_7_glow.json' (ModelJson $glowTex $s7g)

# item models: rendered by the mod's own renderer ("builtin/entity"), these only set the hand/GUI transforms
function WeaponItemJson($guiScale, $guiShift, $fpScale, $tpScale) {
    $g = F $guiScale; $gs = F $guiShift; $fp = F $fpScale; $tp = F $tpScale
    return @"
{
  "parent": "builtin/entity",
  "gui_light": "front",
  "textures": { "particle": "aegiscommand:item/weapon_metal" },
  "display": {
    "gui": { "rotation": [ 0, -90, 0 ], "translation": [ $gs, 0, 0 ], "scale": [ $g, $g, $g ] },
    "fixed": { "rotation": [ 0, -90, 0 ], "translation": [ $gs, 0, 0 ], "scale": [ $g, $g, $g ] },
    "ground": { "rotation": [ 0, 0, 0 ], "translation": [ 0, 2, 0 ], "scale": [ 0.3, 0.3, 0.3 ] },
    "head": { "rotation": [ 0, 180, 0 ], "translation": [ 0, 10, 0 ], "scale": [ 0.6, 0.6, 0.6 ] },
    "firstperson_righthand": { "rotation": [ 0, 0, 0 ], "translation": [ -1, 1.5, -1 ], "scale": [ $fp, $fp, $fp ] },
    "firstperson_lefthand": { "rotation": [ 0, 0, 0 ], "translation": [ 1, 1.5, -1 ], "scale": [ $fp, $fp, $fp ] },
    "thirdperson_righthand": { "rotation": [ 0, 0, 0 ], "translation": [ 0, 1.6, -1.6 ], "scale": [ $tp, $tp, $tp ] },
    "thirdperson_lefthand": { "rotation": [ 0, 0, 0 ], "translation": [ 0, 1.6, -1.6 ], "scale": [ $tp, $tp, $tp ] }
  }
}
"@
}
WriteText 'models/item/phantom_9.json' (WeaponItemJson 0.42 -1.7 0.5 0.55)
WriteText 'models/item/titan_x.json' (WeaponItemJson 0.44 -1.1 0.5 0.55)
WriteText 'models/item/spectre_7.json' (WeaponItemJson 0.58 -1.8 0.55 0.6)

# laptop block
$lapTex = @{ body = 'aegiscommand:block/laptop_body'; keys = 'aegiscommand:block/laptop_keys'; screen = 'aegiscommand:block/laptop_screen'; particle = 'aegiscommand:block/laptop_body' }
$lidRot = @('x', 22.5, 8, 1, 12)
$lap = @(
    (Box @(1, 0, 2) @(15, 1, 12) 'body'),
    (Box @(2, 1, 3.5) @(14, 1.15, 9.5) 'keys'),
    (Box @(6, 1, 9.8) @(10, 1.08, 11.4) 'keys'),
    (Box @(2, 0.6, 11.3) @(14, 1.6, 12.3) 'body'),
    (Box @(1, 1, 11) @(15, 12, 12) 'body' $lidRot),
    (Box @(1.7, 1.8, 10.92) @(14.3, 11.3, 11) 'screen' $lidRot $false)
)
$lapJson = (ModelJson $lapTex $lap)
# full-texture mapping for the screen face and keyboard top
$lapJson = $lapJson.Replace('{"from": [1.7, 1.8, 10.92], "to": [14.3, 11.3, 11], "shade": false', '{"from": [1.7, 1.8, 10.92], "to": [14.3, 11.3, 11], "shade": false, "__SCREEN__": 1')
$lapJson = [regex]::Replace($lapJson, ', "__SCREEN__": 1(.*?)"north": \{"uv": \[[^\]]*\]', '$1"north": {"uv": [0, 0, 16, 16]')
$lapJson = $lapJson.Replace("{`n  `"textures`"", "{`n  `"parent`": `"block/block`",`n  `"textures`"")
WriteText 'models/block/laptop.json' $lapJson
WriteText 'models/item/laptop.json' '{ "parent": "aegiscommand:block/laptop" }'

Write-Host 'Assets generated.'
