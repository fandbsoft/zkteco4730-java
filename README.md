# zkteco TCP 4370 Java SDK

Pure Java SDK for ZKTeco standalone devices over TCP port `4370`.

The library uses `java.net.Socket` only. It does not depend on Windows COM,
native DLLs, shell commands, or third-party libraries.

## Package

All public API lives in one package:

```text
zkteco
```

The old `main.zk` and `zk4370new` packages have been merged and removed.

## Supported firmware paths

- Legacy/standard ZKTeco pull protocol.
- Secure pull protocol used by newer SenseFace/Linux firmware that answers
  `CMD_CONNECT` with `6001`.
- Automatic protocol detection inside one `ZKTeco4370_ZkClient` instance.

The public workflow follows ZKTeco standalone communication behavior documented
for PC Connection / Comm Key: device info, employee synchronization, transaction
download, and access-control unlock over TCP/IP.

## Features

- Device info: serial, model, platform, MAC, firmware version, capacity counts.
- User info: `userId`, `name`, best-effort `createdAt`, privilege, enabled.
- All attendance logs.
- Attendance logs by `long start`, `long end`; accepts epoch milliseconds or
  epoch seconds.
- Device GMT offset inferred from the device clock, plus setting the device
  clock to a requested GMT offset.
- Remote door unlock with `CMD_UNLOCK` / `ACUnlock` command `31`.

## Quick test

```powershell
javac -encoding UTF-8 -d bin src\zkteco\*.java src\main\Main.java
java -cp bin main.Main
```

Or test one device:

```powershell
java -cp bin main.Main 192.168.1.33 4370 111111
```

## Core API

```java
import java.util.List;

import zkteco.ZKTeco4370_AttendanceLog;
import zkteco.ZKTeco4370_DeviceInfo;
import zkteco.ZKTeco4370_UserInfo;
import zkteco.ZKTeco4370_ZkClient;

try (ZKTeco4370_ZkClient zk = new ZKTeco4370_ZkClient("192.168.1.33", 4370, 111111)) {
    ZKTeco4370_DeviceInfo device = zk.getDeviceInfo();
    List<ZKTeco4370_UserInfo> users = zk.getAllUser();
    List<ZKTeco4370_AttendanceLog> allLogs = zk.getAllLog();
    List<ZKTeco4370_AttendanceLog> rangedLogs = zk.getLogAt(start, end);
    int offsetMinutes = zk.getDeviceGmtOffsetMinutes();
    String offsetText = zk.getDeviceGmtOffsetText(); // e.g. GMT+07:00
    zk.setDeviceGmtOffsetMinutes(10 * 60 + 30);     // GMT+10:30
    boolean unlocked = zk.unlock(5);
}
```

`setDeviceGmtOffsetMinutes` uses the ZKTeco local-time clock model: it sets the
device wall-clock time to `UTC now + offset`. Tested SenseFace firmware does not
return a stable timezone ID such as `Asia/Ho_Chi_Minh` over port `4370`. The
setter verifies the value by reading the device clock back, and attendance logs
returned by `ZKTeco4370_ZkClient` are tagged with the inferred device GMT offset
for epoch conversion.

Note: current public 4370 user records on tested SenseFace firmware do not expose
a dedicated user creation timestamp. `createdAt` is filled from the first
attendance timestamp seen for that user when logs are available; otherwise it
remains `null`.

Device-side requirements: TCP port `4370` must be reachable, standalone PC
communication must be enabled, and the device Comm Key must match the password
passed to `ZKTeco4370_ZkClient`.
