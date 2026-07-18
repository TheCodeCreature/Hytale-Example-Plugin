param(
    [string]$Root = "."
)

$ErrorActionPreference = "Stop"
$rootPath = (Resolve-Path -Path $Root).Path
$allowedTagNamespaces = @("domain", "layer", "type", "kind", "signal", "team")
$excludedFiles = @(
    "SYSTEM_GUIDE_REVIEW.md"
)
$legacyCheckExcludedPrefixes = @(
    "_rules/",
    "SYSTEM_GUIDE_",
    "_coverage/Overview.md"
)

$reportFile = Join-Path -Path $rootPath -ChildPath "_coverage/Report.md"

$issues = New-Object System.Collections.Generic.List[object]

function Add-Issue {
    param(
        [string]$Severity,
        [string]$Check,
        [string]$File,
        [int]$Line,
        [string]$Message
    )

    $issues.Add([pscustomobject]@{
        Severity = $Severity
        Check    = $Check
        File     = $File
        Line     = $Line
        Message  = $Message
    })
}

function Get-RelativePath {
    param([string]$Path)

    $basePath = $rootPath
    if (-not $basePath.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $basePath = $basePath + [System.IO.Path]::DirectorySeparatorChar
    }

    $baseUri = New-Object System.Uri($basePath)
    $targetUri = New-Object System.Uri($Path)
    $relativeUri = $baseUri.MakeRelativeUri($targetUri)

    return [System.Uri]::UnescapeDataString($relativeUri.ToString()).Replace('\\', '/')
}

function Get-Frontmatter {
    param([string[]]$Lines)

    if ($Lines.Count -lt 3) {
        return $null
    }

    if ($Lines[0].Trim() -ne "---") {
        return $null
    }

    $end = -1
    for ($i = 1; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i].Trim() -eq "---") {
            $end = $i
            break
        }
    }

    if ($end -lt 0) {
        return $null
    }

    return @{
        StartLine = 1
        EndLine   = $end + 1
        Lines     = $Lines[1..($end - 1)]
    }
}

$mdFiles = Get-ChildItem -Path $rootPath -Recurse -File -Filter "*.md" |
    Where-Object { $_.FullName -notmatch "\\.git\\" }
$markdownLinkRegex = [regex]"\[[^\]]+\]\(([^)]+)\)"
$frontmatterKeyRegex = [regex]"^([a-zA-Z_][a-zA-Z0-9_-]*):\s*(.*)$"
$frontmatterTagRegex = [regex]"^-\s*(.+)$"
$tagNamespaceRegex = [regex]"^([a-z0-9-]+):([a-z0-9-]+)$"

foreach ($file in $mdFiles) {
    $rawLines = Get-Content -Path $file.FullName
    $relativeFile = Get-RelativePath -Path $file.FullName

    if ($excludedFiles -contains $relativeFile) {
        continue
    }

    $excludeLegacyCheck = $false
    foreach ($prefix in $legacyCheckExcludedPrefixes) {
        if ($relativeFile.StartsWith($prefix)) {
            $excludeLegacyCheck = $true
            break
        }
    }

    # Check 1: legacy Details.md references
    $inCodeFence = $false
    for ($i = 0; $i -lt $rawLines.Count; $i++) {
        $line = $rawLines[$i]
        if ($line.Trim().StartsWith('```')) {
            $inCodeFence = -not $inCodeFence
            continue
        }

        if (-not $excludeLegacyCheck -and -not $inCodeFence -and $line -match "Details\.md") {
            Add-Issue -Severity "error" -Check "legacy-details-reference" -File $relativeFile -Line ($i + 1) -Message "Found legacy Details.md reference. Use Overview.md as canonical container file."
        }
    }

    # Check 2: markdown link target existence for local links
    $inCodeFence = $false
    for ($i = 0; $i -lt $rawLines.Count; $i++) {
        $line = $rawLines[$i]
        if ($line.Trim().StartsWith('```')) {
            $inCodeFence = -not $inCodeFence
            continue
        }

        if ($inCodeFence) {
            continue
        }

        $linkMatch = $markdownLinkRegex.Match($line)
        while ($linkMatch.Success) {
            $target = $linkMatch.Groups[1].Value.Trim()

            if ([string]::IsNullOrWhiteSpace($target)) {
                $linkMatch = $linkMatch.NextMatch()
                continue
            }

            if ($target -match "^(https?:|mailto:|#)") {
                $linkMatch = $linkMatch.NextMatch()
                continue
            }

            $targetNoAnchor = $target.Split('#')[0]
            if ([string]::IsNullOrWhiteSpace($targetNoAnchor)) {
                $linkMatch = $linkMatch.NextMatch()
                continue
            }

            $targetPath = [System.Uri]::UnescapeDataString($targetNoAnchor)

            if ($targetPath.Contains("...")) {
                $linkMatch = $linkMatch.NextMatch()
                continue
            }

            # Ignore wiki links if they appear inside markdown link targets unexpectedly.
            if ($targetPath -match "^\[\[") {
                $linkMatch = $linkMatch.NextMatch()
                continue
            }

            $candidate = Join-Path -Path $file.DirectoryName -ChildPath $targetPath
            if (-not (Test-Path -LiteralPath $candidate)) {
                Add-Issue -Severity "error" -Check "broken-markdown-link" -File $relativeFile -Line ($i + 1) -Message "Link target does not exist: $target"
            }

            $linkMatch = $linkMatch.NextMatch()
        }
    }

    # Check 3: frontmatter/tag namespace integrity
    $frontmatter = Get-Frontmatter -Lines $rawLines
    if ($null -eq $frontmatter) {
        continue
    }

    $fmDict = @{}
    $inTags = $false

    for ($j = 0; $j -lt $frontmatter.Lines.Count; $j++) {
        $fmLine = $frontmatter.Lines[$j]
        $trimmed = $fmLine.Trim()

        $keyMatch = $frontmatterKeyRegex.Match($trimmed)
        if ($keyMatch.Success) {
            $key = $keyMatch.Groups[1].Value
            $value = $keyMatch.Groups[2].Value.Trim()
            $fmDict[$key] = $value
            $inTags = ($key -eq "tags")
            continue
        }

        $tagMatch = $frontmatterTagRegex.Match($trimmed)
        if ($inTags -and $tagMatch.Success) {
            $tagValue = $tagMatch.Groups[1].Value.Trim().Trim('"')
            $lineNo = $j + $frontmatter.StartLine + 1

            if ($tagValue -match "^") {
                # intentional no-op
            }

            $namespaceMatch = $tagNamespaceRegex.Match($tagValue)
            if ($namespaceMatch.Success) {
                $ns = $namespaceMatch.Groups[1].Value
                if ($allowedTagNamespaces -notcontains $ns) {
                    Add-Issue -Severity "error" -Check "unknown-tag-namespace" -File $relativeFile -Line $lineNo -Message "Unknown tag namespace '$ns'. Allowed namespaces: $($allowedTagNamespaces -join ', ')."
                }
            } else {
                Add-Issue -Severity "warn" -Check "non-namespaced-tag" -File $relativeFile -Line $lineNo -Message "Tag is not namespaced (namespace:value)."
            }

            continue
        }

        if ($inTags -and $trimmed -notmatch "^-") {
            $inTags = $false
        }
    }

    # Check 4: required metadata sanity
    if ($fmDict.ContainsKey("type")) {
        $typeValue = $fmDict["type"].Trim('"')

        if ($typeValue -in @("epic", "feature", "story", "task", "knowledge-source", "knowledge-topic")) {
            if (-not $fmDict.ContainsKey("updated")) {
                Add-Issue -Severity "error" -Check "missing-updated" -File $relativeFile -Line $frontmatter.StartLine -Message "Frontmatter is missing required 'updated' field."
            }
        }

        if ($typeValue -in @("epic", "feature", "story", "task")) {
            if (-not $fmDict.ContainsKey("status")) {
                Add-Issue -Severity "error" -Check "missing-status" -File $relativeFile -Line $frontmatter.StartLine -Message "Backlog ticket is missing required 'status' field."
            }
            if (-not $fmDict.ContainsKey("progress")) {
                Add-Issue -Severity "error" -Check "missing-progress" -File $relativeFile -Line $frontmatter.StartLine -Message "Backlog ticket is missing required 'progress' field."
            }

            if ($fmDict.ContainsKey("status") -and $fmDict.ContainsKey("progress")) {
                $status = $fmDict["status"].Trim('"')
                $progressRaw = $fmDict["progress"].Trim('"')
                $progress = 0
                if ([int]::TryParse($progressRaw, [ref]$progress)) {
                    if ($status -eq "done" -and $progress -lt 100) {
                        Add-Issue -Severity "error" -Check "status-progress-mismatch" -File $relativeFile -Line $frontmatter.StartLine -Message "status is done but progress is less than 100."
                    }
                    if ($status -eq "backlog" -and $progress -gt 0) {
                        Add-Issue -Severity "warn" -Check "status-progress-mismatch" -File $relativeFile -Line $frontmatter.StartLine -Message "status is backlog but progress is greater than 0."
                    }
                }
            }
        }
    }
}

$errorCount  = @($issues | Where-Object { $_.Severity -eq "error" }).Count
$warnCount   = @($issues | Where-Object { $_.Severity -eq "warn" }).Count
$runTime     = Get-Date -Format "yyyy-MM-ddTHH:mm:ssK"
$resultLabel = if ($errorCount -gt 0) { "FAILED" } else { "PASSED" }

# --- Console output ---
Write-Host "Fractonomical consistency check"
Write-Host "Root: $rootPath"
Write-Host "Errors: $errorCount | Warnings: $warnCount"

if ($issues.Count -gt 0) {
    Write-Host ""
    $issues |
        Sort-Object Severity, File, Line |
        ForEach-Object {
            Write-Host ("[{0}] {1} | {2}:{3} | {4}" -f $_.Severity.ToUpper(), $_.Check, $_.File, $_.Line, $_.Message)
        }
}

# --- Markdown report ---
$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine("# Consistency Report")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("| Field | Value |")
[void]$sb.AppendLine("|-------|-------|")
[void]$sb.AppendLine("| **Run** | $runTime |")
[void]$sb.AppendLine("| **Root** | ``$rootPath`` |")
[void]$sb.AppendLine("| **Result** | $resultLabel |")
[void]$sb.AppendLine("| **Errors** | $errorCount |")
[void]$sb.AppendLine("| **Warnings** | $warnCount |")
[void]$sb.AppendLine("")

if ($issues.Count -gt 0) {
    [void]$sb.AppendLine("## Issues")
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("| Severity | Check | File | Line | Message |")
    [void]$sb.AppendLine("|----------|-------|------|------|---------|")
    $issues |
        Sort-Object Severity, File, Line |
        ForEach-Object {
            $row = "| {0} | ``{1}`` | ``{2}`` | {3} | {4} |" -f $_.Severity.ToUpper(), $_.Check, $_.File, $_.Line, $_.Message
            [void]$sb.AppendLine($row)
        }
    [void]$sb.AppendLine("")
} else {
    [void]$sb.AppendLine("## Issues")
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("No issues found.")
    [void]$sb.AppendLine("")
}

[void]$sb.AppendLine("---")
[void]$sb.AppendLine("*Generated by ``_coverage/fractonomical-consistency-check.ps1``*")

$sb.ToString() | Set-Content -Path $reportFile -Encoding UTF8
Write-Host ""
Write-Host "Report written to: $reportFile"

if ($errorCount -gt 0) {
    exit 1
}

exit 0

