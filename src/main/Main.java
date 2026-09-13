package main;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import zkteco.ZKTeco4370_AttendanceLog;
import zkteco.ZKTeco4370_DeviceInfo;
import zkteco.ZKTeco4370_UserInfo;
import zkteco.ZKTeco4370_ZkClient;
import zkteco.ZKTeco4370_ZkConstants;

/**
 * Test program for the unified zkteco TCP 4370 library.
 */
public class Main {

	public static void main(String[] args) {
		System.out.println("================================================================================");
		System.out.println("       KIEM THU THU VIEN zkteco - PURE JAVA RAW SOCKET TCP 4370                 ");
		System.out.println("       AUTO DETECT LEGACY FIRMWARE VA SECURE SENSEFACE/LINUX FIRMWARE           ");
		System.out.println("================================================================================");

		if (args.length >= 3) {
			String label = args.length >= 4 ? args[3] : "CUSTOM DEVICE";
			testDevice(label, args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
		} else {
//			testDevice("MAY 1: RONALD JACK DG 600BID", "192.168.1.39", ZKTeco4370_ZkConstants.DEFAULT_PORT, 111111);
			System.out.println("\n\n");
//			testDevice("MAY 2: ZKTECO SENSEFACE 2A", "192.168.1.33", ZKTeco4370_ZkConstants.DEFAULT_PORT, 111111);
			System.out.println("\n\n");
			testDevice("MAY 3: ZKTECO SENSEFACE 2Aa", "192.168.1.28", ZKTeco4370_ZkConstants.DEFAULT_PORT, 111111);
		}

		System.out.println("\n================================================================================");
		System.out.println(" HOAN TAT KIEM THU THU VIEN zkteco");
		System.out.println("================================================================================");
	}

	public static void testDevice(String label, String ip, int port, int password) {
		System.out.println("********************************************************************************");
		System.out.printf("  DANG KIEM THU: %s (%s:%d)%n", label, ip, port);
		System.out.println("********************************************************************************");

		try (ZKTeco4370_ZkClient zk = new ZKTeco4370_ZkClient(ip, port, password)) {
			zk.connect();
			System.out.println("-> Protocol tu dong nhan dien: " + zk.getProtocolName());

			System.out.println("\n>>> [1] Lay thong tin device");
			ZKTeco4370_DeviceInfo info = zk.getDeviceInfo();
			System.out.printf("   + Serial Number      : %s%n", info.getSerialNumber());
			System.out.printf("   + Firmware Version   : %s%n", info.getFirmwareVersion());
			System.out.printf("   + Platform           : %s%n", info.getPlatform());
			System.out.printf("   + MAC                : %s%n", info.getMacAddress());
			System.out.printf("   + Model              : %s%n", info.getDeviceModel());
			System.out.printf("   + User Count         : %d%n", info.getUserCount());
			System.out.printf("   + Fingerprint Count  : %d%n", info.getFpCount());
			System.out.printf("   + Face Count         : %d%n", info.getFaceCount());
			System.out.printf("   + Log Count          : %d%n", info.getLogCount());
			int gmtOffsetMinutes = zk.getDeviceGmtOffsetMinutes();
			System.out.printf("   + GMT Offset         : %s (%d minutes)%n",
					ZKTeco4370_ZkClient.formatGmtOffsetText(gmtOffsetMinutes), gmtOffsetMinutes);
			System.out.println("   => [PASS] ZKTeco4370_DeviceInfo");

			System.out.println("\n>>> [2] Lay danh sach user(userID, name, ngay tao)");
			List<ZKTeco4370_UserInfo> users = zk.getAllUser();
			System.out.printf("-> Tim thay %d user:%n", users.size());
			for (ZKTeco4370_UserInfo user : users) {
				System.out.printf("   * ID: %-6s | Name: %-16s | CreatedAt: %-20s | Epoch: %-13d | Privilege: %-2d | Enabled: %s%n",
						user.getUserId(), user.getName(), user.getCreatedAt(), user.getCreatedAtEpochMilli(),
						user.getPrivilege(), user.isEnabled());
			}
			System.out.println("   => [PASS] ZKTeco4370_UserInfo");

			System.out.println("\n>>> [3] Lay tat ca log cham cong");
			long t1 = System.currentTimeMillis();
			List<ZKTeco4370_AttendanceLog> allLogs = zk.getAllLog();
			long t2 = System.currentTimeMillis();
			System.out.printf("-> Tai thanh cong %d log trong %d ms:%n", allLogs.size(), t2 - t1);
			for (int i = 0; i < allLogs.size(); i++) {
				System.out.printf("   [%02d] %s%n", i + 1, formatLog(allLogs.get(i)));
			}
			System.out.println("   => [PASS] getAllLog");

			System.out.println("\n>>> [4] Lay log cham cong theo thoi diem long start, long end");
			LocalDateTime startRange = LocalDateTime.of(2026, 9, 10, 0, 0, 0);
			LocalDateTime endRange = LocalDateTime.of(2026, 9, 12, 23, 59, 59);
			long startMillis = startRange.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			long endMillis = endRange.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			List<ZKTeco4370_AttendanceLog> rangeLogs = zk.getLogAt(startMillis, endMillis);
			System.out.printf("-> Tim thay %d log trong khoang [%d, %d]:%n", rangeLogs.size(), startMillis, endMillis);
			for (int i = 0; i < rangeLogs.size(); i++) {
				System.out.printf("   [%02d] %s%n", i + 1, formatLog(rangeLogs.get(i)));
			}
			System.out.println("   => [PASS] getLogAt");

			System.out.println("\n>>> [5] Mo cua");
			int delaySeconds = ZKTeco4370_ZkConstants.DEFAULT_UNLOCK_DELAY_SECONDS;
			System.out.printf("-> Gui CMD_UNLOCK=%d, delay=%d giay...%n", ZKTeco4370_ZkConstants.CMD_UNLOCK, delaySeconds);
			boolean unlocked = zk.unlock(delaySeconds);
			System.out.println("-> Trang thai mo cua: " + (unlocked ? "THANH CONG [PASS]" : "THAT BAI [FAIL]"));
		} catch (Exception e) {
			System.err.println("   [FAIL] " + label + ": " + e.getMessage());
			e.printStackTrace(System.err);
		}
	}

	private static String formatLog(ZKTeco4370_AttendanceLog log) {
		return String.format("userId=%-6s | time=%s | verify=%-16s | state=%-10s | workCode=%d",
				log.getUserId(),
				log.getTimestamp(),
				log.getVerifyModeName() + "(" + log.getVerifyMode() + ")",
				log.getInOutModeName() + "(" + log.getInOutMode() + ")",
				log.getWorkCode());
	}
}
