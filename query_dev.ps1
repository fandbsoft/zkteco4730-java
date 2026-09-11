$zk = New-Object -ComObject zkemkeeper.ZKEM.1
[void]$zk.SetCommPassword(111111)
$res = $zk.Connect_Net("192.168.1.33", 4370)
Write-Host "Connect: $res"
if ($res) {
    $sn = ""
    [void]$zk.GetSerialNumber(104, [ref]$sn)
    $fw = ""
    [void]$zk.GetFirmwareVersion(104, [ref]$fw)
    $plat = ""
    [void]$zk.GetPlatform(104, [ref]$plat)
    $mac = ""
    [void]$zk.GetDeviceMAC(104, [ref]$mac)
    $userCount = 0
    [void]$zk.GetDeviceStatus(104, 1, [ref]$userCount)
    $fpCount = 0
    [void]$zk.GetDeviceStatus(104, 2, [ref]$fpCount)
    $logCount = 0
    [void]$zk.GetDeviceStatus(104, 6, [ref]$logCount)
    $faceCount = 0
    [void]$zk.GetDeviceStatus(104, 21, [ref]$faceCount)

    Write-Host "SN: $sn"
    Write-Host "Firmware: $fw"
    Write-Host "Platform: $plat"
    Write-Host "MAC: $mac"
    Write-Host "UserCount: $userCount"
    Write-Host "FPCount: $fpCount"
    Write-Host "FaceCount: $faceCount"
    Write-Host "LogCount: $logCount"

    [void]$zk.ReadAllUserID(104)
    $enrollNo = ""
    $name = ""
    $userPassword = ""
    $priv = 0
    $enabled = $false
    while ($zk.SSR_GetAllUserInfo(104, [ref]$enrollNo, [ref]$name, [ref]$userPassword, [ref]$priv, [ref]$enabled)) {
        Write-Host "USER: id=$enrollNo, name=$name, priv=$priv, enabled=$enabled"
    }

    [void]$zk.Disconnect()
}
