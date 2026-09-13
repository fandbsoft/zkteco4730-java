# ZKTeco TCP 4370 Pure Java SDK

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java: 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Zero Dependencies](https://img.shields.io/badge/Dependencies-0-brightgreen.svg)]()
[![Platform: Cross-Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS%20%7C%20Docker%20%7C%20Android-orange.svg)]()

A modern, robust, **100% Pure Java SDK** for ZKTeco and Ronald Jack biometric attendance and access-control terminals communicating over **TCP port 4370**.

---

## 🌟 Điểm nổi bật / Key Highlights

- **100% Pure Java (Raw TCP Socket)**: Sử dụng thuần `java.net.Socket`. Hoàn toàn **KHÔNG phụ thuộc** vào Windows COM (`zkemkeeper.dll`), native JNI/JNA, hay bất kỳ thư viện thứ 3 nào.
- **True Cross-Platform**: Chạy mượt mà trên **Windows, Linux (Ubuntu, Debian, CentOS), macOS, Docker containers, Android, Raspberry Pi**.
- **Tự động nhận diện 2 thế hệ firmware (Auto-detect dual protocol)**:
  1. **Legacy Pull Protocol**: Các dòng máy truyền thống (Ronald Jack DG 600BID, K40, F18, v.v.).
  2. **Secure Visible Light Protocol**: Các dòng máy Linux/Visible Light mới (SenseFace 2A, SpeedFace, MB10-VL...) trả về mã thử thách `6001` (`CMD_ACK_CHALLENGE_6001`), tự động thực hiện bắt tay mã hóa DMC / RSA 2048 / AES-CBC thuần Java.
- **API tinh gọn & đầy đủ (All-in-One)**:
  - 🖥️ **Device Info**: Serial number, model, firmware, MAC, platform, số lượng User, FP, Face, Log.
  - 👤 **User Info**: Danh sách nhân viên (`userId`, `name`, `createdAt`, `privilege`, `enabled`).
  - 🕒 **Attendance Logs**: Tải toàn bộ log hoặc lọc theo mốc thời gian `start` $\rightarrow$ `end` (chấp nhận cả Epoch Millis và Epoch Seconds).
  - 🚪 **Door Unlock**: Mở khóa cửa điều khiển từ xa qua lệnh relay `CMD_UNLOCK (31)`.
  - ⏰ **UTC Offset**: Tự động tính toán độ lệch múi giờ thiết bị từ đồng hồ máy chấm công.

---

## 🚀 Hướng dẫn bắt đầu nhanh (Quick Start)

### 1. Yêu cầu môi trường
- JDK 17 hoặc mới hơn (đã kiểm thử hoàn hảo trên JDK 17 và JDK 21 LTS).
- Maven 3.8+ (tùy chọn, để build jar).

### 2. Biên dịch và đóng gói với Maven

```bash
# Clone repository
git clone https://github.com/fandbsoft/zkteco4730-java.git
cd zkteco4730-java

# Build file JAR thực thi (kèm source code)
mvn clean package
```

File `.jar` độc lập sẽ được tạo tại: `target/zkteco4370-java-1.0.0.jar`.

### 3. Chạy kiểm thử dòng lệnh (CLI Testing)

```bash
# Xem hướng dẫn sử dụng CLI
java -jar target/zkteco4370-java-1.0.0.jar -h

# Chạy kiểm thử trực tiếp tới máy chấm công cụ thể:
# Cú pháp: java -jar target/zkteco4370-java-1.0.0.jar <IP> [PORT] [PASSWORD] [LABEL]
java -jar target/zkteco4370-java-1.0.0.jar 192.168.1.28 4370 111111 "SenseFace 2A"
java -jar target/zkteco4370-java-1.0.0.jar 192.168.1.39 4370 111111 "Ronald Jack DG600BID"

# Hoặc chạy kiểm thử mặc định tự động dò các thiết bị trên mạng LAN:
java -jar target/zkteco4370-java-1.0.0.jar
```

---

## 💻 Hướng dẫn sử dụng trong mã nguồn Java (Code Examples)

### Tích hợp qua Maven (`pom.xml`)
Nếu đưa vào dự án nội bộ hoặc copy source code:
```xml
<dependency>
    <groupId>com.zkteco</groupId>
    <artifactId>zkteco4370-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Ví dụ lập trình cơ bản

```java
import java.util.List;
import zkteco.ZKTeco4370_AttendanceLog;
import zkteco.ZKTeco4370_DeviceInfo;
import zkteco.ZKTeco4370_UserInfo;
import zkteco.ZKTeco4370_ZkClient;

public class AttendanceApp {
    public static void main(String[] args) {
        String ip = "192.168.1.28";
        int port = 4370;
        int commKey = 111111; // Mật mã kết nối PC (nếu không đặt mật mã thì để 0)

        // Sử dụng try-with-resources để tự động đóng kết nối an toàn
        try (ZKTeco4370_ZkClient zk = new ZKTeco4370_ZkClient(ip, port, commKey)) {
            // 1. Kết nối và tự động nhận diện firmware (Legacy hoặc Secure)
            zk.connect();
            System.out.println("Giao thức kết nối: " + zk.getProtocolName());

            // 2. Lấy thông tin thiết bị
            ZKTeco4370_DeviceInfo device = zk.getDeviceInfo();
            System.out.printf("Model: %s | Serial: %s | Firmware: %s%n", 
                    device.getDeviceModel(), device.getSerialNumber(), device.getFirmwareVersion());
            System.out.printf("Dung lượng: %d users, %d logs, %d faces%n",
                    device.getUserCount(), device.getLogCount(), device.getFaceCount());

            // 3. Lấy danh sách nhân viên
            List<ZKTeco4370_UserInfo> users = zk.getAllUser();
            for (ZKTeco4370_UserInfo u : users) {
                System.out.printf("ID: %s | Tên: %s | Ngày tạo: %s%n", 
                        u.getUserId(), u.getName(), u.getCreatedAt());
            }

            // 4. Lấy tất cả lịch sử chấm công
            List<ZKTeco4370_AttendanceLog> allLogs = zk.getAllLog();
            System.out.println("Tổng số bản ghi tải về: " + allLogs.size());

            // 5. Lọc chấm công theo khoảng thời gian [start, end]
            long startMillis = 1788973200000L; // Chấp nhận Epoch Milliseconds hoặc Epoch Seconds
            long endMillis = 1789232399000L;
            List<ZKTeco4370_AttendanceLog> filteredLogs = zk.getLogAt(startMillis, endMillis);
            for (ZKTeco4370_AttendanceLog log : filteredLogs) {
                System.out.printf("User %s check-in luc %s (%s)%n",
                        log.getUserId(), log.getTimestamp(), log.getVerifyModeName());
            }

            // 6. Mở khóa cửa từ xa (relay trễ 5 giây)
            boolean ok = zk.unlock(5);
            System.out.println("Mở khóa cửa: " + (ok ? "THÀNH CÔNG" : "THẤT BẠI"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

## ⚙️ Cấu trúc dự án (Architecture)

Toàn bộ API được tổ chức gọn gàng trong package `zkteco`:

```text
src/
├── main/
│   └── Main.java                       # CLI test runner thân thiện
└── zkteco/
    ├── ZKTeco4370_ZkClient.java        # Facade Client kết nối chính (Auto-detect protocol)
    ├── ZKTeco4370_DeviceInfo.java      # Data model thông tin phần cứng
    ├── ZKTeco4370_UserInfo.java        # Data model thông tin nhân viên
    ├── ZKTeco4370_AttendanceLog.java   # Data model bản ghi chấm công
    ├── ZKTeco4370_ZkCrypto.java        # Thuật toán MakeKey, DMC exchange, RSA, AES-CBC
    ├── ZKTeco4370_ZkPacket.java        # Đóng gói frame TCP Magic 0x5050827D & Checksum 1's complement
    ├── ZKTeco4370_ZkConstants.java     # Bảng mã Opcodes, Status codes, Magic numbers
    ├── ZKTeco4370_ZkException.java     # Ngoại lệ nghiệp vụ ZKTeco
    ├── ZKTeco4370_RecordParser.java    # Parser bản ghi log SSR 40-byte streaming
    ├── ZKTeco4370_UserParser.java      # Parser bản ghi user 72-byte / 28-byte streaming
    ├── ZKTeco4370_TimeCodec.java       # Bộ giải mã thời gian nén 32-bit ZKTeco
    └── ZKTeco4370_ProtocolMode.java    # Enum chế độ giao thức (LEGACY_PULL / SECURE_PULL)
```

---

## 🛡️ Thiết lập trên máy chấm công (Device Settings)

Để kết nối qua cổng 4370:
1. **IP & Mạng LAN**: Đảm bảo PC và máy chấm công cùng dải mạng LAN, ping thông suốt.
2. **Mật mã kết nối (Comm Key)**:
   - Trên máy: Vào **Menu** $\rightarrow$ **Giao tiếp (Comm.)** $\rightarrow$ **Cài đặt PC (PC Connection)**.
   - Kiểm tra **Mã kết nối (Comm Key)**: Điền số tương ứng vào tham số `password` của `ZKTeco4370_ZkClient` (mặc định là `0` hoặc `111111`).
3. **Cổng mạng**: Cổng mặc định là `4370`.

---

## 📄 Bản quyền (License)

Dự án được phân phối theo giấy phép [MIT License](LICENSE). Bạn hoàn toàn tự do sử dụng, chỉnh sửa và tích hợp vào các dự án phần mềm thương mại hoặc mã nguồn mở.
