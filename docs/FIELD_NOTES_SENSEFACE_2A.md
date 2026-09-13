# Field Notes: ZKTeco SenseFace 2A over TCP 4370

This document records field-tested behavior discovered while building and
testing this SDK against real ZKTeco SenseFace 2A devices.

## Tested Devices

| Device | IP | Firmware | Protocol | Result |
| --- | --- | --- | --- | --- |
| ZKTeco SenseFace 2A | `192.168.1.33` | `Ver 6.60 Jan 13 2025` | Secure pull, challenge `6001` | Device info, users, logs, range logs, streaming logs pass |
| ZKTeco SenseFace 2A | `192.168.1.28` | `Ver 6.60 Jan 13 2025` | Secure pull, challenge `6001` | Device info, users, logs, range logs, streaming logs pass |

## Secure Firmware Handshake

Newer SenseFace/Linux firmware can reject the old pull handshake and respond to
`CMD_CONNECT` with command code `6001`. The working flow is:

1. Send `CMD_CONNECT`.
2. Receive `CMD_ACK_CHALLENGE_6001`.
3. Exchange DMC payloads with RSA public keys.
4. Derive an AES-CBC session key from client/server secrets.
5. Authenticate Comm Key with `CMD_AUTH` or `CMD_AUTH_EXT`.
6. Send the `SDKBuild=1` option marker.
7. Use normal pull commands through the encrypted session.

The implementation is pure Java and does not call `zkemkeeper.dll`,
`ZKEMCrypto.dll`, JNI, or JNA.

## What Works

- Read device information: serial number, model, firmware, MAC, platform, user
  count, fingerprint count, face count, attendance log count.
- Read all users.
- Read all attendance logs.
- Filter attendance logs by epoch seconds or epoch milliseconds.
- Stream large attendance log payloads without holding raw transfer buffers in
  memory.
- Open the relay/door with `CMD_UNLOCK` / `ACUnlock` command `31`.
- Infer the device UTC offset from `CMD_GET_TIME` for log epoch conversion.

## Timezone / UTC Offset Limitation

SenseFace 2A secure firmware does not expose its UI timezone setting through raw
TCP `4370`.

The following were tested:

- Raw `CMD_OPTIONS_WRQ` with keys such as `timezone`, `TimeZone`,
  `Timezone`, `TimeZoneOffset`, `GMTOffset`, `UTCOffset`, `GMT`, and `UTC`.
- Official 32-bit `zkemkeeper.dll` version `6.3.1.55` through COM:
  `SetSysOption(1, "timezone", "330")`,
  `SetSysOption(1, "TimeZone", "330")`, and
  `SetSysOption(1, "Timezone", "330")`.
- `SetDeviceTime2(...)` through `zkemkeeper.dll`.

Both raw 4370 and `zkemkeeper.dll` can store and read back arbitrary
timezone-looking option values, but the SenseFace 2A device UI remains on its
real timezone setting. Therefore this SDK intentionally exposes only:

```java
String utcOffset = zk.getUTC(); // e.g. UTC+07:00
```

It does not expose a UTC/GMT offset setter. Returning success for such a setter
would be misleading on tested secure firmware.

## Attendance Timestamp Model

ZKTeco attendance records contain local wall-clock timestamps encoded with the
standard 32-bit ZK time formula. The device does not include a timezone ID in
each attendance record.

The SDK tags parsed logs with the currently inferred device UTC offset so these
methods can produce stable epoch values:

```java
log.getTimestampWithGmtOffset();
log.getTimestampEpochMilli();
log.getTimestampEpochSecond();
log.getUTC();
```

## Operational Notes

- After large buffer reads on secure firmware, reconnecting before small
  interactive commands avoids stale prepared-buffer responses.
- `createdAt` for public 4370 user records is best-effort. On tested SenseFace
  firmware the user record itself does not expose a dedicated creation timestamp;
  the SDK fills it from the first attendance timestamp seen for that user when
  logs are available.
- Some vendor docs use the word "timezone" for access-control time segments.
  That is not the same thing as the system UTC/GMT timezone offset.
