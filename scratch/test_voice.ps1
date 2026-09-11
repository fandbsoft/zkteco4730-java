$zk = New-Object -ComObject zkemkeeper.ZKEM.1
[void]$zk.SetCommPassword(111111)
if ($zk.Connect_Net("192.168.1.33", 4370)) {
    Write-Host "Connected! Calling ONLY ACUnlock(104, 5) without PlayVoice..."
    $res = $zk.ACUnlock(104, 5)
    Write-Host "ACUnlock result: $res"
    Start-Sleep -Seconds 2
    [void]$zk.Disconnect()
    Write-Host "Disconnected."
} else {
    Write-Host "Connect failed"
}
