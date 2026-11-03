param([string[]]$Names=@('clang','libllvm'),[string]$ResolveAddress='')
$ErrorActionPreference='Stop'
$taskRepository=(Get-Location).Path
$taskPhase=Join-Path $taskRepository 'verification/cpp-semantic-probe-2026-10-27'
$taskMetadata=Get-Content (Join-Path $taskPhase 'package-candidates.json') -Raw | ConvertFrom-Json
$taskResolve=@()
if($ResolveAddress){
    [Net.IPAddress]::Parse($ResolveAddress) | Out-Null
    $taskHost=([Uri]$taskMetadata.repository).Host
    $taskResolve=@('--resolve',"${taskHost}:443:${ResolveAddress}")
}
$taskDirectory=Join-Path $taskRepository '.tools/cpp-semantic-probe/debs'
New-Item -ItemType Directory -Path $taskDirectory -Force | Out-Null
foreach($taskName in $Names){
    $taskItem=$taskMetadata.packages.$taskName
    if($null -eq $taskItem){throw "No frozen package metadata: $taskName"}
    if($taskItem.Filename.StartsWith('/') -or $taskItem.Filename.Contains('..')){throw 'Unsafe package path'}
    $taskTarget=Join-Path $taskDirectory (Split-Path $taskItem.Filename -Leaf)
    if(-not (Test-Path -LiteralPath $taskTarget)){
        $taskPartial="$taskTarget.partial"
        & curl.exe @taskResolve --location --fail --continue-at - --connect-timeout 15 --max-time 120 --retry 2 --retry-all-errors --speed-limit 1024 --speed-time 20 --silent --show-error --dump-header "$taskTarget.headers.txt" --output $taskPartial "$($taskMetadata.repository)/$($taskItem.Filename)"
        if($LASTEXITCODE -ne 0){throw "Package download incomplete (partial preserved): $taskName"}
        if((Get-Item -LiteralPath $taskPartial).Length -ne [long]$taskItem.Size -or
            (Get-FileHash -LiteralPath $taskPartial -Algorithm SHA256).Hash.ToLower() -ne $taskItem.SHA256){throw "Package integrity mismatch: $taskName"}
        Move-Item -LiteralPath $taskPartial -Destination $taskTarget
    }
    if((Get-Item -LiteralPath $taskTarget).Length -ne [long]$taskItem.Size -or
        (Get-FileHash -LiteralPath $taskTarget -Algorithm SHA256).Hash.ToLower() -ne $taskItem.SHA256){throw "Cached package integrity mismatch: $taskName"}
    Write-Output "$taskName $($taskItem.Version) $($taskItem.Size) bytes SHA256 verified; not installed"
}
