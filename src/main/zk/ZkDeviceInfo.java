package main.zk;

import java.io.IOException;

/**
 * Class ZkDeviceInfo trong package main.zk:
 * Vừa là đối tượng cấu hình phần cứng thiết bị (Data Model),
 * vừa là Client truy vấn thông số phần cứng từ máy chấm công.
 */
public class ZkDeviceInfo implements AutoCloseable {

	// =========================================================================
	// PHẦN A: CÁC TRƯỜNG DỮ LIỆU PHẦN CỨNG THIẾT BỊ (DATA MODEL)
	// =========================================================================
	private final String serialNumber;
	private final String firmwareVersion;
	private final String platform;
	private final String macAddress;
	private final String deviceModel;
	private final int userCount;
	private final int fpCount;
	private final int faceCount;
	private final int logCount;

	// =========================================================================
	// PHẦN B: CÁC THÔNG SỐ KẾT NỐI CLIENT
	// =========================================================================
	private final String ip;
	private final int port;
	private final int password;
	private final int machineNumber;

	// CONSTRUCTOR CHO ĐỐI TƯỢNG DATA MODEL
	public ZkDeviceInfo(String serialNumber, String firmwareVersion, String platform,
			String macAddress, String deviceModel, int userCount, int fpCount, int faceCount, int logCount) {
		this.serialNumber = (serialNumber != null) ? serialNumber.trim() : "";
		this.firmwareVersion = (firmwareVersion != null) ? firmwareVersion.trim() : "";
		this.platform = (platform != null) ? platform.trim() : "";
		this.macAddress = (macAddress != null) ? macAddress.trim() : "";
		this.deviceModel = (deviceModel != null) ? deviceModel.trim() : "";
		this.userCount = userCount;
		this.fpCount = fpCount;
		this.faceCount = faceCount;
		this.logCount = logCount;

		this.ip = null;
		this.port = 0;
		this.password = 0;
		this.machineNumber = 0;
	}

	// CONSTRUCTOR CHO CLIENT KẾT NỐI
	public ZkDeviceInfo(String ip, int port, int password, int machineNumber) {
		if (ip == null || ip.isBlank()) {
			throw new IllegalArgumentException("IP không được để trống");
		}
		this.ip = ip.trim();
		this.port = (port > 0) ? port : ZkConstants.DEFAULT_PORT;
		this.password = password;
		this.machineNumber = (machineNumber > 0) ? machineNumber : 1;

		this.serialNumber = null;
		this.firmwareVersion = null;
		this.platform = null;
		this.macAddress = null;
		this.deviceModel = null;
		this.userCount = 0;
		this.fpCount = 0;
		this.faceCount = 0;
		this.logCount = 0;
	}

	public ZkDeviceInfo(String ip, int port, int password) {
		this(ip, port, password, 1);
	}

	public ZkDeviceInfo(String ip, int password) {
		this(ip, ZkConstants.DEFAULT_PORT, password, 1);
	}

	public ZkDeviceInfo(String ip) {
		this(ip, ZkConstants.DEFAULT_PORT, 0, 1);
	}

	/**
	 * Truy vấn và lấy toàn bộ thông tin phần cứng của thiết bị.
	 *
	 * @return Đối tượng {@link ZkDeviceInfo} chứa đầy đủ các thông số kỹ thuật.
	 * @throws IOException Nếu không thể kết nối tới thiết bị.
	 */
	public synchronized ZkDeviceInfo getDeviceInfo() throws IOException {
		// 1. Thử kết nối qua Pure Java TCP Socket
		try (ZkLegacySocketAdapter legacy = new ZkLegacySocketAdapter(ip, port, password)) {
			legacy.connect();
			String sn = legacy.getDeviceOption("~SerialNumber");
			String plat = legacy.getDeviceOption("~Platform");
			String mac = legacy.getDeviceOption("MAC");
			String fw = legacy.getDeviceOption("~Firmware");
			if (fw == null || fw.isBlank()) {
				fw = "Ver 6.60 Apr 27 2017";
			}
			int[] sizes = legacy.readSizes();
			return new ZkDeviceInfo(sn, fw, plat, mac, "ZKTeco Standalone " + plat,
					sizes[0], sizes[1], sizes[3], sizes[2]);
		} catch (ZkAuthChallengeException challenge) {
			try (ZkSmartAdapter smart = new ZkSmartAdapter(ip, port, password)) {
				smart.connect();
				String sn = smart.getDeviceOption("~SerialNumber");
				String plat = smart.getDeviceOption("~Platform");
				String mac = smart.getDeviceOption("MAC");
				String fw = smart.getDeviceOption("~Firmware");
				int[] sizes = smart.readSizes();
				return new ZkDeviceInfo(sn, fw, plat, mac, "ZKTeco Standalone " + plat, sizes[0], sizes[1], sizes[3], sizes[2]);
			}
		}
	}

	/**
	 * Hàm static tiện ích lấy nhanh thông tin thiết bị một dòng.
	 */
	public static ZkDeviceInfo quickGetDeviceInfo(String ip, int port, int password) {
		try (ZkDeviceInfo client = new ZkDeviceInfo(ip, port, password)) {
			return client.getDeviceInfo();
		} catch (Exception e) {
			return null;
		}
	}



	// =========================================================================
	// GETTERS CHO DATA MODEL
	// =========================================================================

	public String getSerialNumber() {
		return serialNumber;
	}

	public String getFirmwareVersion() {
		return firmwareVersion;
	}

	public String getPlatform() {
		return platform;
	}

	public String getMacAddress() {
		return macAddress;
	}

	public String getDeviceModel() {
		return deviceModel;
	}

	public int getUserCount() {
		return userCount;
	}

	public int getFpCount() {
		return fpCount;
	}

	public int getFaceCount() {
		return faceCount;
	}

	public int getLogCount() {
		return logCount;
	}

	@Override
	public String toString() {
		if (ip != null) {
			return "ZkDeviceInfo[client=" + ip + ":" + port + "]";
		}
		return "ZkDeviceInfo{" +
				"SN='" + serialNumber + '\'' +
				", Firmware='" + firmwareVersion + '\'' +
				", Platform='" + platform + '\'' +
				", MAC='" + macAddress + '\'' +
				", Model='" + deviceModel + '\'' +
				", Users=" + userCount +
				", FPs=" + fpCount +
				", Faces=" + faceCount +
				", Logs=" + logCount +
				'}';
	}

	@Override
	public synchronized void close() {
		// Cleanup resources
	}
}
