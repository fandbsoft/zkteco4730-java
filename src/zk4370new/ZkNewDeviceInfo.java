package zk4370new;

/**
 * Thông tin phần cứng và cấu hình thiết bị ZKTeco Firmware mới.
 */
public class ZkNewDeviceInfo {
	private String serialNumber = "";
	private String firmwareVersion = "";
	private String platform = "";
	private String deviceModel = "";
	private String macAddress = "";
	private int userCount = 0;
	private int fpCount = 0;
	private int faceCount = 0;
	private int logCount = 0;

	public ZkNewDeviceInfo() {}

	public String getSerialNumber() { return serialNumber; }
	public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

	public String getFirmwareVersion() { return firmwareVersion; }
	public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }

	public String getPlatform() { return platform; }
	public void setPlatform(String platform) { this.platform = platform; }

	public String getDeviceModel() { return deviceModel; }
	public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }

	public String getMacAddress() { return macAddress; }
	public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

	public int getUserCount() { return userCount; }
	public void setUserCount(int userCount) { this.userCount = userCount; }

	public int getFpCount() { return fpCount; }
	public void setFpCount(int fpCount) { this.fpCount = fpCount; }

	public int getFaceCount() { return faceCount; }
	public void setFaceCount(int faceCount) { this.faceCount = faceCount; }

	public int getLogCount() { return logCount; }
	public void setLogCount(int logCount) { this.logCount = logCount; }

	@Override
	public String toString() {
		return String.format("ZkNewDeviceInfo[Model=%s, SN=%s, FW=%s, Platform=%s, MAC=%s, Users=%d, Faces=%d, Logs=%d]",
				deviceModel, serialNumber, firmwareVersion, platform, macAddress, userCount, faceCount, logCount);
	}
}
