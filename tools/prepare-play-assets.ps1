param(
    [string]$OutputDirectory = "design-assets/google-play/8.6.7"
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path $PSScriptRoot -Parent
$assetRoot = Join-Path $projectRoot $OutputDirectory
$sourceRoot = Join-Path $assetRoot "sources"
$records = @()

function Export-ImageCanvas {
    param([string]$Source, [string]$Destination, [int]$Width, [int]$Height, [Drawing.Color]$Background)
    $image = [Drawing.Image]::FromFile($Source)
    $bitmap = [Drawing.Bitmap]::new($Width, $Height, [Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear($Background)
        $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $scale = [Math]::Min($Width / $image.Width, $Height / $image.Height)
        $drawWidth = [int][Math]::Round($image.Width * $scale)
        $drawHeight = [int][Math]::Round($image.Height * $scale)
        $drawX = [int][Math]::Floor(($Width - $drawWidth) / 2)
        $drawY = [int][Math]::Floor(($Height - $drawHeight) / 2)
        $graphics.DrawImage($image, $drawX, $drawY, $drawWidth, $drawHeight)
        New-Item -ItemType Directory -Path (Split-Path $Destination -Parent) -Force | Out-Null
        $bitmap.Save($Destination, [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
        $image.Dispose()
    }
}

foreach ($index in 1..3) {
    $source = Join-Path $sourceRoot ('phone-{0:d2}-full.jpg' -f $index)
    $destination = Join-Path $assetRoot ('phone/phone-{0:d2}-720x1280.png' -f $index)
    $background = switch ($index) {
        1 { [Drawing.Color]::FromArgb(252, 253, 253) }
        2 { [Drawing.Color]::FromArgb(7, 12, 16) }
        3 { [Drawing.Color]::FromArgb(170, 171, 171) }
    }
    Export-ImageCanvas $source $destination 720 1280 $background
}

Export-ImageCanvas (Join-Path $sourceRoot 'feature-full.png') (Join-Path $assetRoot 'common/feature-1024x500.png') 1024 500 ([Drawing.Color]::FromArgb(7, 15, 24))
Export-ImageCanvas (Join-Path $sourceRoot 'feature-full.png') (Join-Path $assetRoot 'tv/tv-banner-1280x720.png') 1280 720 ([Drawing.Color]::FromArgb(7, 15, 24))
Export-ImageCanvas (Join-Path $projectRoot 'design-assets/dadway_launcher_smooth-v5-source.png') (Join-Path $assetRoot 'common/icon-512x512.png') 512 512 ([Drawing.Color]::Black)

foreach ($assetDirectory in @('phone', 'common', 'tv')) {
    foreach ($file in Get-ChildItem (Join-Path $assetRoot $assetDirectory) -File -Filter '*.png') {
        $check = [Drawing.Image]::FromFile($file.FullName)
        try {
            if ($check.PixelFormat -ne [Drawing.Imaging.PixelFormat]::Format24bppRgb) { throw "Expected RGB PNG without alpha: $($file.Name)" }
            $records += [pscustomobject]@{
                File = "$assetDirectory/$($file.Name)"
                Width = $check.Width
                Height = $check.Height
                Format = 'PNG RGB 24-bit, no alpha'
                Bytes = $file.Length
                SHA256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLower()
                Status = if ($assetDirectory -eq 'phone') { 'Technical conversion of real 8.5.7 screenshot; refresh for 8.6.7 recommended' } else { 'Brand asset' }
            }
            if ($file.Length -gt 8MB) { throw "Image exceeds 8 MB: $($file.Name)" }
        } finally { $check.Dispose() }
    }
}
$records | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $assetRoot 'validation.json') -Encoding utf8
$records | Format-Table File,Width,Height,Bytes
