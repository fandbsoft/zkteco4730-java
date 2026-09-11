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

## Secure/new firmware behavior

Some newer access-control terminals answer `CMD_CONNECT` with response `6001`
and reject later pull commands with `2032`. In that state the device is not
accepting the public legacy pull protocol directly. The library now reports this
as `ZkUnsupportedProtocolException` instead of returning cached/fake data.

Check the device Communication / PC Connection / Cloud Service settings:

- `TCP COMM Port` should be reachable, default `4370`.
- `Comm Key` must match the password passed to the library when legacy pull is
  enabled.
- Devices configured for AC Push, TA Push, BEST, ZKBioTime, ZKBioSecurity, or
  vendor-locked secure pull may require changing the device protocol mode or
  using the corresponding official server/SDK channel.
