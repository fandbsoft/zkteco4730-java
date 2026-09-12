package zkteco;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Attendance transaction record returned by a ZKTeco terminal.
 */
public final class AttendanceLog {
	private final String userId;
	private final int uid;
	private final LocalDateTime timestamp;
	private final int verifyMode;
	private final int inOutMode;
	private final int workCode;
	private final int recordSize;

	public AttendanceLog(String userId, int uid, LocalDateTime timestamp,
			int verifyMode, int inOutMode, int workCode, int recordSize) {
		this.userId = userId != null ? userId.trim() : "";
		this.uid = uid;
		this.timestamp = timestamp;
		this.verifyMode = verifyMode;
		this.inOutMode = inOutMode;
		this.workCode = workCode;
		this.recordSize = recordSize;
	}

	public AttendanceLog(String userId, LocalDateTime timestamp, int verifyMode, int inOutMode, int workCode) {
		this(userId, parseUid(userId), timestamp, verifyMode, inOutMode, workCode, 40);
	}

	public String getUserId() { return userId; }
	public int getUid() { return uid; }
	public LocalDateTime getTimestamp() { return timestamp; }
	public int getVerifyMode() { return verifyMode; }
	public int getInOutMode() { return inOutMode; }
	public int getWorkCode() { return workCode; }
	public int getRecordSize() { return recordSize; }

	public long getTimestampEpochMilli() {
		return timestamp != null ? timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0L;
	}

	public long getTimestampEpochSecond() {
		return timestamp != null ? timestamp.atZone(ZoneId.systemDefault()).toEpochSecond() : 0L;
	}

	public String getVerifyModeName() {
		return switch (verifyMode) {
			case 0 -> "Password";
			case 1 -> "Fingerprint";
			case 2 -> "Card";
			case 3 -> "Password";
			case 4 -> "Face";
			case 15 -> "Face/Palm";
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

	private static int parseUid(String value) {
		try {
			return value == null ? 0 : Integer.parseInt(value.trim());
		} catch (RuntimeException ex) {
			return 0;
		}
	}

	@Override
	public String toString() {
		return "AttendanceLog{" +
				"userId='" + userId + '\'' +
				", uid=" + uid +
				", timestamp=" + timestamp +
				", verifyMode=" + verifyMode +
				", inOutMode=" + inOutMode +
				", workCode=" + workCode +
				", recordSize=" + recordSize +
				'}';
	}
}
