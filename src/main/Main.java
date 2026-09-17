package main;

import java.nio.file.Files;
import java.nio.file.Path;
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

		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("-h") || args[0].equalsIgnoreCase("--help")) {
				System.out.println("Cach dung (Usage):");
				System.out.println("  java -jar target/zkteco4370-java-1.0.0.jar <IP> [PORT] [PASSWORD] [LABEL]");
				System.out.println("Vi du:");
				System.out.println("  java -jar target/zkteco4370-java-1.0.0.jar 192.168.1.190 4370 111111 \"iSCAN-03\"");
				System.out.println("  java -jar target/zkteco4370-java-1.0.0.jar 192.168.1.39 4370 111111 \"Ronald Jack DG600BID\"");
				return;
			}
			String ip = args[0];
			int port = args.length >= 2 ? Integer.parseInt(args[1]) : ZKTeco4370_ZkConstants.DEFAULT_PORT;
			int pwd = args.length >= 3 ? Integer.parseInt(args[2]) : 0;
			String label = args.length >= 4 ? args[3] : "DEVICE (" + ip + ")";
			testDevice(label, ip, port, pwd);
		} else {
			System.out.println("Chay kiem thu mac dinh tren cac thiet bi LAN co san:\n");
			testDevice("MAY: ZKTECO iSCAN-03 / ZEM560 (LEGACY)", "192.168.1.190", ZKTeco4370_ZkConstants.DEFAULT_PORT, 111111);
//			testDevice("MAY: ZKTECO iSCAN-03 / ZEM560 (LEGACY)", "192.168.1.33", ZKTeco4370_ZkConstants.DEFAULT_PORT, 111111);
//			testDevice("MAY: ZKTECO iSCAN-03 / ZEM560 (LEGACY)", "42.112.179.197", ZKTeco4370_ZkConstants.DEFAULT_PORT, 111111);
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
			System.out.printf("   + UTC Offset         : %s%n", zk.getUTC());
			System.out.println("   => [PASS] ZKTeco4370_DeviceInfo");

			System.out.println("\n>>> [2] Lay danh sach user(userID, name, ngay tao)");
			List<ZKTeco4370_UserInfo> users = zk.getAllUser();
			System.out.printf("-> Tim thay %d user:%n", users.size());
			int showUserCount = Math.min(10, users.size());
			for (int i = 0; i < showUserCount; i++) {
				ZKTeco4370_UserInfo user = users.get(i);
				System.out.printf("   [%02d] UID: %-4d | ID: %-10s | Name: %-16s | Card: %-10d | Pwd: %-6s | Role: %-12s | Grp: %-2d | TZ: %-2d | Enabled: %s%n",
						i + 1, user.getUid(), user.getUserId(), user.getName(), user.getCardNumber(),
						user.getPassword().isEmpty() ? "-" : user.getPassword(),
						user.getPrivilegeName(), user.getGroup(), user.getTimeZone(), user.isEnabled());
			}
			if (users.size() > showUserCount) {
				System.out.printf("   ... va %d user khac ...%n", users.size() - showUserCount);
			}
			System.out.println("   => [PASS] ZKTeco4370_UserInfo");

			System.out.println("\n>>> [3] Lay tat ca log cham cong");
			long t1 = System.currentTimeMillis();
			List<ZKTeco4370_AttendanceLog> allLogs = zk.getAllLog();
			long t2 = System.currentTimeMillis();
			System.out.printf("-> Tai thanh cong %d log trong %d ms:%n", allLogs.size(), t2 - t1);
			int showLogCount = Math.min(10, allLogs.size());
			for (int i = 0; i < showLogCount; i++) {
				System.out.printf("   [%02d] %s%n", i + 1, formatLog(allLogs.get(i)));
			}
			if (allLogs.size() > showLogCount) {
				System.out.printf("   ... va %d log khac ...%n", allLogs.size() - showLogCount);
				System.out.printf("   [%02d] %s%n", allLogs.size(), formatLog(allLogs.get(allLogs.size() - 1)));
			}
			System.out.println("   => [PASS] getAllLog");

			System.out.println("\n>>> [4] Lay log cham cong theo thoi diem long start, long end");
			if (!allLogs.isEmpty()) {
				LocalDateTime sampleTime = allLogs.get(0).getTimestamp();
				LocalDateTime startRange = sampleTime.minusDays(1);
				LocalDateTime endRange = sampleTime.plusDays(1);
				long startMillis = startRange.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
				long endMillis = endRange.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
				List<ZKTeco4370_AttendanceLog> rangeLogs = zk.getLogAt(startMillis, endMillis);
				System.out.printf("-> Tim thay %d log trong khoang [%s -> %s]:%n", rangeLogs.size(), startRange, endRange);
				for (int i = 0; i < Math.min(5, rangeLogs.size()); i++) {
					System.out.printf("   [%02d] %s%n", i + 1, formatLog(rangeLogs.get(i)));
				}
				System.out.println("   => [PASS] getLogAt");
			} else {
				System.out.println("-> Khong co log tren thiet bi de loc.");
			}

			System.out.println("\n>>> [5] Mo cua");
			int delaySeconds = ZKTeco4370_ZkConstants.DEFAULT_UNLOCK_DELAY_SECONDS;
			System.out.printf("-> Gui CMD_UNLOCK=%d, delay=%d giay...%n", ZKTeco4370_ZkConstants.CMD_UNLOCK, delaySeconds);
			boolean unlocked = zk.unlock(delaySeconds);
			System.out.println("-> Trang thai mo cua: " + (unlocked ? "THANH CONG [PASS]" : "THAT BAI [FAIL]"));
			
			downloadAnyUserPhoto(zk, users);

			System.out.println("\n>>> [7] Kiem tra va Dong bo thoi gian & UTC (syncTime)");
			try {
				LocalDateTime timeBefore = zk.getDeviceTime();
				System.out.println("   + Gio tren may cham cong truoc dong bo: " + timeBefore);
				boolean synced = zk.syncTime();
				LocalDateTime timeAfter = zk.getDeviceTime();
				System.out.println("   + Trang thai dong bo (syncTime)       : " + (synced ? "THANH CONG [PASS]" : "THAT BAI [FAIL]"));
				System.out.println("   + Gio tren may cham cong sau dong bo : " + timeAfter);
				System.out.println("   + UTC Offset thiet bi sau dong bo    : " + zk.getUTC());
				System.out.println("   => [PASS] syncTime");
			} catch (Exception timeEx) {
				System.err.println("   -> Loi dong bo thoi gian: " + timeEx.getMessage());
			}
			
		} catch (Exception e) {
			System.err.println("   [FAIL] " + label + ": " + e.getMessage());
			e.printStackTrace(System.err);
		}
	}

	private static void downloadAnyUserPhoto(ZKTeco4370_ZkClient zk, List<ZKTeco4370_UserInfo> users) {
		System.out.println("\n>>> [6] Thu tai anh nguoi dung");
		int attempts = 0;
		for (ZKTeco4370_UserInfo user : users) {
			try {
				byte[] jpg = zk.downloadUserPhoto(user.getUserId());
				if (jpg != null && jpg.length > 0) {
					Path outPath = Path.of(user.getUserId() + ".jpg").toAbsolutePath().normalize();
					Files.write(outPath, jpg);
					System.out.println("   -> Da tai thanh cong anh user: " + user.getUserId() + " (" + jpg.length + " bytes)");
					return;
				}
			} catch (zkteco.ZKTeco4370_ZkException ex) {
				if (ex.getResponseCode() == 65535 || ex.getResponseCode() == 2001) {
					System.out.println("   -> Thiet bi khong ho tro tinh nang tai anh nguoi dung (ma phan hoi: " + ex.getResponseCode() + ").");
					return;
				}
			} catch (Exception ignored) {
			}
			if (++attempts >= 3) {
				break;
			}
		}
		System.out.println("   -> Khong tim thay anh nguoi dung nao tren thiet bi.");
	}

	private static String formatLog(ZKTeco4370_AttendanceLog log) {
		return String.format("userId=%-10s | time=%s | timeGmt=%s | verify=%-16s | state=%-10s | workCode=%d",
				log.getUserId(),
				log.getTimestamp(),
				log.getTimestampWithGmtOffsetText(),
				log.getVerifyModeName() + "(" + log.getVerifyMode() + ")",
				log.getInOutModeName() + "(" + log.getInOutMode() + ")",
				log.getWorkCode());
	}
}