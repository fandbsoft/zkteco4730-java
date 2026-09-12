package zk4370new;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Chuong trinh kiem thu toan dien package zk4370new cho ZKTeco firmware moi.
 * Chay bang 100% Java TCP Socket tren cong 4370, khong dung COM/DLL/native.
 */
public class ZkNewMain {

	public static void main(String[] args) {
		System.out.println("================================================================================");
		System.out.println("       KIEM THU TOAN DIEN THU VIEN zk4370new - RAW SOCKET PORT 4370             ");
		System.out.println("       ZKTECO FIRMWARE MOI / SENSEFACE - ZERO COM DLL / ZERO NATIVE             ");
		System.out.println("================================================================================");

		if (args.length >= 3) {
			String label = args.length > 3 ? args[3] : "CUSTOM DEVICE";
			testDevice(label, args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
		} else {
			testDevice("MÁY 2: ZKTECO SENSEFACE 2A", "192.168.1.33", ZkNewConstants.DEFAULT_PORT, 111111);
			System.out.println("\n\n");
			testDevice("MÁY 3: ZKTECO SENSEFACE 2Aa", "192.168.1.28", ZkNewConstants.DEFAULT_PORT, 111111);
		}

		System.out.println("\n================================================================================");
		System.out.println(" KET LUAN:");
		System.out.println(" - Thu vien zk4370new dung secure raw socket TCP 4370 cho firmware moi.");
		System.out.println(" - Ho tro device info, user info, mo khoa cua, getAllLog va getLogAt.");
		System.out.println(" - Package legacy main.zk khong bi thay doi.");
		System.out.println("================================================================================");
	}

	public static void testDevice(String label, String ip, int port, int password) {
		System.out.println("********************************************************************************");
		System.out.printf("  DANG KIEM THU: %s (%s:%d)%n", label, ip, port);
		System.out.println("********************************************************************************");

		System.out.println("\n>>> [PHAN 1] KIEM THU DeviceInfo: Lay thong tin thiet bi");
		try (ZkNewSocketClient devClient = new ZkNewSocketClient(ip, port, password)) {
			ZkNewDeviceInfo info = devClient.getDeviceInfo();
			System.out.println("-> Thong tin thiet bi nhan duoc:");
			System.out.printf("   + Serial Number      : %s%n", info.getSerialNumber());
			System.out.printf("   + Firmware Version   : %s%n", info.getFirmwareVersion());
			System.out.printf("   + Nen tang Platform  : %s%n", info.getPlatform());
			System.out.printf("   + Dia chi MAC        : %s%n", info.getMacAddress());
			System.out.printf("   + Model thiet bi     : %s%n", info.getDeviceModel());
			System.out.printf("   + Tong so User       : %d%n", info.getUserCount());
			System.out.printf("   + Tong so Van tay    : %d%n", info.getFpCount());
			System.out.printf("   + Tong so Khuon mat  : %d%n", info.getFaceCount());
			System.out.printf("   + Tong so Log        : %d%n", info.getLogCount());
			System.out.println("   => [PASS] DeviceInfo hoat dong thanh cong!");
		} catch (Exception e) {
			System.err.println("   [FAIL] Loi kiem thu DeviceInfo: " + e.getMessage());
		}

		System.out.println("\n>>> [PHAN 2] KIEM THU UserInfo: Lay danh sach nhan vien");
		try (ZkNewSocketClient userClient = new ZkNewSocketClient(ip, port, password)) {
			List<ZkNewUserInfo> users = userClient.getAllUser();
			System.out.printf("-> Tim thay %d nhan vien tren may cham cong:%n", users.size());
			for (ZkNewUserInfo u : users) {
				System.out.printf("   * ID: %-4s | Ten: %-12s | Ngay tao: %-20s | Epoch: %-13d | Quyen: %-2d | Enabled: %s%n",
						u.getUserId(), u.getName(), u.getCreatedAt(), u.getCreatedAtEpochMilli(),
						u.getPrivilege(), u.isEnabled());
			}

			ZkNewUserInfo u1 = userClient.getUser("1");
			if (u1 != null) {
				System.out.printf("   + Kiem tra getUser('1') -> Ten: %s, Ngay tao: %s%n",
						u1.getName(), u1.getCreatedAt());
			}
			System.out.println("   => [PASS] UserInfo san sang map vao phan mem MyBM!");
		} catch (Exception e) {
			System.err.println("   [FAIL] Loi kiem thu UserInfo: " + e.getMessage());
		}

		System.out.println("\n>>> [PHAN 3] KIEM THU Unlock: Lenh mo cua Access Control");
		try (ZkNewUnlock unlocker = new ZkNewUnlock(ip, port, password)) {
			int delaySeconds = ZkNewUnlock.DEFAULT_DELAY_SECONDS;
			System.out.printf("-> Gui lenh mo cua CMD_UNLOCK=%d (Delay %d giay)...%n",
					ZkNewConstants.CMD_UNLOCK, delaySeconds);
			boolean unlocked = unlocker.unlock(delaySeconds);
			System.out.println("-> Trang thai mo cua: "
					+ (unlocked ? "THANH CONG (Relay Activated) [PASS]" : "THAT BAI [FAIL]"));
		} catch (Exception e) {
			System.err.println("   [FAIL] Loi kiem thu Unlock: " + e.getMessage());
		}

		System.out.println("\n>>> [PHAN 4] KIEM THU AttendanceLog: getAllLog() va getLogAt(...)");
		try (ZkNewSocketClient logClient = new ZkNewSocketClient(ip, port, password)) {
			System.out.println("\n [4.1] Goi getAllLog() lay toan bo log...");
			long t1 = System.currentTimeMillis();
			List<ZkNewAttendanceLog> allLogs = logClient.getAllLog();
			long t2 = System.currentTimeMillis();
			System.out.printf(" -> Tai thanh cong %d ban ghi (Thoi gian: %d ms)%n", allLogs.size(), (t2 - t1));
			for (int i = 0; i < allLogs.size(); i++) {
				System.out.printf("    [%02d] %s%n", i + 1, formatLog(allLogs.get(i)));
			}

			LocalDateTime startRange = LocalDateTime.of(2026, 9, 10, 0, 0, 0);
			LocalDateTime endRange = LocalDateTime.of(2026, 9, 12, 23, 59, 59);
			long startMillis = startRange.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			long endMillis = endRange.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

			System.out.println("\n [4.2] Goi getLogAt(startMillis, endMillis) theo Epoch Milliseconds [10/09 - 12/09]...");
			List<ZkNewAttendanceLog> rangeLogs = logClient.getLogAt(startMillis, endMillis);
			System.out.printf(" -> Ket qua: Tim thay %d ban ghi:%n", rangeLogs.size());
			for (int i = 0; i < rangeLogs.size(); i++) {
				System.out.printf("    [%02d] %s%n", i + 1, formatLog(rangeLogs.get(i)));
			}
			System.out.println("\n   => [PASS] AttendanceLog hoat dong thanh cong!");
		} catch (Exception e) {
			System.err.println("   [FAIL] Loi kiem thu AttendanceLog: " + e.getMessage());
		}
	}

	private static String formatLog(ZkNewAttendanceLog log) {
		return String.format("userId=%-4s | time=%s | verify=%-18s | state=%-12s | workCode=%d",
				log.getUserId(),
				log.getTimestamp(),
				log.getVerifyModeName() + "(" + log.getVerifyMode() + ")",
				log.getInOutModeName() + "(" + log.getInOutMode() + ")",
				log.getWorkCode());
	}
}
