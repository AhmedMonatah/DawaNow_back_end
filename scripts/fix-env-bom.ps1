$p = (Resolve-Path '.env').Path
$text = [System.IO.File]::ReadAllText($p)
if ($text.Length -gt 0 -and [int][char]$text[0] -eq 0xFEFF) {
    $text = $text.Substring(1)
}
$text = $text.Replace("`r`n", "`n").Replace("`r", "`n")
$enc = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($p, $text, $enc)
$b = [System.IO.File]::ReadAllBytes($p)
$hasBom = ($b.Length -ge 3 -and $b[0] -eq 239 -and $b[1] -eq 187 -and $b[2] -eq 191)
Write-Host "bom=$hasBom first3=$($b[0]),$($b[1]),$($b[2])"
