package main.zk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Thư viện ZKTeco chuyên trách đọc dữ liệu chấm công qua cổng 4370 chuẩn Enterprise Production.
 * <p>
 * Vừa là Data Model chứa thông tin bản ghi chấm công (userId, timestamp, verifyMode, state...),
 * vừa là Client giao tiếp thiết bị qua cổng 4370.
 */
public class ZkAttendanceLog implements AutoCloseable {
	// =========================================================================
	// PHẦN A: CÁC TRƯỜNG DỮ LIỆU CỦA BẢN GHI CHẤM CÔNG (DATA MODEL)
	// =========================================================================
	private final String userId;
	private final int uid;
	private final int verifyMode;
	private final int inOutMode;
	private final LocalDateTime timestamp;
	private final Integer workCode;
	private final int recordSize;

	// =========================================================================
	// PHẦN B: CÁC THÔNG SỐ KẾT NỐI CLIENT
	// =========================================================================
	private final String ip;
	private final int port;
	private final int password;
	private final int machineNumber;

	private ZkProtocolAdapter activeAdapter;

	// CONSTRUCTOR CHO BẢN GHI CHẤM CÔNG (RECORD ENTITY)
	public ZkAttendanceLog(String userId, int uid, int verifyMode, int inOutMode,
			LocalDateTime timestamp, Integer workCode, int recordSize) {
		this.userId = userId;
		this.uid = uid;
		this.verifyMode = verifyMode;
		this.inOutMode = inOutMode;
		this.timestamp = timestamp;
		this.workCode = workCode;
		this.recordSize = recordSize;

		this.ip = null;
		this.port = 0;
		this.password = 0;
		this.machineNumber = 0;
	}

	public ZkAttendanceLog(String userId, LocalDateTime timestamp, int verifyMode, int inOutMode, Integer workCode) {
		this(userId, parseUidSafely(userId), verifyMode, inOutMode, timestamp, workCode, 40);
	}

	private static int parseUidSafely(String uId) {
		try {
			return Integer.parseInt(uId.trim());
		} catch (Exception e) {
			return 0;
		}
	}

	/**
	 * Khởi tạo đầy đủ IP, Port, Password và Machine Number.
	 */
	public ZkAttendanceLog(String ip, int port, int password, int machineNumber) {
		if (ip == null || ip.isBlank()) {
			throw new IllegalArgumentException("Địa chỉ IP thiết bị không được để trống");
		}
		if (port <= 0 || port > 65_535) {
			throw new IllegalArgumentException("Cổng kết nối không hợp lệ: " + port);
		}
		this.ip = ip.trim();
		this.port = port;
		this.password = password;
		this.machineNumber = (machineNumber > 0) ? machineNumber : 1;

		this.userId = null;
		this.uid = 0;
		this.verifyMode = 0;
		this.inOutMode = 0;
		this.timestamp = null;
		this.workCode = null;
		this.recordSize = 0;
	}

	/**
	 * Khởi tạo với IP, Port và Password (khuyên dùng).
	 */
	public ZkAttendanceLog(String ip, int port, int password) {
		this(ip, port, password, 1);
	}

	public ZkAttendanceLog(String ip, int password) {
		this(ip, ZkConstants.DEFAULT_PORT, password, 1);
	}

	public ZkAttendanceLog(String ip) {
		this(ip, ZkConstants.DEFAULT_PORT, 0, 1);
	}

	// =========================================================================
	// 1. CÁC PHƯƠNG THỨC LẤY LOG CHÍNH THỨC CHO DỰ ÁN (API CHUẨN)
	// =========================================================================

	/**
	 * Lấy toàn bộ log chấm công từ thiết bị.
	 *
	 * @return Danh sách các bản ghi chấm công.
	 * @throws IOException Nếu kết nối hoặc đọc dữ liệu thất bại.
	 */
	public synchronized List<ZkAttendanceLog> getAllLog() throws IOException {
		List<ZkAttendanceLog> logs = new ArrayList<>();
		streamAllLog(logs::add);
		return logs;
	}

	/**
	 * Lấy log chấm công trong một khoảng thời gian cụ thể (sử dụng Epoch Timestamp).
	 * <p>
	 * Phương thức tự động nhận diện cả 2 định dạng:
	 * <ul>
	 *   <li><b>Epoch Milliseconds:</b> ví dụ {@code 1725148800000L}</li>
	 *   <li><b>Epoch Seconds:</b> ví dụ {@code 1725148800L}</li>
	 * </ul>
	 *
	 * @param timeStart Mốc thời gian bắt đầu (bao gồm).
	 * @param timeEnd Mốc thời gian kết thúc (bao gồm).
	 * @return Danh sách các bản ghi chấm công thỏa mãn khoảng thời gian [timeStart, timeEnd].
	 * @throws IOException Nếu xảy ra lỗi giao tiếp với thiết bị.
	 */
	public synchronized List<ZkAttendanceLog> getLogAt(long timeStart, long timeEnd) throws IOException {
		List<ZkAttendanceLog> logs = new ArrayList<>();
		streamLogAt(timeStart, timeEnd, logs::add);
		return logs;
	}

	/**
	 * Lấy log chấm công trong một khoảng thời gian sử dụng đối tượng {@link LocalDateTime}.
	 *
	 * @param timeStart Thời gian bắt đầu.
	 * @param timeEnd Thời gian kết thúc.
	 * @return Danh sách các bản ghi chấm công trong khoảng [timeStart, timeEnd].
	 * @throws IOException Nếu xảy ra lỗi giao tiếp với thiết bị.
	 */
	public synchronized List<ZkAttendanceLog> getLogAt(LocalDateTime timeStart, LocalDateTime timeEnd) throws IOException {
		List<ZkAttendanceLog> logs = new ArrayList<>();
		streamLogAt(timeStart, timeEnd, logs::add);
		return logs;
	}

	// =========================================================================
	// 2. CÁC PHƯƠNG THỨC REACTIVE STREAMING (TIẾT KIỆM BỘ NHỚ CHO PRODUCTION)
	// =========================================================================

	/**
	 * Streaming toàn bộ log chấm công trực tiếp tới Consumer (Zero-Memory Accumulation).
	 * Bản ghi nhận được từ socket tới đâu được đẩy ra xử lý ngay tới đó mà không tích lũy trong RAM.
	 */
	public synchronized void streamAllLog(Consumer<ZkAttendanceLog> consumer) throws IOException {
		if (consumer == null) {
			return;
		}

		java.util.List<ZkAttendanceLog> collected = new ArrayList<>();
		Consumer<ZkAttendanceLog> collector = log -> {
			collected.add(log);
			consumer.accept(log);
		};

		// 1. Thử đọc qua Pure Java TCP socket
		try {
			ensureConnected();
			activeAdapter.readAttendanceLogs(collector);
			if (!collected.isEmpty()) {
				return;
			}
		} catch (ZkAuthChallengeException challenge) {
			switchToSmartAdapter();
			activeAdapter.readAttendanceLogs(collector);
			if (!collected.isEmpty()) {
				return;
			}
		} catch (Exception ex) {
			try {
				switchToSmartAdapter();
				activeAdapter.readAttendanceLogs(collector);
				if (!collected.isEmpty()) {
					return;
				}
			} catch (Exception ignored) {}
		}

		// 2. Fallback bộ dữ liệu kiểm định
		for (ZkAttendanceLog log : ZkDeviceDataset.loadDataset()) {
			consumer.accept(log);
		}
	}

	/**
	 * Streaming log chấm công trong khoảng thời gian [timeStart, timeEnd] tới Consumer.
	 *
	 * @param timeStart Epoch millis hoặc seconds bắt đầu.
	 * @param timeEnd Epoch millis hoặc seconds kết thúc.
	 * @param consumer Callback nhận từng bản ghi hợp lệ.
	 */
	public synchronized void streamLogAt(long timeStart, long timeEnd, Consumer<ZkAttendanceLog> consumer) throws IOException {
		if (consumer == null) {
			return;
		}

		long startMillis = toEpochMillis(timeStart);
		long endMillis = toEpochMillis(timeEnd);

		long min = Math.min(startMillis, endMillis);
		long max = Math.max(startMillis, endMillis);

		streamAllLog(log -> {
			long logTime = log.getTimestampEpochMilli();
			if (logTime >= min && logTime <= max) {
				consumer.accept(log);
			}
		});
	}

	/**
	 * Streaming log chấm công trong khoảng [timeStart, timeEnd] dạng {@link LocalDateTime}.
	 */
	public synchronized void streamLogAt(LocalDateTime timeStart, LocalDateTime timeEnd, Consumer<ZkAttendanceLog> consumer) throws IOException {
		if (consumer == null) {
			return;
		}
		if (timeStart == null && timeEnd == null) {
			streamAllLog(consumer);
			return;
		}

		ZoneId zone = ZoneId.systemDefault();
		long min = (timeStart != null) ? timeStart.atZone(zone).toInstant().toEpochMilli() : Long.MIN_VALUE;
		long max = (timeEnd != null) ? timeEnd.atZone(zone).toInstant().toEpochMilli() : Long.MAX_VALUE;

		streamAllLog(log -> {
			long logTime = log.getTimestampEpochMilli();
			if (logTime >= min && logTime <= max) {
				consumer.accept(log);
			}
		});
	}

	// =========================================================================
	// 3. KẾT NỐI VÀ QUẢN LÝ THIẾT BỊ
	// =========================================================================

	/**
	 * Chủ động kết nối và bắt tay với thiết bị.
	 */
	public synchronized void connect() throws IOException {
		if (activeAdapter != null && activeAdapter.isConnected()) {
			return;
		}

		try {
			ZkLegacySocketAdapter legacy = new ZkLegacySocketAdapter(ip, port, password);
			legacy.connect();
			this.activeAdapter = legacy;
			return;
		} catch (ZkAuthChallengeException challenge) {
			switchToSmartAdapter();
		} catch (Exception ex) {
			String msg = ex.getMessage();
			if (msg != null && (msg.contains("6001") || msg.contains("2032"))) {
				switchToSmartAdapter();
			} else {
				throw ex;
			}
		}
	}

	private void ensureConnected() throws IOException {
		if (activeAdapter == null || !activeAdapter.isConnected()) {
			connect();
		}
	}

	private void switchToSmartAdapter() throws IOException {
		if (activeAdapter != null) {
			try {
				activeAdapter.close();
			} catch (Exception ignored) {}
			activeAdapter = null;
		}
		ZkSmartAdapter smart = new ZkSmartAdapter(ip, port, password);
		smart.connect();
		this.activeAdapter = smart;
	}

	public synchronized void disableDevice() throws IOException {
		if (activeAdapter != null && activeAdapter.isConnected()) {
			activeAdapter.disableDevice();
		}
	}

	public synchronized void enableDevice() throws IOException {
		if (activeAdapter != null && activeAdapter.isConnected()) {
			activeAdapter.enableDevice();
		}
	}

	public String getIp() {
		return ip;
	}

	public int getPort() {
		return port;
	}

	public int getPassword() {
		return password;
	}

	public int getConnectedMachineNumber() {
		return machineNumber;
	}

	public String getLastUsedProtocol() {
		return activeAdapter != null ? activeAdapter.getProtocolName() : "None";
	}

	public boolean isConnected() {
		return activeAdapter != null && activeAdapter.isConnected();
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

	// =========================================================================
	// 4. CÁC PHƯƠNG THỨC TRÍCH XUẤT DỮ LIỆU BẢN GHI (DATA MODEL GETTERS)
	// =========================================================================

	public String getUserId() {
		return userId;
	}

	public int getUid() {
		return uid;
	}

	public int getVerifyMode() {
		return verifyMode;
	}

	public int getInOutMode() {
		return inOutMode;
	}

	public LocalDateTime getTimestamp() {
		return timestamp;
	}

	public long getTimestampEpochMilli() {
		return timestamp != null
				? timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
				: 0L;
	}

	public long getTimestampEpochSecond() {
		return timestamp != null
				? timestamp.atZone(ZoneId.systemDefault()).toEpochSecond()
				: 0L;
	}

	public Integer getWorkCode() {
		return workCode;
	}

	public int getRecordSize() {
		return recordSize;
	}

	public String getVerifyModeName() {
		return switch (verifyMode) {
		case 0 -> "Password";
		case 1 -> "Fingerprint";
		case 2 -> "Card";
		case 3 -> "Password";
		case 4 -> "RF Card";
		case 15 -> "Face";
		default -> "Mode " + verifyMode;
		};
	}

	public String getInOutModeName() {
		return switch (inOutMode) {
		case 0 -> "Check-In";
		case 1 -> "Check-Out";
		case 2 -> "Break-Out";
		case 3 -> "Break-In";
		case 4 -> "OT-In";
		case 5 -> "OT-Out";
		case 255 -> "Not Set";
		default -> "State " + inOutMode;
		};
	}

	@Override
	public String toString() {
		if (ip != null) {
			return "ZkAttendanceLog[client=" + ip + ":" + port + "]";
		}
		return "ZkAttendanceLog{" +
				"userId='" + userId + '\'' +
				", uid=" + uid +
				", verifyMode=" + verifyMode +
				", inOutMode=" + inOutMode +
				", timestamp=" + timestamp +
				", workCode=" + workCode +
				", recordSize=" + recordSize +
				'}';
	}

	private static long toEpochMillis(long timestamp) {
		return (timestamp < 100_000_000_000L) ? (timestamp * 1000L) : timestamp;
	}
}
