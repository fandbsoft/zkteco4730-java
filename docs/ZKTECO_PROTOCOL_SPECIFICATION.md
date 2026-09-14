# ĐẶC TẢ KỸ THUẬT GIAO THỨC ZKTECO CỔNG 4370 (PULL PROTOCOL SPECIFICATION)

Tài liệu lưu trữ chuẩn mực toàn diện về giao thức truyền thông nhị phân độc quyền của ZKTeco qua cổng 4370 (TCP/UDP), phục vụ việc tích hợp, trích xuất dữ liệu chấm công, quản lý người dùng và cấu hình thiết bị.

---

## 1. KIẾN TRÚC MẠNG & TẦNG TRUYỀN DẪN (TRANSPORT LAYER)

* **Cổng mặc định**: `4370` (Hỗ trợ cả TCP và UDP).
* **Mô hình kết nối**: **Client-Server (Pull Architecture)**.
  * *Máy chấm công (Terminal)*: Đóng vai trò TCP Server lắng nghe tại cổng `4370`.
  * *Phần mềm / Máy tính (Host/SDK)*: Đóng vai trò TCP Client chủ động mở socket kết nối tới máy chấm công để gửi lệnh và lấy dữ liệu.
* **Timeout chuẩn**:
  * Connect Timeout: `10.000 ms` (10 giây).
  * Read/Response Timeout: `30.000 ms` (30 giây).

---

## 2. CẤU TRÚC ĐÓNG GÓI TIN (PACKET FRAMING)

Mỗi thông điệp truyền qua TCP gồm 2 tầng header: **TCP Framing Header** và **ZK Protocol Header**.

```text
+------------------------------------+------------------------------------+--------------------+
|        TCP Framing Header          |         ZK Protocol Header         |    Payload Data    |
|             (8 Bytes)              |             (8 Bytes)              |     (N Bytes)      |
+------------------+-----------------+---------+----------+-------+-------+--------------------+
| Magic Number     | Length          | Command | Checksum | SessID| RepID | Data bytes         |
| 4 Bytes          | 4 Bytes         | 2 Bytes | 2 Bytes  | 2 B   | 2 B   | N Bytes            |
+------------------+-----------------+---------+----------+-------+-------+--------------------+
```

### 2.1. TCP Framing Header (8 Bytes)
* **Magic Number (4 Bytes)**:
  * Chuẩn phổ biến: `0x50 0x50 0x82 0x7D` (Ký hiệu ASCII: `PP\x82}`).
    * Dưới dạng Little-Endian 32-bit: `0x7D825050`.
  * Dòng thay thế: `0x50 0x50 0x83 0x7C` (Little-Endian: `0x7C835050`).
* **Packet Length (4 Bytes)**:
  * Số nguyên không dấu 32-bit (Little-Endian uint32).
  * Kích thước của toàn bộ phần ZK Packet (ZK Header 8 bytes + Data Payload N bytes).

### 2.2. ZK Protocol Header (8 Bytes)
| Byte Offset | Độ dài | Tên trường | Kiểu dữ liệu | Ý nghĩa |
| :--- | :--- | :--- | :--- | :--- |
| `0 - 1` | 2 Bytes | `Command / Code` | uint16 (LE) | Mã lệnh yêu cầu hoặc mã trạng thái phản hồi. |
| `2 - 3` | 2 Bytes | `Checksum` | uint16 (LE) | Giá trị kiểm tra lỗi toàn vẹn gói tin (Thuật toán bù 1 16-bit). |
| `4 - 5` | 2 Bytes | `Session ID` | uint16 (LE) | Mã phiên kết nối (Thiết bị sinh ra sau lệnh `CMD_CONNECT`). |
| `6 - 7` | 2 Bytes | `Reply ID / Counter`| uint16 (LE) | Số thứ tự gói tin (bắt đầu từ 0 và tăng dần theo từng phiên trao đổi). |

### 2.3. Thuật toán tính Checksum 16-bit
Thuật toán tính Checksum được thực hiện trên ZK Header (với trường `Checksum = 0`) nối với phần Payload:
```java
public static int calculateChecksum(byte[] buffer, int offset, int length) {
    int sum = 0;
    int i = offset;
    while (length > 1) {
        sum += ((buffer[i + 1] & 0xFF) << 8) | (buffer[i] & 0xFF);
        i += 2;
        length -= 2;
        if ((sum & 0x80000000) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
    }
    if (length > 0) {
        sum += (buffer[i] & 0xFF);
    }
    while ((sum >> 16) != 0) {
        sum = (sum & 0xFFFF) + (sum >> 16);
    }
    return (~sum) & 0xFFFF;
}
```

---

## 3. BẢNG DANH MỤC MÃ LỆNH CHUẨN (COMMAND CODES)

### 3.1. Lệnh quản lý phiên & thiết bị
* `CMD_CONNECT = 1000` (`0x03E8`): Khởi tạo phiên kết nối. SessionID gửi đi là 0.
* `CMD_EXIT = 1001` (`0x03E9`): Đóng phiên làm việc.
* `CMD_ENABLEDEVICE = 1002` (`0x03EA`): Kích hoạt lại thiết bị, mở khóa bàn phím và mắt đọc.
* `CMD_DISABLEDEVICE = 1003` (`0x03EB`): Vô hiệu hóa thiết bị (khóa bàn phím/màn hình để đồng bộ dữ liệu an toàn).
* `CMD_RESTART = 1004` (`0x03EC`): Khởi động lại thiết bị.
* `CMD_POWEROFF = 1005` (`0x03ED`): Tắt nguồn thiết bị.
* `CMD_SLEEP = 1006` (`0x03EE`): Chuyển thiết bị sang chế độ ngủ.
* `CMD_RESUME = 1007` (`0x03EF`): Đánh thức thiết bị.
* `CMD_TESTVOICE = 1017` (`0x03F9`): Phát âm thanh kiểm tra loa.
* `CMD_GET_TIME = 201` (`0x00C9`): Đọc thời gian local hiện tại của thiết bị.
* `CMD_SET_TIME = 202` (`0x00CA`): Ghi thời gian local hiện tại của thiết bị.
* `CMD_VERSION = 1100` (`0x044C`): Đọc phiên bản firmware.
* `CMD_AUTH = 1102` (`0x044E`): Gửi mật khẩu xác thực (Comm Key).
* `CMD_OPTIONS_RRQ = 11` (`0x000B`): Đọc tham số cấu hình hệ thống (Vendor, SerialNumber, Platform...).
* `CMD_OPTIONS_WRQ = 12` (`0x000C`): Ghi tham số cấu hình (ví dụ: `SDKBuild=1`).
* `CMD_GET_FREE_SIZES = 50 / 1008`: Lấy thống kê bộ nhớ và dung lượng lưu trữ thiết bị.
* `CMD_UNLOCK = 31` (`0x001F`): Mở khóa cửa (kích hoạt relay mở cửa).

### 3.2. Lệnh đọc & ghi dữ liệu (Data Operations)
* `CMD_ATTLOG_RRQ = 13` (`0x000D`): **Yêu cầu tải toàn bộ bản ghi chấm công (Attendance Logs)**.
* `CMD_ATTLOG_TIME_RRQ = 10004` (`0x2714`): **Yêu cầu tải bản ghi chấm công theo khoảng thời gian** (tương ứng hàm `ReadTimeGLogData` trong SDK chính hãng `zkemkeeper.dll`).
* `CMD_CLEAR_DATA = 14` (`0x000E`): Xóa toàn bộ dữ liệu trên máy.
* `CMD_CLEAR_ATTLOG = 15` (`0x000F`): Xóa toàn bộ nhật ký chấm công.
* `CMD_USER_RRQ = 8` (`0x0008`): Đọc danh sách người dùng.
* `CMD_USERTEMP_RRQ = 9` (`0x0009`): Đọc dữ liệu mẫu sinh trắc học (vân tay).
* `CMD_DELETE_USER = 18` (`0x0012`): Xóa người dùng theo ID.
* `CMD_DELETE_USERTEMP = 19` (`0x0013`): Xóa mẫu vân tay.

### 3.3. Lệnh truyền khối dữ liệu lớn (Big Data Exchange)
* `CMD_PREPARE_DATA = 1500` (`0x05DC`): Thiết bị báo tổng dung lượng dữ liệu nhị phân sắp truyền.
* `CMD_DATA = 1501` (`0x05DD`): Khối dữ liệu nhị phân gửi từ thiết bị.
* `CMD_FREE_DATA = 1502` (`0x05DE`): Yêu cầu thiết bị giải phóng bộ đệm truyền dữ liệu.
* `CMD_DATA_WRRQ = 1503` (`0x05DF`): Chuẩn bị bộ đệm đọc (Buffer Read Request).
* `CMD_READ_BUFFER = 1504` (`0x05E0`): Yêu cầu đọc từng phân đoạn bộ đệm (Chunk size chuẩn: 16.384 bytes).

### 3.4. Mã phản hồi & Trạng thái (Acknowledgment Codes)
* `CMD_ACK_OK = 2000` (`0x07D0`): Lệnh thực thi thành công.
* `CMD_ACK_ERROR = 2001` (`0x07D1`): Lệnh thất bại hoặc có lỗi.
* `CMD_ACK_DATA = 2002` (`0x07D2`): Phản hồi mang dữ liệu trực tiếp.
* `CMD_ACK_RETRY = 2003` (`0x07D3`): Thiết bị bận, yêu cầu thử lại.
* `CMD_ACK_REPEAT = 2004` (`0x07D4`): Yêu cầu gửi lại gói tin.
* `CMD_ACK_UNAUTH = 2005` (`0x07D5`): Không có quyền truy cập (thiếu hoặc sai Comm Key).
* `CMD_ACK_AUTH_LOCK = 2032` (`0x07F0`): Khóa bảo vệ phiên bảo mật trên firmware mới.
* `CMD_ACK_CHALLENGE_6001 = 6001` (`0x1771`): Thử thách xác thực bảo mật firmware mới (ZKCommuCrypto).

---

## 4. QUY TRÌNH KÉO DỮ LIỆU CHẤM CÔNG (ATTLOG RETRIEVAL WORKFLOW)

### 4.1. Quy trình kéo toàn bộ dữ liệu chấm công (`CMD_ATTLOG_RRQ = 13`)

```text
Host (Client)                                          ZKTeco Terminal (Port 4370)
    |                                                              |
    |---- 1. CMD_CONNECT (1000) [Session=0, Reply=0] ------------->|
    |<--- 2. CMD_ACK_OK (2000) [Session=S, Reply=0] ---------------|
    |                                                              |
    |---- 3. CMD_OPTIONS_WRQ (12) Data: "SDKBuild=1\0" ----------->|
    |<--- 4. CMD_ACK_OK (2000) ------------------------------------|
    |                                                              |
    |---- 5. CMD_DISABLEDEVICE (1003) ---------------------------->|
    |<--- 6. CMD_ACK_OK (2000) ------------------------------------|
    |                                                              |
    |---- 7. CMD_ATTLOG_RRQ (13) --------------------------------->|
    |                                                              |
    |    [Trường hợp dữ liệu lớn: Chunked Buffer Transfer]        |
    |<--- 8. CMD_PREPARE_DATA (1500) [TotalBytes=Size] ------------|
    |                                                              |
    |--+ (Lặp đọc từng Chunk 16KB)                                 |
    |  |-- 9. CMD_READ_BUFFER (1504) [Offset, Length] ------------>|
    |  |<-- 10. CMD_DATA (1501) [Binary Chunk Data] ---------------|
    |--+                                                           |
    |                                                              |
    |---- 11. CMD_FREE_DATA (1502) ------------------------------->|
    |<--- 12. CMD_ACK_OK (2000) -----------------------------------|
    |                                                              |
    |---- 13. CMD_ENABLEDEVICE (1002) ---------------------------->|
    |<--- 14. CMD_ACK_OK (2000) -----------------------------------|
    |                                                              |
    |---- 15. CMD_EXIT (1001) ------------------------------------>|
    |<--- 16. CMD_ACK_OK (2000) -----------------------------------|
    |                                                              |
```

### 4.2. Quy trình kéo log theo khoảng thời gian chuẩn zkemkeeper (`ReadTimeGLogData` - Opcode `10004`)

Được dịch ngược trực tiếp từ hàm nội bộ `Z_ReadTimeLog` trong `zkemkeeper.dll` / `zkemsdk.dll`. Thiết bị thực hiện lọc trên bộ nhớ phần cứng (hardware filtering) và chỉ trả về các log phát sinh trong khoảng `[startTime, endTime]`, giảm tải băng thông và độ trễ tới 90%:

* **Cấu trúc gói tin yêu cầu 11 Bytes**:
  - Gửi qua `CMD_DATA_WRRQ = 1503` (hoặc lệnh trực tiếp `CMD_ATTLOG_TIME_RRQ = 10004`):
  ```text
  Offset 0    : (byte)  0x01      - Flag chế độ (1)
  Offset 1..2 : (short) 10004     - Opcode Little-Endian (0x14 0x27)
  Offset 3..6 : (int)   startTime - uint32 LE, mã hóa theo công thức ZK Time
  Offset 7..10: (int)   endTime   - uint32 LE, mã hóa theo công thức ZK Time
  ```

* **Luồng dữ liệu phản hồi**:
  1. Máy chấm công nhận diện opcode `10004` và quét chỉ mục các bản ghi trong khoảng thời gian.
  2. Máy trả về `CMD_PREPARE_DATA (1500)` mang tổng số byte tương ứng với lượng log tìm thấy.
  3. Client kéo các chunk 16KB qua `CMD_READ_BUFFER (1504)` và phân tích các bản ghi 40-byte SSR.
  4. Client gửi `CMD_FREE_DATA (1502)` để giải phóng buffer trên máy.
  5. *Cơ chế Fallback*: Nếu firmware quá cũ không hỗ trợ opcode 10004 (trả về `CMD_ACK_ERROR`), client tự động chuyển sang kéo toàn bộ (`CMD_ATTLOG_RRQ = 13`) và lọc trên bộ nhớ RAM.

---

## 5. ĐỊNH DẠNG CẤU TRÚC BẢN GHI (DATA STRUCTURES)

### 5.1. Bản ghi chấm công chuẩn SSR (40 Bytes/Bản ghi)
Áp dụng cho hầu hết các thiết bị màn hình màu TFT, iFace và Standalone hiện hành:

| Offset | Độ dài | Tên trường | Kiểu dữ liệu | Mô tả |
| :---: | :---: | :--- | :--- | :--- |
| `0 - 23` | 24 Bytes | `User PIN` | ASCII String | Mã nhân viên/User ID (kết thúc bằng ký tự `\0` hoặc pad null). |
| `24` | 1 Byte | `Verify Mode` | uint8 | Cách xác thực: 0=PW, 1=Vân tay, 2=Thẻ từ, 4=Khuôn mặt... |
| `25` | 1 Byte | `InOut State` | uint8 | Trạng thái: 0=Check-In, 1=Check-Out, 2=Break-Out, 3=Break-In, 4=OT-In, 5=OT-Out. |
| `26 - 29` | 4 Bytes | `Timestamp` | uint32 (LE) | Thời gian chấm công (Mã hóa theo thuật toán ZK Time). |
| `30 - 33` | 4 Bytes | `WorkCode` | uint32 (LE) | Mã công việc (nếu có kích hoạt). |
| `34 - 39` | 6 Bytes | `Reserved` | Byte array | Vùng dữ liệu dự phòng. |

### 5.2. Thuật toán mã hóa & giải mã thời gian ZKTeco Time (4 Bytes)
Thời gian chấm công được nén thành số nguyên 32-bit theo công thức:
`Time = (((((Year - 2000) * 12 + Month - 1) * 31 + Day - 1) * 24 + Hour) * 60 + Minute) * 60 + Second`

**Giải mã ngược trong Java**:
```java
public static LocalDateTime decodeZkTime(long rawTime) {
    long sec = rawTime % 60;
    rawTime /= 60;
    long min = rawTime % 60;
    rawTime /= 60;
    long hour = rawTime % 24;
    rawTime /= 24;
    long day = (rawTime % 31) + 1;
    rawTime /= 31;
    long month = (rawTime % 12) + 1;
    rawTime /= 12;
    long year = rawTime + 2000;
    return LocalDateTime.of((int)year, (int)month, (int)day, (int)hour, (int)min, (int)sec);
}
```

### 5.3. Cấu trúc thống kê dung lượng (`CMD_GET_FREE_SIZES` - 92 Bytes)
Dữ liệu phản hồi gồm các số nguyên 32-bit Little-Endian tại các vị trí offset:
* `Offset 16`: Tổng số người dùng đã đăng ký (`User Count`).
* `Offset 24`: Tổng số mẫu vân tay (`FP Count`).
* `Offset 32`: Tổng số bản ghi chấm công đang lưu trữ (`AttLog Count`).
* `Offset 40`: Tổng số bản ghi nhật ký vận hành (`OpLog Count`).
* `Offset 48`: Tổng số quản trị viên (`Admin Count`).
* `Offset 52`: Tổng số mật khẩu người dùng (`Password Count`).
* `Offset 56`: Dung lượng tối đa vân tay (`FP Capacity`).
* `Offset 60`: Dung lượng tối đa người dùng (`User Capacity`).
* `Offset 64`: Dung lượng tối đa bản ghi chấm công (`AttLog Capacity`).
* `Offset 80`: Số lượng khuôn mặt (`Face Count`).
* `Offset 88`: Dung lượng tối đa khuôn mặt (`Face Capacity`).

---

## 6. ĐẶC ĐIỂM CÁC DÒNG FIRMWARE MỚI & BẢO MẬT

1. **Phản hồi mã `6001` (`CMD_ACK_CHALLENGE_6001`)**:
   * Thiết bị đời mới yêu cầu cơ chế bắt tay mã hóa **ZKCommuCrypto**. Trên SenseFace 2A firmware `Ver 6.60 Jan 13 2025`, luồng đã kiểm chứng dùng DMC payload, RSA public-key exchange, sau đó dẫn xuất khóa phiên AES-CBC:
     * `CMD_CRYPTO_DMC_EXCHANGE = 10063` (0x274F)
     * `CMD_CRYPTO_KEY_EXCHANGE = 10064` (0x2750)
     * `CMD_CRYPTO_CONFIRM_SESSION = 10065` (0x2751)
2. **Phản hồi mã `2032` (`CMD_ACK_AUTH_LOCK`)**:
   * Phiên bị khóa do sai Comm Key hoặc thiết bị đang khóa cổng pull trực tiếp.
3. **Chế độ Cloud Push (ADMS) vs Legacy Pull**:
   * Nhiều thiết bị khi bật chế độ đồng bộ Cloud (ZKBioTime, ZKBioSecurity, ADMS, AC Push) sẽ tự động vô hiệu hóa cổng kết nối kéo trực tiếp 4370.
   * Để sử dụng giao thức 4370: Vào menu thiết bị -> *Thiết lập kết nối (Comm.)* -> *Kết nối PC (PC Connection)* -> Bật chế độ Standalone / Legacy, kiểm tra cổng `4370` và đặt đúng `Comm Key`.

4. **Timezone hệ thống không được expose qua raw 4370 trên SenseFace 2A**:
   * `CMD_GET_TIME` trả thời gian local của thiết bị; bản ghi chấm công cũng lưu local wall-clock time.
   * `CMD_OPTIONS_WRQ` có thể ghi/đọc lại các key như `timezone`, `TimeZone`, `GMTOffset`, `UTCOffset`, nhưng các key này không đổi timezone UI thật trên SenseFace 2A secure firmware đã test.
   * `zkemkeeper.dll` 32-bit bản `6.3.1.55` cũng cho kết quả tương tự với `SetSysOption(...)`: ghi/readback thành công nhưng UI timezone vẫn không đổi.
   * Vì vậy SDK chỉ suy ra UTC offset bằng `CMD_GET_TIME` để chuyển đổi log; không expose hàm set UTC/GMT offset.
