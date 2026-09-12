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
- Attendance logs through both direct `1501` and prepared-buffer `1503/1504`

Quick test:

```powershell
javac -encoding UTF-8 -d bin src\zk4370new\*.java
java -cp bin zk4370new.ZkNewMain 192.168.1.33 4370 111111
```

Device-side requirements remain the same: TCP port `4370` must be reachable,
standalone PC communication must be enabled, and the device Comm Key must match
the password passed to the client.
