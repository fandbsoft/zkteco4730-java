# SDK binary comparison — 2026-09-14

This is an incremental interoperability audit, not a reconstruction of all vendor
source code or certification for every firmware. The Java runtime does not load
these DLLs. SDK binaries were obtained from the CNCU mirror of ZKAccess 3.4.3.23,
NewSDK directory; the COM component reports SDK version 6.3.1.55. The mirror's
folder/version label does not establish binary authenticity.

SHA-256:

| Binary | SHA-256 |
|---|---|
| zkemkeeper.dll | 8B7E5BD670C24D367DFE0297E9C2FEB67972D062FB50B830F15873C65C6B34D9 |
| zkemsdk.dll | 8F06C314566654DB0F81C3241EE9923E488FD0E8E1C4D4A9298F8709D60AF4C5 |
| commpro.dll | E699E5DAE3A16FAC1F79BBF9EBC665E79718EA7040EE496FFC76E150652FBB1B |

## Observed binary behavior

- `zkemsdk!Z_ReadTimeLog`, RVA 0x35270: command 10004 and two packed device
  timestamps. Imported `commpro` ordinal 6 dispatches this to buffered reading.
- `commpro!ZEMBPRO_READDATA`, RVA 0x81a0, and `ZEMBPRO_READDATA2`, RVA 0x7de0:
  request command 1503 contains byte 1, a little-endian 16-bit command, and up to
  eight argument bytes; request size is 11 bytes.
- `zkemsdk!Z_DownloadUserPhoto`, RVA 0x35b20: command 10010, ASCII filename
  including its trailing NUL, 1 MiB output buffer. Unlike attendance reads, this
  uses the direct `ZEMBPRO_READDATA1` transfer.
- `commpro` direct transfer helper RVA 0x7370: PREPARE_DATA carries total length
  and chunk size; DATA packets use their session field as chunk index. This is
  why normal session-ID equality must not be applied blindly to photo chunks.

## Changes in this audit

- Validate received checksums and parser bounds; use a long checksum accumulator
  to prevent overflow for large packets.
- Send CMD_EXIT before marking a connected client closed. Close a partially
  opened connection when connect fails; reject zero/negative socket timeouts.
- Consume DATA after a buffered PREPARE_DATA marker instead of resending the
  same READ_BUFFER request. Reject unexpected responses and oversized chunks.
- Limit range fallback to unsupported command responses 65535/65533 and buffer
  allocation failure 4989; reconnect before checking the full log. Other errors
  propagate rather than being silently retried as full downloads.

## Validation

`mvn -q package` succeeds. Standalone protocol tests are run explicitly (they are
not automatically discovered by Maven):

```powershell
javac -cp target/classes -d target/protocol-tests tests/PhotoDownloadTest.java tests/TransportValidationTest.java
java -cp 'target/classes;target/protocol-tests' zkteco.PhotoDownloadTest
java -cp 'target/classes;target/protocol-tests' zkteco.TransportValidationTest
```

Tests cover photo transfer/invalid IDs, independent large-packet checksum sum,
corruption rejection, parser bounds, buffered PREPARE/DATA sequence, explicit
device errors, oversized chunks, and the actual EXIT packet on the socket.

Read-only hardware checks: SenseFace 2A, firmware `Ver 6.60 Jan 13 2025`, secure
TCP 4370 on .28 and .33. Five time windows per device match the saved COM SDK
reference by record count and SHA-256 over canonical log records. Current full
counts are 14 and 97; the .33 reference's last timestamp excludes newer records.
The downloaded .33 JPG is 24,639 bytes and still matches the SDK SHA-256:
`01b5d2d7783afb3ab9b5a8511498d1994b4a3b2a1595f366d9581adaff2c5319`.

No door command, user mutation, deletion, clock change, or cloud configuration
was used for these checks. Legacy hardware, all SDK APIs, Visible Light-specific
photo APIs, reconnect during interrupted transfers, and other firmware versions
remain outside this validation. Packet session/reply validation across all
transfer variants still needs a separate compatibility audit.
