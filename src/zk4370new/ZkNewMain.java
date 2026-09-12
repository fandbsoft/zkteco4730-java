package zk4370new;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Chương trình kiểm thử Raw Socket độc lập dành riêng cho các thiết bị ZKTeco Firmware mới (zk4370new).
 * Thực hiện kết nối tới thiết bị ZKTeco firmware mới qua cổng TCP 4370.
 */
public class ZkNewMain {

	public static void main(String[] args) {
		String targetIp = args.length > 0 ? args[0] : "192.168.1.28";
		int targetPort = args.length > 1 ? Integer.parseInt(args[1]) : ZkNewConstants.DEFAULT_PORT;
		int commKey = args.length > 2 ? Integer.parseInt(args[2]) : 111111; // Có thể thay đổi nếu thiết bị cài đặt mật khẩu khác
		Long rangeStart = args.length > 3 ? Long.parseLong(args[3]) : null;
		Long rangeEnd = args.length > 4 ? Long.parseLong(args[4]) : null;

		System.out.println("================================================================================");
		System.out.println("   KIỂM THỬ GIAO THỨC RAW SOCKET CỔNG 4370 CHO FIRMWARE MỚI (PACKAGE zk4370new) ");
		System.out.println("   THIẾT BỊ MỤC TIÊU: ZKTeco SenseFace 2A (" + targetIp + ":" + targetPort + ")");
		System.out.println("================================================================================");

		System.out.println("\n[BƯỚC 1] Khởi tạo ZkNewSocketClient và thực hiện Deep Diagnostic Probe...");

		try (ZkNewSocketClient client = new ZkNewSocketClient(targetIp, targetPort, commKey, 5000, 5000)) {
			ZkNewSocketClient.ProbeReport report = client.probe();

			System.out.println("\n--------------------------------------------------------------------------------");
			System.out.println(" KẾT QUẢ THỬ NGHIỆM RAW SOCKET TỚI " + targetIp + ":" + targetPort + ":");
			System.out.println("--------------------------------------------------------------------------------");
			System.out.printf(" + Trạng thái kết nối TCP    : %s%n", report.tcpConnected ? "THÀNH CÔNG (PORT 4370 MỞ)" : "THẤT BẠI");
			System.out.printf(" + Mã phản hồi thiết bị      : %d (0x%04X)%n", report.responseCode, report.responseCode);
			System.out.printf(" + Session ID được cấp       : %d%n", report.sessionId);
			System.out.printf(" + Gói tin Hex thô nhận được : %s%n", report.rawHex);
			System.out.printf(" + Trạng thái phiên          : %s%n", report.state);
			if (report.authResponseCode != -1) {
				System.out.printf(" + Phản hồi lệnh CMD_AUTH    : %d (0x%04X)%n", report.authResponseCode, report.authResponseCode);
			}
			if (report.authExtResponseCode != -1) {
				System.out.printf(" + Phản hồi lệnh CMD_AUTH_EXT: %d (0x%04X)%n", report.authExtResponseCode, report.authExtResponseCode);
			}
			System.out.printf(" + Secure handshake          : %s%n", report.secureHandshakeOk ? "THÀNH CÔNG" : "CHƯA THÀNH CÔNG");
			System.out.printf(" + Encrypted channel         : %s%n", report.encryptedChannelActive ? "ĐÃ BẬT" : "CHƯA BẬT");
			System.out.println("--------------------------------------------------------------------------------");
			System.out.println(" PHÂN TÍCH KỸ THUẬT & ĐÁNH GIÁ:");
			System.out.println(" " + report.diagnosticMessage);
			System.out.println("--------------------------------------------------------------------------------");

			if (report.state == ZkNewSocketClient.ConnectionState.CHALLENGE_6001_RECEIVED
					|| report.state == ZkNewSocketClient.ConnectionState.AUTH_LOCK_2032) {
				System.out.println("\n=> KẾT LUẬN & HƯỚNG DẪN XỬ LÝ CHO SENSEFACE 2A:");
				System.out.println(" 1. Máy SenseFace 2A đã nhận Raw Socket trên cổng 4370 và phản hồi mã thách thức 6001.");
				System.out.println(" 2. Tuy nhiên, thiết bị từ chối xác thực kéo truyền thống (trả mã 2032 AUTH_LOCK).");
				System.out.println(" 3. Nguyên nhân: SenseFace 2A (Firmware 2024/2025) đang được cấu hình ở chế độ");
				System.out.println("    'Cloud Push / ADMS' hoặc đã bật mã hóa bắt buộc.");
				System.out.println(" 4. Để kéo trực tiếp qua cổng 4370, vui lòng mở màn hình máy SenseFace 2A:");
				System.out.println("    -> Menu -> Thiết lập (Comm / Giao tiếp) -> Kết nối PC (PC Connection)");
				System.out.println("    -> Chuyển từ Push/Cloud sang chế độ Standalone / Legacy Pull");
				System.out.println("    -> Kiểm tra Comm Key (Mật mã kết nối PC) có khớp với " + commKey + " hay không.");
			} else if (report.state == ZkNewSocketClient.ConnectionState.AUTH_SUCCESS
					|| report.state == ZkNewSocketClient.ConnectionState.CONNECTED_STANDARD) {
				System.out.println("\n=> KẾT NỐI VÀ XÁC THỰC THÀNH CÔNG! Đang lấy thông tin máy...");
				try {
					ZkNewDeviceInfo devInfo = client.getDeviceInfo();
					System.out.println(" + Thông tin máy: " + devInfo);

					List<ZkNewUserInfo> users = client.getAllUserInfo();
					System.out.println(" + Số user đọc được: " + users.size());
					for (ZkNewUserInfo user : users) {
						System.out.println("   - " + user);
					}

					List<ZkNewAttendanceLog> logs = client.getAttendanceLogs();
					System.out.println(" + Số log chấm công đọc được: " + logs.size());
					for (int i = 0; i < Math.min(5, logs.size()); i++) {
						System.out.println("   - " + logs.get(i));
					}

					if (rangeStart == null || rangeEnd == null) {
						LocalDateTime firstTime = logs.stream()
								.map(ZkNewAttendanceLog::getTimestamp)
								.filter(t -> t != null)
								.min(LocalDateTime::compareTo)
								.orElse(null);
						if (firstTime != null) {
							LocalDateTime dayStart = firstTime.toLocalDate().atStartOfDay();
							LocalDateTime dayEnd = dayStart.plusDays(1).minusSeconds(1);
							rangeStart = dayStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
							rangeEnd = dayEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
						}
					}
					if (rangeStart != null && rangeEnd != null) {
						List<ZkNewAttendanceLog> rangedLogs = client.getLogAt(rangeStart, rangeEnd);
						System.out.println(" + Số log trong khoảng [" + rangeStart + ", " + rangeEnd + "]: " + rangedLogs.size());
					}
				} catch (Exception ex) {
					System.out.println(" + Lỗi đọc thông tin: " + ex.getMessage());
				}
			}
		} catch (Exception e) {
			System.err.println("[LỖI NGOẠI LỆ]: " + e.getMessage());
			e.printStackTrace();
		}

		System.out.println("\n================================================================================");
		System.out.println(" HOÀN TẤT KIỂM THỬ zk4370new VỚI " + targetIp + ":" + targetPort);
		System.out.println("================================================================================");
	}
}
