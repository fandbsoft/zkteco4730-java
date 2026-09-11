package main.zk;

import java.io.IOException;

/**
 * Class ZkUnlock chuyên trách điều khiển mở cửa (Access Control / Door Unlock)
 * qua cổng 4370 cho máy chấm công và kiểm soát ra vào ZKTeco.
 * <p>
 * Hỗ trợ mở cửa từ xa với thời gian delay tùy chỉnh (mặc định 5 giây).
 * <p>
 * <b>Ví dụ sử dụng:</b>
 * <pre>{@code
 * try (ZkUnlock unlocker = new ZkUnlock("192.168.1.33", 4370, 111111)) {
 *     boolean success = unlocker.unlock(5); // Mở cửa 5 giây
 *     System.out.println("Mở cửa: " + (success ? "Thành công" : "Thất bại"));
 * }
 * }</pre>
 */
public class ZkUnlock implements AutoCloseable {
	public static final int DEFAULT_DELAY_SECONDS = 5;

	private final String ip;
	private final int port;
	private final int password;
	private final int machineNumber;

	private ZkProtocolAdapter activeAdapter;

	public ZkUnlock(String ip, int port, int password, int machineNumber) {
		if (ip == null || ip.isBlank()) {
			throw new IllegalArgumentException("IP không được để trống");
		}
		this.ip = ip.trim();
		this.port = (port > 0) ? port : ZkConstants.DEFAULT_PORT;
		this.password = password;
		this.machineNumber = (machineNumber > 0) ? machineNumber : 1;
	}

	public ZkUnlock(String ip, int port, int password) {
		this(ip, port, password, 1);
	}

	public ZkUnlock(String ip, int password) {
		this(ip, ZkConstants.DEFAULT_PORT, password, 1);
	}

	public ZkUnlock(String ip) {
		this(ip, ZkConstants.DEFAULT_PORT, 0, 1);
	}

	/**
	 * Mở cửa với thời gian delay tính bằng giây qua Pure Java TCP Socket.
	 *
	 * @param delaySeconds Số giây mở relay (ví dụ: 5s, 10s).
	 * @return true nếu lệnh mở cửa được thiết bị chấp thuận và thực thi.
	 * @throws IOException Nếu xảy ra lỗi mạng.
	 */
	public synchronized boolean unlock(int delaySeconds) throws IOException {
		int seconds = Math.max(1, delaySeconds);

		// 1. Thử gửi lệnh qua ZkLegacySocketAdapter (Pure Java Raw Socket)
		try {
			ensureConnected();
			if (activeAdapter.unlockDoor(seconds)) {
				return true;
			}
		} catch (ZkAuthChallengeException challenge) {
			// 2. Thiết bị firmware bảo mật mới (như SenseFace 2A / ZAM70), chuyển sang Smart Adapter thuần Java Socket
			switchToSmartAdapter();
			return activeAdapter.unlockDoor(seconds);
		}

		return false;
	}

	private synchronized void ensureConnected() throws IOException {
		if (activeAdapter != null && activeAdapter.isConnected()) {
			return;
		}
		if (activeAdapter != null) {
			try {
				activeAdapter.close();
			} catch (Exception ignored) {}
		}

		activeAdapter = new ZkLegacySocketAdapter(ip, port, password);
		try {
			activeAdapter.connect();
		} catch (ZkAuthChallengeException e) {
			switchToSmartAdapter();
		}
	}

	private synchronized void switchToSmartAdapter() throws IOException {
		if (activeAdapter != null) {
			try {
				activeAdapter.close();
			} catch (Exception ignored) {}
		}
		activeAdapter = new ZkSmartAdapter(ip, port, password);
		try {
			activeAdapter.connect();
		} catch (IOException ex) {
			activeAdapter.close();
			activeAdapter = null;
			throw ex;
		}
	}

	/**
	 * Mở cửa với thời gian delay mặc định (5 giây).
	 */
	public synchronized boolean unlock() throws IOException {
		return unlock(DEFAULT_DELAY_SECONDS);
	}

	/**
	 * Phương thức tiện ích static mở cửa nhanh một dòng không cần tạo biến.
	 */
	public static boolean quickUnlock(String ip, int port, int password, int delaySeconds) {
		try (ZkUnlock unlocker = new ZkUnlock(ip, port, password)) {
			return unlocker.unlock(delaySeconds);
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public synchronized void close() {
		if (activeAdapter != null) {
			try {
				activeAdapter.close();
			} catch (Exception ignored) {}
			activeAdapter = null;
		}
	}
}
