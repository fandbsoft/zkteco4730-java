# zkteco4370-java

Pure Java 21 SDK for communicating with ZKTeco-compatible biometric devices over
the TCP 4370 pull protocol.

The library uses `java.net.Socket` only. It does not depend on Windows COM,
native DLLs, shell commands, or OS-specific helpers.

## Supported mode

- Legacy/standard ZKTeco pull protocol on TCP port `4370`.
- Attendance log retrieval.
- Device option and size queries.
- User list retrieval for supported legacy layouts.
- Remote door unlock when the device accepts `CMD_UNLOCK`.

## New firmware / SenseFace secure 4370

The `zk4370new` package implements the newer secure pull flow used by SenseFace
firmware that answers `CMD_CONNECT` with `6001`. It keeps the legacy
`src/main/zk` package untouched.

Verified against ZKTeco SenseFace 2A firmware `Ver 6.60 Jan 13 2025`:

- RSA/DMC handshake: `10063 -> 10064 -> 10065`
- AES-256-CBC secure tunnel over `50 50 83 7C`
- Comm Key auth: `1102 -> 2001`, then `1106 -> 2000`
- Device info: serial, model, platform, MAC, firmware version, capacity counts
- User info: user ID, name, privilege, enabled status, best-effort created time
- Remote door unlock with `CMD_UNLOCK` / `ACUnlock` command `31`
- Attendance logs through both direct `1501` and prepared-buffer `1503/1504`
- Attendance range filter through `getLogAt(long start, long end)`; accepts epoch
  milliseconds or epoch seconds.

Quick test:

```powershell
javac -encoding UTF-8 -d bin src\zk4370new\*.java
java -cp bin zk4370new.ZkNewMain 192.168.1.33 4370 111111
```

Core API:

```java
try (ZkNewSocketClient client = new ZkNewSocketClient("192.168.1.33", 4370, 111111)) {
    client.connect();
    ZkNewDeviceInfo device = client.getDeviceInfo();
    List<ZkNewUserInfo> users = client.getAllUserInfo();
    List<ZkNewAttendanceLog> allLogs = client.getAllLog();
    List<ZkNewAttendanceLog> rangedLogs = client.getLogAt(start, end);
    boolean unlocked = client.unlock(5);
}

try (ZkNewUnlock unlocker = new ZkNewUnlock("192.168.1.33", 4370, 111111)) {
    boolean unlocked = unlocker.unlock(5);
}
```

Note: the public 4370 user record exposed by the current SenseFace firmware and
the official COM SDK does not include a dedicated "created at" field. The
`createdAt` value is therefore filled from the first attendance timestamp seen
for that user when logs are available; otherwise it remains `null`.

Device-side requirements remain the same: TCP port `4370` must be reachable,
standalone PC communication must be enabled, and the device Comm Key must match
the password passed to the client.
