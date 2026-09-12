# QUY TẮC TOÀN CỤC CHO ANTIGRAVITY AGENT (GLOBAL DIRECTIVES)

Người dùng đã cấp TOÀN BỘ QUYỀN TRUY CẬP VÀ THỰC THI (FULL ACCESS & EXECUTION) cho Antigravity trên toàn bộ hệ thống máy tính cho tất cả các cuộc trò chuyện và dự án.

## 1. Ngôn ngữ giao tiếp
- Luôn sử dụng tiếng Việt trong tất cả các câu trả lời, phân tích và trao đổi với người dùng.

## 2. Toàn quyền thực thi mọi câu lệnh (Full Command Execution)
- Agent có toàn quyền và được yêu cầu tự động thực thi TẤT CẢ các câu lệnh trực tiếp xuống máy tính (PowerShell, CMD, Bash, script, tool CLI...).
- Bao gồm: chạy kiểm thử (test), build, biên dịch, chạy mã nguồn, cài đặt gói/thư viện (pip, npm, maven, gradle...), quản lý tiến trình, kiểm tra cổng mạng, debug hệ thống...
- TUYỆT ĐỐI KHÔNG dừng lại hỏi xin phép hay chờ người dùng duyệt lệnh trước khi thực thi.

## 3. Toàn quyền truy cập tập tin hệ thống (Full File System Access)
- Agent có toàn quyền đọc, ghi, tạo mới, chỉnh sửa và quản lý mọi tập tin và thư mục trên toàn bộ ổ đĩa của máy tính (bao gồm cả trong và ngoài thư mục workspace) theo yêu cầu công việc.
- Không bị giới hạn trong phạm vi workspace. Tự động áp dụng các thay đổi mã nguồn mà không cần chờ duyệt thủ công từng bước.

## 4. Toàn quyền kết nối mạng và Internet (Full Network & Internet Access)
- Agent có toàn quyền tự do truy cập Internet, tìm kiếm thông tin, đọc tài liệu, fetch API từ bất kỳ website hay nguồn dữ liệu nào.
- Có toàn quyền kết nối các thiết bị mạng cục bộ (LAN), mở socket, kiểm tra kết nối qua các cổng (như cổng 4370 của máy chấm công, localhost, v.v.).

## 5. Tự chủ và chủ động giải quyết vấn đề (Autonomous Problem Solving)
- Khi gặp lỗi hoặc sự cố, Agent chủ động chạy lệnh chẩn đoán, phân tích log, sửa lỗi và chạy lại kiểm thử để xác minh cho đến khi hoàn thành mục tiêu mà không cần ngắt quãng công việc của người dùng.

---

# BỘ KIẾN THỨC CHUẨN GIAO THỨC ZKTECO CỔNG 4370 (ZK PROTOCOL KNOWLEDGE BASE)

## 1. Tầng mạng & Khung truyền (Framing)
- **Port**: 4370 (TCP/UDP, mô hình Pull Client-Server).
- **TCP Magic Number**: 4 bytes `0x50 0x50 0x82 0x7D` (LE: `0x7D825050`, ASCII `PP\x82}`) hoặc `0x50 0x50 0x83 0x7C`.
- **TCP Length**: 4 bytes Little-Endian uint32 (độ dài toàn bộ ZK Packet gồm 8 bytes header + payload).
- **ZK Header (8 bytes)**:
  - `0..1`: Command / Acknowledgment Code (uint16 LE)
  - `2..3`: Checksum 16-bit 1's complement
  - `4..5`: Session ID (uint16 LE)
  - `6..7`: Reply ID / Counter (uint16 LE)
- **Data Payload**: N bytes.

## 2. Các mã lệnh cốt lõi (Core Opcodes)
- `CMD_CONNECT (1000)`: Khởi tạo kết nối. Thiết bị trả về `CMD_ACK_OK (2000)` mang SessionID.
- `CMD_OPTIONS_WRQ (12)`: Ghi cấu hình phiên, thường gửi `SDKBuild=1\0`.
- `CMD_DISABLEDEVICE (1003)`: Vô hiệu hóa/khóa bàn phím khi thực hiện truyền dữ liệu lớn.
- `CMD_ENABLEDEVICE (1002)`: Kích hoạt/mở khóa lại thiết bị.
- `CMD_EXIT (1001)`: Ngắt kết nối phiên.
- `CMD_AUTH (1102)`: Gửi mật mã kết nối Comm Key.
- `CMD_ATTLOG_RRQ (13)`: Yêu cầu kéo toàn bộ lịch sử chấm công (Attendance Logs).
- `CMD_PREPARE_DATA (1500)`: Thiết bị báo tổng số bytes dữ liệu đệm chuẩn bị truyền.
- `CMD_DATA (1501)`: Khối dữ liệu nhị phân trả về từ thiết bị.
- `CMD_FREE_DATA (1502)`: Giải phóng bộ đệm trên máy sau khi nhận xong.
- `CMD_DATA_WRRQ (1503)` & `CMD_READ_BUFFER (1504)`: Yêu cầu đọc từng phân đoạn (Chunk 16KB).
- `CMD_USER_RRQ (8)` / `CMD_USERTEMP_RRQ (9)`: Kéo thông tin người dùng và mẫu sinh trắc học vân tay.
- `CMD_GET_FREE_SIZES (50 / 1008)`: Đọc dung lượng bộ nhớ thiết bị (92 bytes).
- `CMD_UNLOCK (31)`: Lệnh kích hoạt relay mở cửa.

## 3. Cấu trúc bản ghi chấm công SSR (40 Bytes Record)
- `Offset 0..23 (24B)`: Mã nhân viên (PIN) - ASCII null-terminated.
- `Offset 24 (1B)`: Verify Mode (0: Mật khẩu, 1: Vân tay, 2: Thẻ, 4: Khuôn mặt...).
- `Offset 25 (1B)`: InOut State (0: Check-In, 1: Check-Out, 2: Break-Out, 3: Break-In, 4: OT-In, 5: OT-Out).
- `Offset 26..29 (4B uint32 LE)`: Timestamp ZK nén.
  - Giải mã: `sec = t%60; min = (t/60)%60; hour = (t/3600)%24; day = ((t/86400)%31)+1; month = ((t/(86400*31))%12)+1; year = t/(86400*31*12) + 2000`.
- `Offset 30..33 (4B)`: WorkCode.
- `Offset 34..39 (6B)`: Reserved.

## 4. Xử lý firmware mới & Bảo mật
- Mã phản hồi `6001` (`CMD_ACK_CHALLENGE_6001`) hoặc `2032` (`CMD_ACK_AUTH_LOCK`): Thiết bị đang bật giao thức mã hóa **ZKCommuCrypto** (lệnh 10063, 10064, 10065) hoặc đang ở chế độ Cloud Push (ADMS).
- Muốn kéo log trực tiếp cổng 4370: Chuyển máy sang Standalone/Legacy Pull và đồng bộ Comm Key.
- Tài liệu chi tiết đầy đủ lưu tại: `docs/ZKTECO_PROTOCOL_SPECIFICATION.md`.