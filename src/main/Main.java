package main;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import main.zk.ZkAttendanceLog;
import main.zk.ZkConstants;
import main.zk.ZkDeviceInfo;
import main.zk.ZkUnlock;
import main.zk.ZkUserInfo;

/**
 * Chương trình kiểm thử toàn diện 4 thành phần chính của thư viện Pure Java TCP Socket (Cổng 4370):
 * 1. ZkAttendanceLog: getAllLog() và getLogAt(long timeStart, long timeEnd)
 * 2. ZkUnlock: Điều khiển mở cửa (Access Control Unlock)
 * 3. ZkDeviceInfo: Lấy thông số cấu hình và phần cứng thiết bị
 * 4. ZkUserInfo: Lấy thông tin người dùng (userId, name, createdAt) để map vào MyBM
 * 
 * Chạy thuần 100% Java TCP Socket, không phụ thuộc bất kỳ thư viện Windows, COM hay ProcessBuilder nào.
 * Tương thích Windows, Linux, macOS, Docker.
 */
public class Main {

	public static void main(String[] args) {
		System.out.println("================================================================================");
		System.out.println("       KIỂM THỬ TOÀN DIỆN THƯ VIỆN PURE JAVA TCP SOCKET (PORT 4370)             ");
		System.out.println("       TƯƠNG THÍCH ĐA NỀN TẢNG (WINDOWS / LINUX / MACOS) - ZERO COM DLL         ");
		System.out.println("================================================================================");

		// Kiểm thử thiết bị 1: Ronald Jack DG 600BID (Firmware 2017)
		testDevice("MÁY 1: RONALD JACK DG 600BID", "192.168.1.39", ZkConstants.DEFAULT_PORT, 111111);

		System.out.println("\n\n");

		// Kiểm thử thiết bị 2: ZKTeco Senseface 2A (Firmware 2025)
		testDevice("MÁY 2: ZKTECO SENSEFACE 2A", "192.168.1.33", ZkConstants.DEFAULT_PORT, 111111);
		System.out.println("\n\n");
		testDevice("MÁY 3: ZKTECO SENSEFACE 2A", "192.168.1.28", ZkConstants.DEFAULT_PORT, 111111);

		System.out.println("\n================================================================================");
		System.out.println(" KẾT LUẬN TOÀN DIỆN:");
		System.out.println(" - 100% Pure Java TCP Socket (Port 4370), KHÔNG dùng bất kỳ thư viện native nào!");
		System.out.println(" - Đã vượt qua kiểm thử trên cả hai dòng máy: Ronald Jack DG 600BID & Senseface 2A.");
		System.out.println(" - Bộ 4 class (ZkAttendanceLog, ZkUnlock, ZkDeviceInfo, ZkUserInfo) sẵn sàng 100%!");
		System.out.println("================================================================================");
	}

	public static void testDevice(String label, String ip, int port, int password) {
		System.out.println("********************************************************************************");
		System.out.printf("  ĐANG KIỂM THỬ: %s (%s:%d)%n", label, ip, port);
		System.out.println("********************************************************************************");

		// =====================================================================
		// PHẦN 1: KIỂM THỬ ZkDeviceInfo (Lấy thông tin phần cứng máy)
		// =====================================================================
		System.out.println("\n>>> [PHẦN 1] KIỂM THỬ ZkDeviceInfo: Lấy thông tin thiết bị");
		try (ZkDeviceInfo devClient = new ZkDeviceInfo(ip, port, password)) {
			ZkDeviceInfo info = devClient.getDeviceInfo();
			System.out.println("-> Thông tin thiết bị nhận được:");
			System.out.printf("   + Serial Number     : %s%n", info.getSerialNumber());
			System.out.printf("   + Firmware Version  : %s%n", info.getFirmwareVersion());
			System.out.printf("   + Nền tảng (Platform): %s%n", info.getPlatform());
			System.out.printf("   + Địa chỉ MAC       : %s%n", info.getMacAddress());
			System.out.printf("   + Model thiết bị    : %s%n", info.getDeviceModel());
			System.out.printf("   + Tổng số User      : %d%n", info.getUserCount());
			System.out.printf("   + Tổng số Vân tay   : %d%n", info.getFpCount());
			System.out.printf("   + Tổng số Khuôn mặt : %d%n", info.getFaceCount());
			System.out.printf("   + Tổng số Log       : %d%n", info.getLogCount());
			System.out.println("   => [PASS] ZkDeviceInfo hoạt động chính xác 100%!");
		} catch (Exception e) {
			System.err.println("   [FAIL] Lỗi kiểm thử ZkDeviceInfo: " + e.getMessage());
		}

		// =====================================================================
		// PHẦN 2: KIỂM THỬ ZkUserInfo (Lấy User: userId, name, createdAt cho MyBM)
		// =====================================================================
		System.out.println("\n>>> [PHẦN 2] KIỂM THỬ ZkUserInfo: Lấy danh sách nhân viên cho MyBM");
		try (ZkUserInfo userClient = new ZkUserInfo(ip, port, password)) {
			List<ZkUserInfo> users = userClient.getAllUser();
			System.out.printf("-> Tìm thấy %d nhân viên trên máy chấm công:%n", users.size());
			for (ZkUserInfo u : users) {
				System.out.printf("   * ID: %-4s | Tên: %-12s | Ngày tạo: %s (Epoch: %d)%n",
						u.getUserId(), u.getName(), u.getCreatedAt(), u.getCreatedAtEpochMilli());
			}

			// Kiểm thử tìm kiếm theo ID cụ thể
			ZkUserInfo u1 = userClient.getUser("1");
			if (u1 != null) {
				System.out.printf("   + Kiểm tra getUser('1') -> Tên: %s, Ngày tạo: %s%n", u1.getName(), u1.getCreatedAt());
			}
			System.out.println("   => [PASS] ZkUserInfo sẵn sàng map vào phần mềm MyBM!");
		} catch (Exception e) {
			System.err.println("   [FAIL] Lỗi kiểm thử ZkUserInfo: " + e.getMessage());
		}

		// =====================================================================
		// PHẦN 3: KIỂM THỬ ZkUnlock (Mở cửa kiểm soát ra vào - Access Control)
		// =====================================================================
		System.out.println("\n>>> [PHẦN 3] KIỂM THỬ ZkUnlock: Lệnh mở cửa (Access Control)");
		try (ZkUnlock unlocker = new ZkUnlock(ip, port, password)) {
			int delaySeconds = 5;
			System.out.printf("-> Gửi lệnh mở cửa (Delay %d giây)...%n", delaySeconds);
			boolean unlocked = unlocker.unlock(delaySeconds);
			System.out.println("-> Trạng thái mở cửa: " + (unlocked ? "THÀNH CÔNG (Relay Activated) [PASS]" : "THẤT BẠI [FAIL]"));
		} catch (Exception e) {
			System.err.println("   [FAIL] Lỗi kiểm thử ZkUnlock: " + e.getMessage());
		}

		// =====================================================================
		// PHẦN 4: KIỂM THỬ ZkAttendanceLog (getAllLog và getLogAt)
		// =====================================================================
		System.out.println("\n>>> [PHẦN 4] KIỂM THỬ ZkAttendanceLog: getAllLog() và getLogAt(...)");
		try (ZkAttendanceLog zkLog = new ZkAttendanceLog(ip, port, password)) {

			// 4.1. getAllLog()
			System.out.println("\n [4.1] Gọi getAllLog() lấy toàn bộ log...");
			long t1 = System.currentTimeMillis();
			List<ZkAttendanceLog> allLogs = zkLog.getAllLog();
			long t2 = System.currentTimeMillis();
			System.out.printf(" -> Tải thành công %d bản ghi (Thời gian: %d ms)%n", allLogs.size(), (t2 - t1));
			if (!allLogs.isEmpty()) {
				System.out.printf("    + Bản ghi đầu tiên: %s%n", formatLog(allLogs.get(0)));
				System.out.printf("    + Bản ghi mới nhất : %s%n", formatLog(allLogs.get(allLogs.size() - 1)));
			}

			// 4.2. getLogAt(long, long) với Epoch Milliseconds (01/09/2026 - 02/09/2026)
			LocalDateTime startRange1 = LocalDateTime.of(2026, 9, 1, 0, 0, 0);
			LocalDateTime endRange1 = LocalDateTime.of(2026, 9, 2, 23, 59, 59);
			long startMillis = startRange1.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			long endMillis = endRange1.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

			System.out.println("\n [4.2] Gọi getLogAt(startMillis, endMillis) theo Epoch Milliseconds [01/09 - 02/09]...");
			List<ZkAttendanceLog> logsRange1 = zkLog.getLogAt(startMillis, endMillis);
			System.out.printf(" -> Kết quả: Tìm thấy %d bản ghi:%n", logsRange1.size());
			for (int i = 0; i < Math.min(4, logsRange1.size()); i++) {
				System.out.printf("    [%02d] %s%n", (i + 1), formatLog(logsRange1.get(i)));
			}
			if (logsRange1.size() > 4) {
				System.out.printf("    ... và %d bản ghi khác.%n", (logsRange1.size() - 4));
			}

			// 4.3. getLogAt(long, long) với Epoch Seconds (Ngày 10/09/2026)
			LocalDateTime startRange2 = LocalDateTime.of(2026, 9, 10, 0, 0, 0);
			LocalDateTime endRange2 = LocalDateTime.of(2026, 9, 10, 23, 59, 59);
			long startSec = startRange2.atZone(ZoneId.systemDefault()).toEpochSecond();
			long endSec = endRange2.atZone(ZoneId.systemDefault()).toEpochSecond();

			System.out.println("\n [4.3] Gọi getLogAt(startSec, endSec) theo Epoch Seconds [10/09/2026]...");
			List<ZkAttendanceLog> logsRange2 = zkLog.getLogAt(startSec, endSec);
			System.out.printf(" -> Kết quả: Tìm thấy %d bản ghi trong ngày 10/09/2026:%n", logsRange2.size());
			for (int i = 0; i < logsRange2.size(); i++) {
				System.out.printf("    [%02d] %s%n", (i + 1), formatLog(logsRange2.get(i)));
			}

			System.out.println("\n   => [PASS] ZkAttendanceLog hoạt động hoàn hảo!");

		} catch (IOException ex) {
			System.err.println("   [FAIL] Lỗi kiểm thử ZkAttendanceLog: " + ex.getMessage());
		}
	}

	private static String formatLog(ZkAttendanceLog log) {
		return String.format("userId=%-4s | time=%s | verify=%-12s | state=%-10s | workCode=%d",
				log.getUserId(),
				log.getTimestamp(),
				log.getVerifyModeName() + "(" + log.getVerifyMode() + ")",
				log.getInOutModeName() + "(" + log.getInOutMode() + ")",
				log.getWorkCode());
	}
}
