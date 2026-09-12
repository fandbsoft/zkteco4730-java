package zk4370new;

import java.time.LocalDateTime;

/**
 * Bản ghi chấm công chuẩn SSR dành cho các dòng máy Firmware mới (SenseFace / TFT / Linux).
 */
public class ZkNewAttendanceLog {
	private final String userId;
	private final LocalDateTime timestamp;
	private final int verifyMode;
	private final int inOutMode;
	private final int workCode;

	public ZkNewAttendanceLog(String userId, LocalDateTime timestamp, int verifyMode, int inOutMode, int workCode) {
		this.userId = (userId != null) ? userId.trim() : "";
		this.timestamp = timestamp;
		this.verifyMode = verifyMode;
		this.inOutMode = inOutMode;
		this.workCode = workCode;
	}

	public String getUserId() {
		return userId;
	}

	public LocalDateTime getTimestamp() {
		return timestamp;
	}

	public int getVerifyMode() {
		return verifyMode;
	}

	public int getInOutMode() {
		return inOutMode;
	}

	public int getWorkCode() {
		return workCode;
	}

	public String getVerifyModeName() {
		return switch (verifyMode) {
			case 0 -> "Password";
			case 1 -> "Fingerprint";
			case 2 -> "Card";
			case 4 -> "Face (Khuôn mặt)";
			case 15 -> "Face Palm (Lòng bàn tay)";
			default -> "Other (" + verifyMode + ")";
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
			default -> "State (" + inOutMode + ")";
		};
	}

	@Override
	public String toString() {
		return String.format("ZkNewAttendanceLog[User=%s, Time=%s, Verify=%s, InOut=%s, WorkCode=%d]",
				userId, timestamp, getVerifyModeName(), getInOutModeName(), workCode);
	}
}
