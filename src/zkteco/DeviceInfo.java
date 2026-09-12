package zkteco;

/**
 * Immutable device summary returned by a ZKTeco terminal.
 */
public final class DeviceInfo {
	private final String serialNumber;
	private final String firmwareVersion;
	private final String platform;
	private final String macAddress;
	private final String deviceModel;
	private final int userCount;
	private final int fpCount;
	private final int faceCount;
	private final int logCount;
	private final ProtocolMode protocolMode;

	public DeviceInfo(String serialNumber, String firmwareVersion, String platform,
			String macAddress, String deviceModel, int userCount, int fpCount,
			int faceCount, int logCount, ProtocolMode protocolMode) {
		this.serialNumber = clean(serialNumber);
		this.firmwareVersion = clean(firmwareVersion);
		this.platform = clean(platform);
		this.macAddress = clean(macAddress);
		this.deviceModel = clean(deviceModel);
		this.userCount = userCount;
		this.fpCount = fpCount;
		this.faceCount = faceCount;
		this.logCount = logCount;
		this.protocolMode = protocolMode != null ? protocolMode : ProtocolMode.UNKNOWN;
	}

	public String getSerialNumber() { return serialNumber; }
	public String getFirmwareVersion() { return firmwareVersion; }
	public String getPlatform() { return platform; }
	public String getMacAddress() { return macAddress; }
	public String getDeviceModel() { return deviceModel; }
	public int getUserCount() { return userCount; }
	public int getFpCount() { return fpCount; }
	public int getFaceCount() { return faceCount; }
	public int getLogCount() { return logCount; }
	public ProtocolMode getProtocolMode() { return protocolMode; }

	private static String clean(String value) {
		return value != null ? value.trim() : "";
	}

	@Override
	public String toString() {
		return "DeviceInfo{" +
				"serialNumber='" + serialNumber + '\'' +
				", firmwareVersion='" + firmwareVersion + '\'' +
				", platform='" + platform + '\'' +
				", macAddress='" + macAddress + '\'' +
				", deviceModel='" + deviceModel + '\'' +
				", userCount=" + userCount +
				", fpCount=" + fpCount +
				", faceCount=" + faceCount +
				", logCount=" + logCount +
				", protocolMode=" + protocolMode +
				'}';
	}
}
