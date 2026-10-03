<#
  Generates Arcana material part textures for Tinkers' Construct.

  TiC renders a material from a dedicated sprite (`<part>_<namespace>_<path>.png`) when
  one exists, and only falls back to tinting a shared sprite with the material's `color`
  when it does not. The dedicated sprite is what makes a material read as its own colour
  instead of a flat multiply, so every part TiC can ask for needs one.

  The part list is the union of three sources:

    1. TiC's own `tinkering/generator_part_textures.json` - the authoritative list its
       texture generator walks, and the list verified against TiC's shipped textures
       part for part. It carries the parts that never appear in a checked-in model
       (broken states, bow draw frames, part items); those models are datagen output and
       live only in the build directory.
    2. Texture references in `models/item/**/*.json`, as a catch-all for anything the
       generator list does not cover.
    3. The neutral layers under `textures/tinker_armor`.

  The two sources are treated differently, because TiC does:

    - Everything on the generator list is recoloured unconditionally, greyscale or not.
      TiC's GreyToColorMapping is built for colour input: it takes the highest channel as
      the grey index and scales the other two down by ratio. Bow limbs, goggles and the
      melting pan handle are colour sources and TiC still gives them material textures.
    - A part reached only through a model reference has to be pure greyscale to be
      treated as a palette mask. Those are the fixed-look sprites (staff crystals, the
      swasher tank, flint and brick); TiC ships no material variants of them and tinting
      them would repaint artwork that is meant to stay put.

  TiC's resources are read from every root that exists (build output first, then the
  source tree), because the generated models and the generator list are present only in
  the build output. Reading a single root silently drops whole part families.

  Parts that carry a stat type are emitted only for materials that actually declare that
  stat, mirroring MaterialPartTextureGenerator's own `supportStatType` filter. TiC gives
  iron bow limbs but no iron bowstring, and the same rule applies here.

  Material palettes and supported stats are read straight out of
  `assets/arcana/tinkering/materials/*.json`, so the textures can never drift from the
  material definitions.

  Usage:
    powershell -ExecutionPolicy Bypass -File tools/generate_material_textures.ps1 -DryRun
    powershell -ExecutionPolicy Bypass -File tools/generate_material_textures.ps1 -Apply
#>
param(
  [switch]$Apply,
  [switch]$DryRun
)

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$tcRoot   = Join-Path (Split-Path -Parent $repoRoot) 'TinkersConstruct-1.21.1'

# Every TiC resource root that exists, build output first: the generator list and the
# datagen models (longbow/pulling_1.json and friends) only exist there.
$resourceRoots = @(
  (Join-Path $tcRoot 'build\resources\main'),
  (Join-Path $tcRoot 'src\generated\resources'),
  (Join-Path $tcRoot 'src\main\resources')
) | Where-Object { Test-Path $_ }
if ($resourceRoots.Count -eq 0) { throw "No Tinkers' Construct resource root found under $tcRoot" }

$materialDir = Join-Path $repoRoot 'src\main\resources\assets\arcana\tinkering\materials'
$outputRoots = @(
  (Join-Path $repoRoot 'src\main\resources\assets\tconstruct\textures'),
  (Join-Path $repoRoot 'resourcepacks\arcana-resources\assets\tconstruct\textures')
)

$greyAnchors = @(0, 63, 102, 140, 178, 216, 255)

# ---- material palettes and stat support, straight from the render definitions ------
$palettes = [ordered]@{}
$supported = @{}
foreach ($file in (Get-ChildItem $materialDir -Filter *.json | Sort-Object Name)) {
  $def = Get-Content $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
  $anchors = @($def.generator.transformer.color_mapping.palette) | Sort-Object { [int]$_.grey }
  if ($anchors.Count -eq 0) { throw "$($file.Name) has no generator palette" }
  $palettes[$file.BaseName] = @($anchors | ForEach-Object { [string]$_.color })
  $supported[$file.BaseName] = @($def.generator.supported_stats | ForEach-Object { [string]$_ })
}

function Interpolate-Channel {
  # Mirrors GreyToColorMapping.interpolate: integer division truncating toward zero.
  param([int]$A, [int]$B, [int]$X, [int]$Divisor)
  return $A + [int][Math]::Truncate((($B - $A) * $X) / $Divisor)
}

function New-Lut {
  param([string[]]$Palette)
  # 0..255 grey -> ARGB, interpolation between anchors, mirroring
  # GreyToColorMapping.getNearestByGrey / interpolateColors.
  $lut = New-Object 'int[]' 256
  for ($g = 0; $g -le 255; $g++) {
    $upper = 0
    while ($upper -lt $greyAnchors.Count -and $greyAnchors[$upper] -lt $g) { $upper++ }
    if ($upper -eq 0) {
      $lut[$g] = [Convert]::ToInt32($Palette[0], 16)
    } elseif ($upper -ge $greyAnchors.Count) {
      $lut[$g] = [Convert]::ToInt32($Palette[$Palette.Count - 1], 16)
    } else {
      $lo = $upper - 1
      $num = $g - $greyAnchors[$lo]
      $den = $greyAnchors[$upper] - $greyAnchors[$lo]
      $v1 = [Convert]::ToInt32($Palette[$lo], 16)
      $v2 = [Convert]::ToInt32($Palette[$upper], 16)
      $a  = Interpolate-Channel ($v1 -shr 24 -band 0xFF) ($v2 -shr 24 -band 0xFF) $num $den
      $r  = Interpolate-Channel ($v1 -shr 16 -band 0xFF) ($v2 -shr 16 -band 0xFF) $num $den
      $gr = Interpolate-Channel ($v1 -shr 8  -band 0xFF) ($v2 -shr 8  -band 0xFF) $num $den
      $b  = Interpolate-Channel ($v1 -band 0xFF)         ($v2 -band 0xFF)         $num $den
      $lut[$g] = ($a -shl 24) -bor ($r -shl 16) -bor ($gr -shl 8) -bor $b
    }
  }
  return $lut
}

# ---- 1. TiC's own generator part list ---------------------------------------------
# part path -> @() when the part applies to every material, @(statType, ...) otherwise.
# $listedParts records which parts TiC itself recolours regardless of source colours.
$parts = @{}
$listedParts = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($root in $resourceRoots) {
  $generatorList = Join-Path $root 'assets\tconstruct\tinkering\generator_part_textures.json'
  if (-not (Test-Path $generatorList)) { continue }
  $json = Get-Content $generatorList -Raw | ConvertFrom-Json
  foreach ($part in $json.parts) {
    $path = [string]$part.path
    if ($path -notmatch '^(?:tconstruct:)?([a-z0-9_/]+)$') { continue }
    $rel = $Matches[1]
    if ($rel -notmatch '/') { continue }
    $stats = @()
    foreach ($field in @('stat_type', 'stat_types')) {
      if ($part.PSObject.Properties.Name -contains $field) {
        foreach ($value in @($part.$field)) { if ($value) { $stats += [string]$value } }
      }
    }
    if (-not $parts.ContainsKey($rel)) { $parts[$rel] = $stats }
    $null = $listedParts.Add($rel)
  }
  break
}

# ---- 2. texture references in the item models --------------------------------------
# Only the "textures" block counts: strings like "tconstruct:item/tool/pickaxe/blocking"
# are model references, not sprites.
foreach ($root in $resourceRoots) {
  $modelRoot = Join-Path $root 'assets\tconstruct\models\item'
  if (-not (Test-Path $modelRoot)) { continue }
  Get-ChildItem -Recurse $modelRoot -Filter *.json | ForEach-Object {
    $json = Get-Content $_.FullName -Raw | ConvertFrom-Json
    if ($null -eq $json.textures) { return }
    foreach ($prop in $json.textures.PSObject.Properties) {
      $value = [string]$prop.Value
      if ($value -match '^(?:tconstruct:)?(item/tool/[a-z0-9_/]+)$') {
        if (-not $parts.ContainsKey($Matches[1])) { $parts[$Matches[1]] = @() }
      }
    }
  }
}

# ---- 3. neutral armor layers -------------------------------------------------------
# Variants that already name a specific look (_metal, _contrast, _cloth, material
# specific) are fallbacks in their own right, so only the neutral layers get derived.
$armorExclude = '_metal$|_contrast$|_cloth$|_tconstruct_'
foreach ($root in $resourceRoots) {
  $armorRoot = Join-Path $root 'assets\tconstruct\textures\tinker_armor'
  if (-not (Test-Path $armorRoot)) { continue }
  Get-ChildItem -Recurse $armorRoot -Filter *.png | ForEach-Object {
    if ($_.BaseName -match $armorExclude) { return }
    $rel = 'tinker_armor/' + ($_.FullName.Substring($armorRoot.Length + 1) -replace '\\', '/' -replace '\.png$', '')
    if (-not $parts.ContainsKey($rel)) { $parts[$rel] = @() }
  }
}

$luts = [ordered]@{}
foreach ($mat in $palettes.Keys) { $luts[$mat] = New-Lut -Palette $palettes[$mat] }

$written = 0; $skippedColored = 0; $missing = 0; $filtered = 0
$perMaterial = [ordered]@{}
foreach ($mat in $palettes.Keys) { $perMaterial[$mat] = 0 }
$notes = New-Object System.Collections.Generic.List[string]

foreach ($rel in ($parts.Keys | Sort-Object)) {
  $sourcePng = $null
  foreach ($root in $resourceRoots) {
    $candidate = Join-Path $root ('assets\tconstruct\textures\' + ($rel -replace '/', '\') + '.png')
    if (Test-Path $candidate) { $sourcePng = $candidate; break }
  }
  if (-not $sourcePng) { $missing++; $notes.Add("missing source: $rel"); continue }

  $wanted = @()
  foreach ($mat in $palettes.Keys) {
    $required = @($parts[$rel])
    if ($required.Count -gt 0 -and -not ($required | Where-Object { $supported[$mat] -contains $_ })) {
      $filtered++
      continue
    }
    $wanted += $mat
  }
  if ($wanted.Count -eq 0) { continue }

  $bitmap = [System.Drawing.Bitmap]::FromFile($sourcePng)
  try {
    $w = $bitmap.Width; $h = $bitmap.Height
    $count = $w * $h
    $pa = New-Object 'int[]' $count
    $pr = New-Object 'int[]' $count
    $pg = New-Object 'int[]' $count
    $pb = New-Object 'int[]' $count

    $isGrey = $true; $i = 0
    for ($y = 0; $y -lt $h; $y++) {
      for ($x = 0; $x -lt $w; $x++) {
        $c = $bitmap.GetPixel($x, $y)
        $pa[$i] = $c.A; $pr[$i] = $c.R; $pg[$i] = $c.G; $pb[$i] = $c.B
        if ($c.A -ne 0 -and -not ($c.R -eq $c.G -and $c.G -eq $c.B)) { $isGrey = $false }
        $i++
      }
    }
    # Off-list sprites are fixed artwork; only the generator list is recoloured as-is.
    if (-not $isGrey -and -not $listedParts.Contains($rel)) {
      $skippedColored++; $notes.Add("skip (colored source, off-list): $rel"); continue
    }

    foreach ($mat in $wanted) {
      $lut = $luts[$mat]
      $relOut = "$($rel)_arcana_$mat"
      foreach ($root in $outputRoots) {
        if (-not $Apply) { continue }
        $outPath = (Join-Path $root ($relOut -replace '/', '\')) + '.png'
        $outDir = Split-Path -Parent $outPath
        if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
        $out = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        for ($idx = 0; $idx -lt $count; $idx++) {
          $a = $pa[$idx]
          if ($a -eq 0) { continue }   # new bitmap is zero-filled, i.e. fully transparent
          $r = $pr[$idx]; $g = $pg[$idx]; $b = $pb[$idx]
          # GreyToColorMapping.getGrey: the highest channel is the palette index.
          $grey = [Math]::Max($r, [Math]::Max($g, $b))
          $nc = $lut[$grey]
          $na = ($nc -shr 24) -band 0xFF; $nr = ($nc -shr 16) -band 0xFF
          $ng = ($nc -shr 8 -band 0xFF);  $nb = $nc -band 0xFF
          # GreyToColorMapping.scaleColor: pull channels down when the source is not pure
          # grey, and fold the source alpha into the mapped alpha. Both use Java integer
          # division, i.e. truncation toward zero, not PowerShell's rounding cast.
          if ($r -lt $grey) { $nr = [int][Math]::Truncate($nr * $r / $grey) }
          if ($g -lt $grey) { $ng = [int][Math]::Truncate($ng * $g / $grey) }
          if ($b -lt $grey) { $nb = [int][Math]::Truncate($nb * $b / $grey) }
          if ($a -lt 255) { $na = [int][Math]::Truncate($a * $na / 255) }
          # PowerShell's [int] cast rounds; floor the row instead of truncating via cast.
          $out.SetPixel(($idx % $w), [int][Math]::Floor($idx / $w), [System.Drawing.Color]::FromArgb($na, $nr, $ng, $nb))
        }
        $out.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $out.Dispose()
        $written++
      }
      $perMaterial[$mat]++
      if (-not $Apply) { $written += $outputRoots.Count }
    }
  } finally {
    $bitmap.Dispose()
  }
}

"parts considered  : $($parts.Count)  (generator list: $($listedParts.Count))"
"part/material cut : $filtered  (stat not declared by that material)"
"source missing    : $missing"
"source not grey   : $skippedColored  (off-list fixed artwork)"
"images generated  : $written"
foreach ($mat in $palettes.Keys) { "  $mat : $($perMaterial[$mat]) parts" }
if ($notes.Count -gt 0) { "--- notes ---"; $notes | ForEach-Object { $_ } }
if (-not $Apply) { "(dry run - nothing written)" }
