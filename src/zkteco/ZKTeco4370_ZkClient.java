package zkteco;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Production-ready pure Java client for ZKTeco standalone devices over TCP 4370.
 * <p>
 * The client auto-detects legacy pull firmware and newer secure SenseFace/Linux
 * firmware, then exposes one compact API for device info, users, attendance
 * logs, range filtering, and door unlock.
 */
public class ZKTeco4370_ZkClient implements AutoCloseable {
	private final String host;
	private final int port;
	private final int password;
	private final String passwordText;
	private final int connectTimeoutMs;
	private final int readTimeoutMs;

	private Socket socket;
	private InputStream in;
	private OutputStream out;
	private int sessionId;
	private int replyId;
	private boolean secureMode;
	private byte[] aesKey;
	private boolean connected;
	private boolean closed = true;
	private ZKTeco4370_ProtocolMode protocolMode = ZKTeco4370_ProtocolMode.UNKNOWN;
	private List<ZKTeco4370_AttendanceLog> attendanceLogCache;
	private boolean attendanceLogCacheEnabled;
	private boolean bulkReadSinceConnect;

	public ZKTeco4370_ZkClient(String host, int port, int password) {
		this(host, port, password, ZKTeco4370_ZkConstants.DEFAULT_CONNECT_TIMEOUT_MS, ZKTeco4370_ZkConstants.DEFAULT_READ_TIMEOUT_MS);
	}

	public ZKTeco4370_ZkClient(String host, int password) {
		this(host, ZKTeco4370_ZkConstants.DEFAULT_PORT, password);
	}

	public ZKTeco4370_ZkClient(String host) {
		this(host, ZKTeco4370_ZkConstants.DEFAULT_PORT, 0);
	}

	public ZKTeco4370_ZkClient(String host, int port, int password, int connectTimeoutMs, int readTimeoutMs) {
		if (host == null || host.isBlank()) {
			throw new IllegalArgumentException("Device IP/host must not be blank");
		}
		if (port <= 0 || port > 65_535) {
			throw new IllegalArgumentException("Invalid TCP port: " + port);
		}
		this.host = host.trim();
		if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
			throw new IllegalArgumentException("Connection and read timeouts must be positive");
		}
		this.port = port;
		this.password = password;
		this.passwordText = Integer.toString(password);
		this.connectTimeoutMs = connectTimeoutMs;
		this.readTimeoutMs = readTimeoutMs;
	}

	public synchronized void connect() throws IOException {
		if (isConnected()) {
			return;
		}
		try {
			openSocket();
			sendPacket(ZKTeco4370_ZkConstants.CMD_CONNECT, new byte[0], false);
			ZKTeco4370_ZkPacket response = receivePacket();
			this.sessionId = response.getSessionId();

			int code = response.getCommandId();
			if (code == ZKTeco4370_ZkConstants.CMD_ACK_OK) {
				this.protocolMode = ZKTeco4370_ProtocolMode.LEGACY_PULL;
				this.connected = true;
				return;
			}
			if (code == ZKTeco4370_ZkConstants.CMD_ACK_UNAUTH) {
				authenticateLegacy();
				this.protocolMode = ZKTeco4370_ProtocolMode.LEGACY_PULL;
				this.connected = true;
				return;
			}
			if (code == ZKTeco4370_ZkConstants.CMD_ACK_CHALLENGE_6001) {
				negotiateSecureSession();
				authenticateSecureSession();
				this.protocolMode = ZKTeco4370_ProtocolMode.SECURE_PULL;
				this.connected = true;
				return;
			}
			if (code == ZKTeco4370_ZkConstants.CMD_ACK_AUTH_LOCK) {
				throw new ZKTeco4370_ZkException("Device requires secure/standalone pull authentication but did not issue a public-key challenge", code);
			}
			throw new ZKTeco4370_ZkException("Unexpected response to CMD_CONNECT", code);
		} catch (IOException | RuntimeException ex) {
			closeSocketOnly();
			throw ex;
		}
	}

	public synchronized ZKTeco4370_DeviceInfo getDeviceInfo() throws IOException {
		ensureConnected();
		String serialNumber = getDeviceOption("~SerialNumber");
		String deviceModel = firstNonBlank(getDeviceOption("~DeviceName"), getDeviceOption("DeviceName"));
		String platform = firstNonBlank(getDeviceOption("~Platform"), getDeviceOption("Platform"));
		String macAddress = getDeviceOption("MAC");
		String firmwareVersion = firstNonBlank(getFirmwareVersion(), getDeviceOption("~Firmware"));

		int[] sizes = readSizes();
		return new ZKTeco4370_DeviceInfo(serialNumber, firmwareVersion, platform, macAddress, deviceModel,
				sizes[0], sizes[1], sizes[3], sizes[2], protocolMode);
	}

	public synchronized List<ZKTeco4370_UserInfo> getAllUser() throws IOException {
		ensureConnected();
		List<ZKTeco4370_UserInfo> users = new ArrayList<>();
		ZKTeco4370_UserParser.ZKTeco4370_StreamingParser parser = ZKTeco4370_UserParser.newStreamingParser(users::add);
		streamBufferedPayload(buildUserRequest(), "users", new ZKTeco4370_PayloadHandler() {
			@Override
			public void onStart(int totalSize) {
				parser.start(totalSize);
			}

			@Override
			public void onChunk(byte[] data, int offset, int length) {
				parser.accept(data, offset, length);
			}

			@Override
			public void onFinish() {
				parser.finish();
			}
		});
		return enrichUserCreatedAt(users);
	}

	public synchronized List<ZKTeco4370_UserInfo> getAllUserInfo() throws IOException {
		return getAllUser();
	}

	public synchronized ZKTeco4370_UserInfo getUser(String userId) throws IOException {
		if (userId == null || userId.isBlank()) {
			return null;
		}
		String target = userId.trim();
		for (ZKTeco4370_UserInfo user : getAllUser()) {
			if (target.equalsIgnoreCase(user.getUserId())) {
				return user;
			}
		}
		return null;
	}

	public synchronized List<ZKTeco4370_AttendanceLog> getAllLog() throws IOException {
		if (attendanceLogCacheEnabled && attendanceLogCache != null) {
			return new ArrayList<>(attendanceLogCache);
		}
		List<ZKTeco4370_AttendanceLog> logs = new ArrayList<>();
		streamAllLog(logs::add);
		if (attendanceLogCacheEnabled) {
			attendanceLogCache = new ArrayList<>(logs);
		}
		return logs;
	}

	/**
	 * Downloads the user's JPG photo (not a biometric face template).
	 * @param userId userInfo.getUserId(), without the .jpg extension
	 * @return JPG bytes, ready for Files.write or image display
	 * @throws IOException if the photo is unavailable, unsupported, or incomplete
	 */
	public synchronized byte[] downloadUserPhoto(String userId) throws IOException {
		if (userId == null || !userId.matches("[ A-Za-z0-9_-]+")
				|| !userId.trim().matches("[A-Za-z0-9_-]{1,24}")) {
			throw new IllegalArgumentException("userId must contain 1-24 letters, digits, underscores or hyphens");
		}
		ensureInteractiveCommandReady();
		try {
			// Z_DownloadUserPhoto: direct command, NUL-terminated filename.
			sendPacket(10010, (userId.trim() + ".jpg\0").getBytes(StandardCharsets.US_ASCII));
			ZKTeco4370_ZkPacket response = receivePacket();
			byte[] photo;
			if (response.isData()) {
				photo = response.getPayload();
			} else if (response.isPrepareData()) {
				byte[] descriptor = response.payloadView();
				if (descriptor.length < 8) throw new IOException("Invalid photo transfer descriptor");
				int size = readInt32LE(descriptor, 0);
				int chunkSize = readInt32LE(descriptor, 4);
				if (size <= 0 || size > 1024 * 1024 || chunkSize <= 0 || chunkSize > 1024 * 1024) {
					throw new IOException("Invalid photo transfer size");
				}
				photo = new byte[size];
				int count = (size + chunkSize - 1) / chunkSize;
				boolean[] received = new boolean[count];
				int transferReply = response.getReplyId();
				for (int n = 0; n < count; n++) {
					ZKTeco4370_ZkPacket chunk = receivePacket();
					// In this SDK transfer, the session field is the chunk index.
					int index = chunk.getSessionId();
					if (!chunk.isData() || chunk.getReplyId() != transferReply || index >= count || received[index]) {
						throw new IOException("Invalid photo transfer chunk");
					}
					int offset = index * chunkSize;
					if (chunk.getPayloadLength() != Math.min(chunkSize, size - offset)) {
						throw new IOException("Incomplete photo transfer chunk");
					}
					System.arraycopy(chunk.payloadView(), 0, photo, offset, chunk.getPayloadLength());
					received[index] = true;
				}
			} else {
				throw new ZKTeco4370_ZkException("User photo unavailable or download unsupported", response.getCommandId());
			}
			if (photo.length < 4 || photo.length > 1024 * 1024
					|| (photo[0] & 255) != 255 || (photo[1] & 255) != 216
					|| (photo[photo.length - 2] & 255) != 255 || (photo[photo.length - 1] & 255) != 217) {
				throw new IOException("Device did not return a complete JPG photo");
			}
			return photo;
		} finally {
			// Firmware may append a terminal packet; isolate it from subsequent calls.
			// The next operation reconnects automatically and retains the log cache.
			closeSocketOnly();
			closed = true;
		}
	}

	public synchronized List<ZKTeco4370_AttendanceLog> getAttendanceLogs() throws IOException {
		return getAllLog();
	}

	public synchronized List<ZKTeco4370_AttendanceLog> getLogAt(long start, long end) throws IOException {
		List<ZKTeco4370_AttendanceLog> result = new ArrayList<>();
		streamLogAt(start, end, result::add);
		return result;
	}

	public synchronized List<ZKTeco4370_AttendanceLog> getLogAt(LocalDateTime start, LocalDateTime end) throws IOException {
		List<ZKTeco4370_AttendanceLog> result = new ArrayList<>();
		streamLogAt(start, end, result::add);
		return result;
	}

	public synchronized List<ZKTeco4370_AttendanceLog> getLogAt(String sTime, String eTime) throws IOException {
		List<ZKTeco4370_AttendanceLog> result = new ArrayList<>();
		streamLogAt(sTime, eTime, result::add);
		return result;
	}

	/**
	 * Tải các bản ghi chấm công theo khoảng thời gian tương thích chuẩn hàm
	 * {@code ReadTimeGLogData(dwMachineNumber, sTime, eTime)} của zkemkeeper.
	 *
	 * @param sTime Thời gian bắt đầu định dạng "YYYY-MM-DD hh:mm:ss".
	 * @param eTime Thời gian kết thúc định dạng "YYYY-MM-DD hh:mm:ss".
	 * @return Danh sách các bản ghi chấm công trong khoảng thời gian.
	 */
	public synchronized List<ZKTeco4370_AttendanceLog> readTimeGLogData(String sTime, String eTime) throws IOException {
		return getLogAt(sTime, eTime);
	}

	public synchronized void streamAllLog(Consumer<ZKTeco4370_AttendanceLog> consumer) throws IOException {
		Objects.requireNonNull(consumer, "Attendance log consumer must not be null");
		ensureConnected();
		int deviceGmtOffsetMinutes = inferDeviceUtcOffsetMinutesFromClock();
		ZKTeco4370_RecordParser.ZKTeco4370_StreamingParser parser = ZKTeco4370_RecordParser.newStreamingParser(
				log -> consumer.accept(log.withDeviceGmtOffsetMinutes(deviceGmtOffsetMinutes)));
		streamBufferedPayload(buildAttendanceLogRequest(), "attendance logs", new ZKTeco4370_PayloadHandler() {
			@Override
			public void onStart(int totalSize) {
				parser.start(totalSize);
			}

			@Override
			public void onChunk(byte[] data, int offset, int length) {
				parser.accept(data, offset, length);
			}

			@Override
			public void onFinish() {
				parser.finish();
			}
		});
	}

	/**
	 * Streaming log chấm công theo luồng ReadTimeGLogData (CMD_ATTLOG_TIME_RRQ = 10004)
	 * trực tiếp từ phần cứng máy chấm công.
	 */
	public synchronized void streamLogAt(LocalDateTime start, LocalDateTime end, Consumer<ZKTeco4370_AttendanceLog> consumer) throws IOException {
		Objects.requireNonNull(consumer, "Attendance log consumer must not be null");
		ensureConnected();
		int deviceGmtOffsetMinutes = inferDeviceUtcOffsetMinutesFromClock();
		ZoneOffset deviceOffset = ZoneOffset.ofTotalSeconds(deviceGmtOffsetMinutes * 60);

		LocalDateTime startTime = start != null ? start : LocalDateTime.of(2000, 1, 1, 0, 0, 0);
		LocalDateTime endTime = end != null ? end : LocalDateTime.of(2099, 12, 31, 23, 59, 59);

		if (startTime.isAfter(endTime)) {
			LocalDateTime temp = startTime;
			startTime = endTime;
			endTime = temp;
		}

		long minMillis = startTime.toInstant(deviceOffset).toEpochMilli();
		long maxMillis = endTime.toInstant(deviceOffset).toEpochMilli();

		if (attendanceLogCacheEnabled && attendanceLogCache != null) {
			for (ZKTeco4370_AttendanceLog log : attendanceLogCache) {
				long ts = log.getTimestampEpochMilli();
				if (ts >= minMillis && ts <= maxMillis) {
					consumer.accept(log);
				}
			}
			return;
		}

		// 1. Thử gửi lệnh phạm vi thời gian CMD_ATTLOG_TIME_RRQ (10004) chuẩn mã máy zkemkeeper
		try {
			byte[] request = buildRangeAttendanceLogRequest(startTime, endTime);
			ZKTeco4370_RecordParser.ZKTeco4370_StreamingParser parser = ZKTeco4370_RecordParser.newStreamingParser(
					log -> {
						ZKTeco4370_AttendanceLog tagged = log.withDeviceGmtOffsetMinutes(deviceGmtOffsetMinutes);
						long ts = tagged.getTimestampEpochMilli();
						if (ts >= minMillis && ts <= maxMillis) {
							consumer.accept(tagged);
						}
					});
			streamBufferedPayload(request, "range attendance logs", new ZKTeco4370_PayloadHandler() {
				@Override
				public void onStart(int totalSize) {
					parser.start(totalSize);
				}

				@Override
				public void onChunk(byte[] data, int offset, int length) {
					parser.accept(data, offset, length);
				}

				@Override
				public void onFinish() {
					parser.finish();
				}
			});
			return;
		} catch (ZKTeco4370_ZkException ex) {
			int code = ex.getResponseCode();
			if (code != ZKTeco4370_ZkConstants.CMD_ACK_ERROR && code != 65535 && code != 65533 && code != 4989) throw ex;
			// Unsupported command (2001/65535/65533) or failed range buffer: fallback with a full read and filter.
			reconnectPreservingCache();
		}

		streamAllLog(log -> {
			long ts = log.getTimestampEpochMilli();
			if (ts >= minMillis && ts <= maxMillis) {
				consumer.accept(log);
			}
		});
	}

	public synchronized void streamLogAt(long start, long end, Consumer<ZKTeco4370_AttendanceLog> consumer) throws IOException {
		Objects.requireNonNull(consumer, "Attendance log consumer must not be null");
		ensureConnected();
		int deviceGmtOffsetMinutes = inferDeviceUtcOffsetMinutesFromClock();
		ZoneOffset deviceOffset = ZoneOffset.ofTotalSeconds(deviceGmtOffsetMinutes * 60);

		long startMillis = start <= 0 ? Long.MIN_VALUE : toEpochMillis(start);
		long endMillis = end <= 0 ? Long.MAX_VALUE : toEpochMillis(end);
		long min = Math.min(startMillis, endMillis);
		long max = Math.max(startMillis, endMillis);

		LocalDateTime startTime = (min == Long.MIN_VALUE)
				? LocalDateTime.of(2000, 1, 1, 0, 0, 0)
				: LocalDateTime.ofInstant(Instant.ofEpochMilli(min), deviceOffset);
		LocalDateTime endTime = (max == Long.MAX_VALUE)
				? LocalDateTime.of(2099, 12, 31, 23, 59, 59)
				: LocalDateTime.ofInstant(Instant.ofEpochMilli(max), deviceOffset);

		streamLogAt(startTime, endTime, consumer);
	}

	public synchronized void streamLogAt(String sTime, String eTime, Consumer<ZKTeco4370_AttendanceLog> consumer) throws IOException {
		LocalDateTime start = parseFlexibleDateTime(sTime);
		LocalDateTime end = parseFlexibleDateTime(eTime);
		streamLogAt(start, end, consumer);
	}

	private static LocalDateTime parseFlexibleDateTime(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String clean = text.trim();
		DateTimeFormatter[] formatters = new DateTimeFormatter[] {
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
				DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
				DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
				DateTimeFormatter.ofPattern("yyyy-MM-dd"),
				DateTimeFormatter.ofPattern("yyyy/MM/dd"),
				DateTimeFormatter.ISO_LOCAL_DATE_TIME
		};
		for (DateTimeFormatter dtf : formatters) {
			try {
				if (clean.length() <= 10 && !clean.contains("T") && !clean.contains(":")) {
					return java.time.LocalDate.parse(clean, dtf).atStartOfDay();
				}
				return LocalDateTime.parse(clean, dtf);
			} catch (DateTimeParseException ignored) {
			}
		}
		throw new IllegalArgumentException("Invalid date-time format (expected yyyy-MM-dd HH:mm:ss): " + text);
	}

	public synchronized boolean unlock(int delaySeconds) throws IOException {
		ensureInteractiveCommandReady();
		return unlockOnce(delaySeconds);
	}

	private boolean unlockOnce(int delaySeconds) throws IOException {
		int delay = Math.max(1, delaySeconds);
		byte[] payload = new byte[4];
		writeInt32LE(payload, 0, delay);
		sendPacket(ZKTeco4370_ZkConstants.CMD_UNLOCK, payload);
		ZKTeco4370_ZkPacket response = receivePacket();
		return response.isOk();
	}

	public synchronized boolean unlockDoor(int delaySeconds) throws IOException {
		return unlock(delaySeconds);
	}

	public synchronized boolean unlock() throws IOException {
		return unlock(ZKTeco4370_ZkConstants.DEFAULT_UNLOCK_DELAY_SECONDS);
	}

	public synchronized String getDeviceOption(String key) throws IOException {
		ensureInteractiveCommandReady();
		byte[] payload = ((key != null ? key : "") + "\0").getBytes(StandardCharsets.US_ASCII);
		sendPacket(ZKTeco4370_ZkConstants.CMD_OPTIONS_RRQ, payload);
		ZKTeco4370_ZkPacket response = receivePacket();
		if (response.getPayloadLength() == 0) {
			return "";
		}
		String text = new String(response.payloadView(), StandardCharsets.US_ASCII)
				.replace("\0", "")
				.trim();
		int equals = text.indexOf('=');
		return equals >= 0 ? text.substring(equals + 1).trim() : text;
	}

	public synchronized String getFirmwareVersion() throws IOException {
		ensureInteractiveCommandReady();
		sendPacket(ZKTeco4370_ZkConstants.CMD_VERSION, new byte[0]);
		ZKTeco4370_ZkPacket response = receivePacket();
		if (response.getPayloadLength() > 0 && (response.isOk() || response.isData())) {
			return new String(response.payloadView(), StandardCharsets.US_ASCII).replace("\0", "").trim();
		}
		return "";
	}

	/**
	 * Returns the device UTC offset text.
	 * <p>
	 * ZKTeco pull protocol exposes the device wall-clock time, not a reliable
	 * system timezone identifier. This method infers the offset from CMD_GET_TIME.
	 */
	public synchronized String getUTC() throws IOException {
		return formatUtcOffsetText(inferDeviceUtcOffsetMinutesFromClock());
	}

	private static String formatUtcOffsetText(int minutes) {
		int valid = validateGmtOffsetMinutes(minutes);
		int absolute = Math.abs(valid);
		return String.format("UTC%s%02d:%02d", valid >= 0 ? "+" : "-", absolute / 60, absolute % 60);
	}

	/**
	 * Ghi một tham số cấu hình hệ thống xuống thiết bị qua lệnh CMD_OPTIONS_WRQ (12).
	 *
	 * @param key Tên tham số (ví dụ: ~TimeZone, TimeZone, TZ, SDKBuild...).
	 * @param value Giá trị cấu hình cần ghi.
	 * @return true nếu thiết bị chấp nhận cấu hình, false nếu thất bại.
	 */
	public synchronized boolean setDeviceOption(String key, String value) throws IOException {
		if (key == null || key.isBlank()) {
			return false;
		}
		ensureInteractiveCommandReady();
		String item = key.trim() + "=" + (value != null ? value.trim() : "") + "\0";
		byte[] payload = item.getBytes(StandardCharsets.US_ASCII);
		sendPacket(ZKTeco4370_ZkConstants.CMD_OPTIONS_WRQ, payload);
		ZKTeco4370_ZkPacket response = receivePacket();
		return response.isOk();
	}

	/**
	 * Làm mới cấu hình tham số hệ thống trên thiết bị (CMD_REFRESHOPTION = 1014).
	 *
	 * @return true nếu thiết bị phản hồi OK, false nếu thất bại.
	 */
	public synchronized boolean refreshOptions() throws IOException {
		ensureInteractiveCommandReady();
		sendPacket(ZKTeco4370_ZkConstants.CMD_REFRESHOPTION, new byte[0]);
		ZKTeco4370_ZkPacket response = receivePacket();
		return response.isOk();
	}

	/**
	 * Vô hiệu hóa bàn phím và màn hình thiết bị (CMD_DISABLEDEVICE = 1003).
	 * Thường dùng khi đồng bộ dữ liệu lớn hoặc ghi cấu hình/RTC để tránh người dùng thao tác.
	 *
	 * @return true nếu thiết bị phản hồi OK, false nếu thất bại.
	 */
	public synchronized boolean disableDevice() throws IOException {
		ensureInteractiveCommandReady();
		sendPacket(ZKTeco4370_ZkConstants.CMD_DISABLEDEVICE, new byte[0]);
		ZKTeco4370_ZkPacket response = receivePacket();
		return response.isOk();
	}

	/**
	 * Kích hoạt lại thiết bị sau khi hoàn thành thao tác dữ liệu (CMD_ENABLEDEVICE = 1002).
	 *
	 * @return true nếu thiết bị phản hồi OK, false nếu thất bại.
	 */
	public synchronized boolean enableDevice() throws IOException {
		ensureInteractiveCommandReady();
		sendPacket(ZKTeco4370_ZkConstants.CMD_ENABLEDEVICE, new byte[0]);
		ZKTeco4370_ZkPacket response = receivePacket();
		return response.isOk();
	}

	/**
	 * Làm mới bộ đệm dữ liệu trên thiết bị (CMD_REFRESHDATA = 1013).
	 *
	 * @return true nếu thiết bị phản hồi OK, false nếu thất bại.
	 */
	public synchronized boolean refreshData() throws IOException {
		ensureInteractiveCommandReady();
		sendPacket(ZKTeco4370_ZkConstants.CMD_REFRESHDATA, new byte[0]);
		ZKTeco4370_ZkPacket response = receivePacket();
		return response.isOk();
	}

	/**
	 * Đọc thời gian hiển thị trên màn hình của máy chấm công (CMD_GET_TIME = 201).
	 *
	 * @return Thời gian local hiện tại của thiết bị.
	 */
	public synchronized LocalDateTime getDeviceTime() throws IOException {
		return getDeviceLocalTime();
	}

	/**
	 * Đồng bộ thời gian và múi giờ máy chấm công theo thời gian và múi giờ hiện tại của máy tính.
	 * Vừa ghi giờ cục bộ vào chip RTC phần cứng, vừa đồng bộ tham số cấu hình UTC TimeZone.
	 *
	 * @return true nếu đồng bộ thành công, false nếu thất bại.
	 */
	public synchronized boolean syncTime() throws IOException {
		return syncTime(ZonedDateTime.now());
	}

	/**
	 * Đồng bộ thời gian và múi giờ máy chấm công theo thời điểm xác định kèm múi giờ (ZonedDateTime).
	 *
	 * @param zonedDateTime Thời gian và múi giờ cần đồng bộ.
	 * @return true nếu đồng bộ thành công, false nếu thất bại.
	 */
	public synchronized boolean syncTime(ZonedDateTime zonedDateTime) throws IOException {
		Objects.requireNonNull(zonedDateTime, "ZonedDateTime must not be null");
		return syncTime(zonedDateTime.toLocalDateTime(), zonedDateTime.getOffset(), zonedDateTime.getZone());
	}

	/**
	 * Đồng bộ thời gian máy chấm công theo LocalDateTime với múi giờ mặc định của hệ thống.
	 *
	 * @param localTime Thời gian cần cài đặt.
	 * @return true nếu đồng bộ thành công, false nếu thất bại.
	 */
	public synchronized boolean syncTime(LocalDateTime localTime) throws IOException {
		Objects.requireNonNull(localTime, "LocalTime must not be null");
		ZoneId defaultZone = ZoneId.systemDefault();
		ZoneOffset offset = defaultZone.getRules().getOffset(localTime);
		return syncTime(localTime, offset, defaultZone);
	}

	/**
	 * Đồng bộ toàn diện cả thời gian hiển thị (Local Wall-Clock Time) và múi giờ UTC cho máy chấm công.
	 * Luồng thực thi chuẩn zkemkeeper SDK:
	 * 1. Khóa tạm thiết bị (CMD_DISABLEDEVICE = 1003).
	 * 2. Đồng bộ cấu hình múi giờ UTC Offset trước qua CMD_OPTIONS_WRQ (~TimeZone, TimeZone, TZ)
	 *    và làm mới cấu hình (CMD_REFRESHOPTION = 1014) để tránh nhảy giờ khi áp dụng múi giờ.
	 * 3. Ghi thời gian hiển thị cục bộ (CMD_SET_TIME = 202) vào chip RTC sau cùng và làm mới màn hình (CMD_REFRESHDATA = 1013).
	 * 4. Luôn mở khóa lại thiết bị trong khối finally (CMD_ENABLEDEVICE = 1002).
	 *
	 * @param localTime Thời gian cục bộ cài đặt vào máy chấm công.
	 * @param offset Múi giờ UTC offset (ví dụ: UTC+07:00).
	 * @param zoneId Định danh múi giờ (ví dụ: "Asia/Ho_Chi_Minh").
	 * @return true nếu ghi thời gian thành công, false nếu thất bại.
	 */
	public synchronized boolean syncTime(LocalDateTime localTime, ZoneOffset offset, ZoneId zoneId) throws IOException {
		Objects.requireNonNull(localTime, "LocalTime must not be null");
		ensureInteractiveCommandReady();

		try {
			// 1. Tạm khóa thiết bị để ghi cấu hình và chip RTC an toàn
			try {
				disableDevice();
			} catch (Exception ignored) {
			}

			// 2. BƯỚC 1: Đồng bộ cấu hình múi giờ UTC TimeZone TRƯỚC
			// Để thiết bị nạp múi giờ mới vào hệ thống, tránh trường hợp áp dụng timezone sau làm nhảy giờ RTC
			if (offset != null) {
				int totalMinutes = offset.getTotalSeconds() / 60;
				String offsetMinutesStr = String.valueOf(totalMinutes);

				try {
					setDeviceOption("~TimeZone", offsetMinutesStr);
				} catch (Exception ignored) {
				}
				try {
					setDeviceOption("TimeZone", offsetMinutesStr);
				} catch (Exception ignored) {
				}
				if (zoneId != null) {
					try {
						setDeviceOption("TZ", zoneId.getId());
					} catch (Exception ignored) {
					}
				}
				try {
					refreshOptions();
				} catch (Exception ignored) {
				}
			}

			// 3. BƯỚC 2: Ghi thời gian local vào chip RTC SAU CÙNG và làm mới màn hình
			byte[] payload = new byte[4];
			long encoded = ZKTeco4370_TimeCodec.encodeTime(localTime);
			writeInt32LE(payload, 0, (int) encoded);
			sendPacket(ZKTeco4370_ZkConstants.CMD_SET_TIME, payload);
			ZKTeco4370_ZkPacket response = receivePacket();

			if (!response.isOk()) {
				return false;
			}

			try {
				refreshData();
			} catch (Exception ignored) {
			}

			return true;
		} finally {
			// 4. Luôn đảm bảo mở khóa lại thiết bị
			try {
				enableDevice();
			} catch (Exception ignored) {
			}
		}
	}

	/**
	 * Đồng bộ thời gian máy chấm công theo giờ hiện tại của máy tính.
	 *
	 * @deprecated Khuyến nghị sử dụng {@link #syncTime()} để đồng bộ toàn diện cả thời gian và múi giờ UTC.
	 * @return true nếu đồng bộ thành công, false nếu thất bại.
	 */
	@Deprecated
	public synchronized boolean setDeviceTime() throws IOException {
		return syncTime();
	}

	/**
	 * Cài đặt ngày giờ cho máy chấm công qua lệnh CMD_SET_TIME (202).
	 *
	 * @deprecated Khuyến nghị sử dụng {@link #syncTime(LocalDateTime)} để đồng bộ toàn diện cả thời gian và múi giờ UTC.
	 * @param time Thời gian cần cài đặt vào máy chấm công.
	 * @return true nếu thiết bị chấp nhận và cài đặt thành công, false nếu thất bại.
	 */
	@Deprecated
	public synchronized boolean setDeviceTime(LocalDateTime time) throws IOException {
		return syncTime(time);
	}

	public synchronized void clearCache() {
		attendanceLogCache = null;
	}

	public synchronized boolean isAttendanceLogCacheEnabled() {
		return attendanceLogCacheEnabled;
	}

	public synchronized ZKTeco4370_ZkClient setAttendanceLogCacheEnabled(boolean enabled) {
		this.attendanceLogCacheEnabled = enabled;
		if (!enabled) {
			clearCache();
		}
		return this;
	}

	public boolean isConnected() {
		return connected && socket != null && !socket.isClosed() && !closed;
	}

	public ZKTeco4370_ProtocolMode getProtocolMode() {
		return protocolMode;
	}

	public String getProtocolName() {
		return switch (protocolMode) {
			case LEGACY_PULL -> "ZKTeco legacy pull protocol";
			case SECURE_PULL -> "ZKTeco secure pull protocol";
			default -> "Unknown";
		};
	}

	public int getSessionId() {
		return sessionId;
	}

	private void openSocket() throws IOException {
		closeSocketOnly();
		this.closed = false;
		this.connected = false;
		this.secureMode = false;
		this.aesKey = null;
		this.protocolMode = ZKTeco4370_ProtocolMode.UNKNOWN;
		this.attendanceLogCache = null;
		this.bulkReadSinceConnect = false;
		this.sessionId = 0;
		this.replyId = 0;

		socket = new Socket();
		socket.setTcpNoDelay(true);
		socket.setSoTimeout(readTimeoutMs);
		socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
		in = socket.getInputStream();
		out = socket.getOutputStream();
	}

	private void authenticateLegacy() throws IOException {
		int[] ticks = { 50, 0 };
		int lastCode = -1;
		for (int tick : ticks) {
			sendPacket(ZKTeco4370_ZkConstants.CMD_AUTH, ZKTeco4370_ZkCrypto.makeCommKey(password, sessionId, tick), false);
			ZKTeco4370_ZkPacket response = receivePacket();
			if (response.isOk()) {
				return;
			}
			lastCode = response.getCommandId();
			if (response.isAuthChallenge() || response.isAuthLock()) {
				throw new ZKTeco4370_ZkException("Legacy authentication rejected by secure firmware", lastCode);
			}
		}
		throw new ZKTeco4370_ZkException("Legacy Comm Key authentication failed", lastCode);
	}

	private void negotiateSecureSession() throws IOException {
		try {
			KeyPair clientKeyPair = ZKTeco4370_ZkCrypto.generateClientRsaKeyPair();
			String clientPem = ZKTeco4370_ZkCrypto.toPkcs1PublicPem((RSAPublicKey) clientKeyPair.getPublic());
			byte[] clientDmc = ZKTeco4370_ZkCrypto.buildDmcPayload(3, clientPem.getBytes(StandardCharsets.US_ASCII));

			sendPacket(ZKTeco4370_ZkConstants.CMD_CRYPTO_DMC_EXCHANGE, clientDmc, false);
			ZKTeco4370_ZkPacket publicKeyResponse = receivePacket();
			if (!publicKeyResponse.isOk()) {
				throw new ZKTeco4370_ZkException("Secure public-key exchange failed", publicKeyResponse.getCommandId());
			}
			ZKTeco4370_ZkCrypto.ZKTeco4370_DmcMessage deviceKeyMessage = ZKTeco4370_ZkCrypto.parseDmcPayload(publicKeyResponse.payloadView());
			String devicePem = new String(deviceKeyMessage.getBody(), StandardCharsets.US_ASCII);
			RSAPublicKey devicePublicKey = ZKTeco4370_ZkCrypto.parsePkcs1PublicPem(devicePem);

			int clientSecret = ZKTeco4370_ZkCrypto.nextClientSecret();
			byte[] secretPayload = ZKTeco4370_ZkCrypto.buildRsaEncryptedDmcPayload(
					2, ZKTeco4370_ZkCrypto.int32LE(clientSecret), devicePublicKey);
			sendPacket(ZKTeco4370_ZkConstants.CMD_CRYPTO_KEY_EXCHANGE, secretPayload, false);
			ZKTeco4370_ZkPacket secretResponse = receivePacket();
			if (!secretResponse.isOk()) {
				throw new ZKTeco4370_ZkException("Secure session-key exchange failed", secretResponse.getCommandId());
			}
			ZKTeco4370_ZkCrypto.ZKTeco4370_DmcMessage serverSecretMessage = ZKTeco4370_ZkCrypto.parseRsaEncryptedDmcPayload(
					secretResponse.payloadView(), clientKeyPair.getPrivate());
			byte[] serverSecretBytes = serverSecretMessage.getBody();
			if (serverSecretBytes.length < 4) {
				throw new ZKTeco4370_ZkException("Secure session-key response is too short");
			}
			int serverSecret = readInt32LE(serverSecretBytes, 0);
			this.aesKey = ZKTeco4370_ZkCrypto.deriveAesKey(clientSecret, serverSecret);

			sendPacket(ZKTeco4370_ZkConstants.CMD_CRYPTO_CONFIRM_SESSION, new byte[4], false);
			ZKTeco4370_ZkPacket confirmResponse = receivePacket();
			if (!confirmResponse.isOk()) {
				throw new ZKTeco4370_ZkException("Secure session confirmation failed", confirmResponse.getCommandId());
			}
			this.secureMode = true;
			sleepQuietly(50);
		} catch (ZKTeco4370_ZkException e) {
			throw e;
		} catch (Exception e) {
			throw new ZKTeco4370_ZkException("Secure handshake failed", e);
		}
	}

	private void authenticateSecureSession() throws IOException {
		byte[] stage1 = ZKTeco4370_ZkCrypto.makeCommKeyWithCurrentTick(password, sessionId);
		sendPacket(ZKTeco4370_ZkConstants.CMD_AUTH, stage1, true);
		ZKTeco4370_ZkPacket stage1Response = receivePacket();
		if (stage1Response.isOk()) {
			sendSdkBuildMarker();
			return;
		}
		if (stage1Response.isAuthLock()) {
			throw new ZKTeco4370_ZkException("Secure firmware locked CMD_AUTH", stage1Response.getCommandId());
		}

		byte[] stage2 = ZKTeco4370_ZkCrypto.buildExtendedAuthPayload(passwordText);
		sendPacket(ZKTeco4370_ZkConstants.CMD_AUTH_EXT, stage2, true);
		ZKTeco4370_ZkPacket stage2Response = receivePacket();
		if (!stage2Response.isOk()) {
			throw new ZKTeco4370_ZkException("Secure Comm Key authentication failed", stage2Response.getCommandId());
		}
		sendSdkBuildMarker();
	}

	private void sendSdkBuildMarker() throws IOException {
		sendPacket(ZKTeco4370_ZkConstants.CMD_OPTIONS_WRQ, "SDKBuild=1\0".getBytes(StandardCharsets.US_ASCII), true);
		ZKTeco4370_ZkPacket response = receivePacket();
		if (!response.isOk()) {
			throw new ZKTeco4370_ZkException("Device rejected SDKBuild marker", response.getCommandId());
		}
	}

	private int[] readSizes() throws IOException {
		ensureInteractiveCommandReady();
		sendPacket(ZKTeco4370_ZkConstants.CMD_GET_FREE_SIZES, new byte[0]);
		ZKTeco4370_ZkPacket response = receivePacket();
		byte[] payload = response.payloadView();
		if (payload.length < 80) {
			throw new ZKTeco4370_ZkException("Invalid device size response", response.getCommandId());
		}
		int users = readInt32LE(payload, 16);
		int fingers = readInt32LE(payload, 24);
		int logs = readInt32LE(payload, 32);
		int faces = payload.length >= 92 ? readInt32LE(payload, 80) : 0;
		return new int[] { users, fingers, logs, faces };
	}

	private LocalDateTime getDeviceLocalTime() throws IOException {
		ensureInteractiveCommandReady();
		sendPacket(ZKTeco4370_ZkConstants.CMD_GET_TIME, new byte[0]);
		ZKTeco4370_ZkPacket response = receivePacket();
		if (!(response.isOk() || response.isData()) || response.getPayloadLength() < 4) {
			throw new ZKTeco4370_ZkException("Device rejected time read", response.getCommandId());
		}
		LocalDateTime value = ZKTeco4370_TimeCodec.decodeTime(readUInt32LE(response.payloadView(), 0));
		if (value == null) {
			throw new IOException("Device returned an invalid local time payload");
		}
		return value;
	}

	private int inferDeviceUtcOffsetMinutesFromClock() {
		try {
			LocalDateTime deviceTime = getDeviceLocalTime();
			Instant deviceTimeAsUtc = deviceTime.atOffset(ZoneOffset.UTC).toInstant();
			long offsetSeconds = Duration.between(Instant.now(), deviceTimeAsUtc).getSeconds();
			return roundToNearestQuarterHourMinutes(offsetSeconds);
		} catch (Exception e) {
			int defaultOffsetSeconds = ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds();
			return defaultOffsetSeconds / 60;
		}
	}

	private interface ZKTeco4370_PayloadHandler {
		void onStart(int totalSize) throws IOException;
		void onChunk(byte[] data, int offset, int length) throws IOException;
		void onFinish() throws IOException;
	}

	private void streamBufferedPayload(byte[] request, String label, ZKTeco4370_PayloadHandler handler) throws IOException {
		Objects.requireNonNull(handler, "Payload handler must not be null");
		ensureBulkReadReady();
		freeDeviceDataBuffer();
		boolean releaseDeviceBuffer = false;
		try {
			sendPacket(ZKTeco4370_ZkConstants.CMD_DATA_WRRQ, request);
			releaseDeviceBuffer = true;
			ZKTeco4370_ZkPacket response = receivePacket();
			int initSkips = 0;
			while (response.isOk() && response.getPayloadLength() == 0 && ++initSkips <= 3) {
				response = receivePacket();
			}
			if (response.isError() || response.isUnauth() || response.isAuthLock()) {
				throw new ZKTeco4370_ZkException("Device rejected " + label + " read", response.getCommandId());
			}

			byte[] payload = response.payloadView();
			if (response.isData()) {
				validateBulkPayloadSize(label, payload.length);
				handler.onStart(payload.length);
				if (payload.length > 0) {
					handler.onChunk(payload, 0, payload.length);
				}
				handler.onFinish();
				return;
			}
			if (!response.isOk() && !response.isPrepareData()) {
				throw new ZKTeco4370_ZkException("Unexpected " + label + " response", response.getCommandId());
			}
			if (payload.length == 0) {
				handler.onStart(0);
				handler.onFinish();
				return;
			}
			if (payload.length < 4) {
				throw new IOException("Invalid " + label + " prepared-buffer descriptor length: " + payload.length);
			}

			int totalSize = extractPreparedBufferSize(payload);
			validateBulkPayloadSize(label, totalSize);
			handler.onStart(Math.max(0, totalSize));
			if (totalSize <= 0) {
				handler.onFinish();
				return;
			}

			int offset = 0;
			while (offset < totalSize) {
				int chunkSize = Math.min(ZKTeco4370_ZkConstants.DEFAULT_BUFFER_CHUNK_SIZE, totalSize - offset);
				byte[] chunkRequest = new byte[8];
				writeInt32LE(chunkRequest, 0, offset);
				writeInt32LE(chunkRequest, 4, chunkSize);
				sendPacket(ZKTeco4370_ZkConstants.CMD_READ_BUFFER, chunkRequest);
				ZKTeco4370_ZkPacket chunkResponse = receivePacket();
				int prepareSkips = 0;
				while (!chunkResponse.isData() && (chunkResponse.isPrepareData() || (chunkResponse.isOk() && chunkResponse.getPayloadLength() == 0))) {
					if (++prepareSkips > 5) {
						break;
					}
					chunkResponse = receivePacket();
				}
				if (!chunkResponse.isData() && !chunkResponse.isOk()) {
					throw new ZKTeco4370_ZkException("Device rejected " + label + " buffer chunk", chunkResponse.getCommandId());
				}
				byte[] chunk = chunkResponse.payloadView();
				if (chunk.length == 0) {
					throw new EOFException("Device ended " + label + " buffer at " + offset + " / " + totalSize + " bytes");
				}
				if (chunk.length > chunkSize) throw new IOException("Oversized " + label + " buffer chunk");
				int bytesToConsume = chunk.length;
				handler.onChunk(chunk, 0, bytesToConsume);
				offset += bytesToConsume;
			}
			handler.onFinish();
		} finally {
			if (releaseDeviceBuffer) {
				bulkReadSinceConnect = true;
				freeDeviceDataBuffer();
			}
		}
	}

	private static byte[] buildUserRequest() {
		return new byte[] {
				1,
				(byte) (ZKTeco4370_ZkConstants.CMD_USERTEMP_RRQ & 0xFF),
				(byte) ((ZKTeco4370_ZkConstants.CMD_USERTEMP_RRQ >>> 8) & 0xFF),
				(byte) ZKTeco4370_ZkConstants.FCT_USER,
				0, 0, 0, 0, 0, 0, 0
		};
	}

	private static byte[] buildAttendanceLogRequest() {
		byte[] request = new byte[11];
		request[0] = 1;
		request[1] = (byte) (ZKTeco4370_ZkConstants.CMD_ATTLOG_RRQ & 0xFF);
		request[2] = (byte) ((ZKTeco4370_ZkConstants.CMD_ATTLOG_RRQ >>> 8) & 0xFF);
		return request;
	}

	private static byte[] buildRangeAttendanceLogRequest(LocalDateTime startTime, LocalDateTime endTime) {
		byte[] request = new byte[11];
		request[0] = 1;
		request[1] = (byte) (ZKTeco4370_ZkConstants.CMD_ATTLOG_TIME_RRQ & 0xFF);
		request[2] = (byte) ((ZKTeco4370_ZkConstants.CMD_ATTLOG_TIME_RRQ >>> 8) & 0xFF);
		int startVal = (int) ZKTeco4370_TimeCodec.encodeTime(startTime);
		int endVal = (int) ZKTeco4370_TimeCodec.encodeTime(endTime);
		writeInt32LE(request, 3, startVal);
		writeInt32LE(request, 7, endVal);
		return request;
	}

	private static void validateBulkPayloadSize(String label, int totalSize) throws IOException {
		if (totalSize < 0) {
			throw new IOException("Invalid " + label + " payload size: " + totalSize);
		}
		if (totalSize > ZKTeco4370_ZkConstants.MAX_BULK_TRANSFER_SIZE) {
			throw new IOException("Refusing oversized " + label + " payload: " + totalSize + " bytes");
		}
	}

	private List<ZKTeco4370_UserInfo> enrichUserCreatedAt(List<ZKTeco4370_UserInfo> users) {
		if (users == null || users.isEmpty()) {
			return List.of();
		}
		boolean needsCreatedAt = users.stream().anyMatch(user -> user.getCreatedAt() == null);
		if (!needsCreatedAt) {
			return users;
		}
		try {
			Map<String, LocalDateTime> firstLogByUser = new HashMap<>();
			streamAllLog(log -> {
				if (log.getUserId().isEmpty() || log.getTimestamp() == null) {
					return;
				}
				firstLogByUser.merge(log.getUserId(), log.getTimestamp(), (a, b) -> a.isBefore(b) ? a : b);
			});
			List<ZKTeco4370_UserInfo> enriched = new ArrayList<>(users.size());
			for (ZKTeco4370_UserInfo user : users) {
				enriched.add(user.withCreatedAt(firstLogByUser.get(user.getUserId())));
			}
			return enriched;
		} catch (IOException ex) {
			return users;
		}
	}

	private void sendPacket(int commandId, byte[] payload) throws IOException {
		sendPacket(commandId, payload, secureMode);
	}

	private void sendPacket(int commandId, byte[] payload, boolean encrypted) throws IOException {
		if (out == null || closed) {
			throw new IOException("Socket is not open");
		}
		ZKTeco4370_ZkPacket packet = new ZKTeco4370_ZkPacket(commandId, sessionId, replyId, payload);
		byte[] frame;
		if (encrypted) {
			if (aesKey == null) {
				throw new IOException("Secure mode requested before AES key is ready");
			}
			try {
				frame = ZKTeco4370_ZkCrypto.encryptSecureTcpFrame(packet.toZkBytes(), aesKey);
			} catch (Exception e) {
				throw new IOException("Unable to encrypt secure frame", e);
			}
		} else {
			frame = packet.toTcpFrame(ZKTeco4370_ZkConstants.TCP_MAGIC);
		}
		out.write(frame);
		out.flush();
		replyId = (replyId + 1) & 0xFFFF;
	}

	private ZKTeco4370_ZkPacket receivePacket() throws IOException {
		if (in == null || closed) {
			throw new IOException("Socket is not open");
		}
		byte[] tcpHeader = new byte[ZKTeco4370_ZkConstants.TCP_HEADER_SIZE];
		int magic = readTcpHeader(in, tcpHeader);
		int payloadLength = readInt32LE(tcpHeader, 4);
		if (payloadLength < ZKTeco4370_ZkConstants.ZK_HEADER_SIZE || payloadLength > ZKTeco4370_ZkConstants.MAX_FRAME_PAYLOAD_SIZE) {
			throw new IOException("Invalid packet length: " + payloadLength);
		}
		byte[] payload = new byte[payloadLength];
		readFully(in, payload, 0, payloadLength);
		byte[] zkBytes = payload;
		if (magic == ZKTeco4370_ZkConstants.TCP_MAGIC_ALT) {
			try {
				zkBytes = ZKTeco4370_ZkCrypto.decryptSecurePayload(payload, aesKey);
			} catch (Exception e) {
				throw new IOException("Unable to decrypt secure frame", e);
			}
		}
		try {
			return ZKTeco4370_ZkPacket.parseZkBytes(zkBytes, 0, zkBytes.length);
		} catch (IllegalArgumentException ex) {
			closeSocketOnly();
			throw new IOException("Invalid received ZK packet", ex);
		}
	}

	private void ensureConnected() throws IOException {
		if (!isConnected()) {
			connect();
		}
	}

	private void ensureInteractiveCommandReady() throws IOException {
		ensureConnected();
		if (protocolMode == ZKTeco4370_ProtocolMode.SECURE_PULL && bulkReadSinceConnect) {
			reconnectPreservingCache();
		}
	}

	private void ensureBulkReadReady() throws IOException {
		ensureConnected();
		if (protocolMode == ZKTeco4370_ProtocolMode.SECURE_PULL && bulkReadSinceConnect) {
			reconnectPreservingCache();
		}
	}

	private void reconnectPreservingCache() throws IOException {
		List<ZKTeco4370_AttendanceLog> cachedLogs = attendanceLogCache;
		close();
		connect();
		attendanceLogCache = cachedLogs;
	}

	private void freeDeviceDataBuffer() {
		try {
			sendPacket(ZKTeco4370_ZkConstants.CMD_FREE_DATA, new byte[0]);
			receivePacket();
		} catch (Exception ignored) {
		}
	}

	private static int extractPreparedBufferSize(byte[] payload) {
		if (payload.length >= 9 && payload[0] == 0) {
			int size1 = readInt32LE(payload, 1);
			int size2 = readInt32LE(payload, 5);
			if (size1 > 0 && (size1 == size2 || size2 == 0)) {
				return size1;
			}
		}
		return readInt32LE(payload, 0);
	}

	private static int readTcpHeader(InputStream input, byte[] headerDest) throws IOException {
		byte[] window = new byte[4];
		readFully(input, window, 0, 4);
		while (true) {
			if (isMagic(window, 0)) {
				System.arraycopy(window, 0, headerDest, 0, 4);
				readFully(input, headerDest, 4, 4);
				return ((headerDest[0] & 0xFF) << 24)
						| ((headerDest[1] & 0xFF) << 16)
						| ((headerDest[2] & 0xFF) << 8)
						| (headerDest[3] & 0xFF);
			}
			int next = input.read();
			if (next < 0) {
				throw new EOFException("Connection closed while searching for TCP magic header");
			}
			window[0] = window[1];
			window[1] = window[2];
			window[2] = window[3];
			window[3] = (byte) next;
		}
	}

	private static boolean isMagic(byte[] data, int offset) {
		return data != null && offset + 4 <= data.length
				&& data[offset] == 0x50
				&& data[offset + 1] == 0x50
				&& ((data[offset + 2] == (byte) 0x82 && data[offset + 3] == 0x7D)
						|| (data[offset + 2] == (byte) 0x83 && data[offset + 3] == 0x7C));
	}

	private static void readFully(InputStream input, byte[] buffer, int offset, int length) throws IOException {
		int total = 0;
		while (total < length) {
			int read = input.read(buffer, offset + total, length - total);
			if (read < 0) {
				throw new EOFException("Connection closed after reading " + total + " / " + length + " bytes");
			}
			total += read;
		}
	}

	private static int readInt32LE(byte[] data, int offset) {
		return (data[offset] & 0xFF)
				| ((data[offset + 1] & 0xFF) << 8)
				| ((data[offset + 2] & 0xFF) << 16)
				| ((data[offset + 3] & 0xFF) << 24);
	}

	private static long readUInt32LE(byte[] data, int offset) {
		return Integer.toUnsignedLong(readInt32LE(data, offset));
	}

	private static void writeInt32LE(byte[] data, int offset, int value) {
		data[offset] = (byte) (value & 0xFF);
		data[offset + 1] = (byte) ((value >>> 8) & 0xFF);
		data[offset + 2] = (byte) ((value >>> 16) & 0xFF);
		data[offset + 3] = (byte) ((value >>> 24) & 0xFF);
	}

	private static long toEpochMillis(long value) {
		return value > 0 && value < 100_000_000_000L ? value * 1000L : value;
	}

	private static int roundToNearestQuarterHourMinutes(long offsetSeconds) {
		long roundedSeconds = Math.round(offsetSeconds / 900.0) * 900L;
		int minutes = Math.toIntExact(roundedSeconds / 60L);
		return validateGmtOffsetMinutes(minutes);
	}

	private static int validateGmtOffsetMinutes(int minutes) {
		if (minutes < -18 * 60 || minutes > 18 * 60) {
			int defaultOffsetSeconds = ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds();
			return defaultOffsetSeconds / 60;
		}
		ZoneOffset.ofTotalSeconds(minutes * 60);
		return minutes;
	}

	private static String firstNonBlank(String first, String second) {
		return first != null && !first.isBlank() ? first : (second != null ? second : "");
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public synchronized void close() {
		if (closed) {
			return;
		}
		if (connected && socket != null && !socket.isClosed()) {
			try {
				socket.setSoTimeout(1000);
				sendPacket(ZKTeco4370_ZkConstants.CMD_EXIT, new byte[0], secureMode);
			} catch (Exception ignored) {
			}
		}
		closeSocketOnly();
	}

	private void closeSocketOnly() {
		closed = true;
		connected = false;
		secureMode = false;
		bulkReadSinceConnect = false;
		if (aesKey != null) {
			Arrays.fill(aesKey, (byte) 0);
			aesKey = null;
		}
		if (in != null) {
			try { in.close(); } catch (Exception ignored) {}
			in = null;
		}
		if (out != null) {
			try { out.close(); } catch (Exception ignored) {}
			out = null;
		}
		if (socket != null) {
			try { socket.close(); } catch (Exception ignored) {}
			socket = null;
		}
	}
}
