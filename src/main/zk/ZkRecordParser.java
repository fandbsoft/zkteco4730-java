package main.zk;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bộ giải mã bản ghi chấm công nhị phân ZKTeco chuẩn xác và tối ưu bộ nhớ.
 * <p>
 * Tính năng tối ưu hóa bộ nhớ chuẩn Production:
 * <ul>
 *   <li><b>Streaming Parsing:</b> Giải mã trực tiếp từng bản ghi vào Consumer callback,
 *       không cấp phát ArrayList trung gian khi streaming dữ liệu lớn.</li>
 *   <li><b>Zero-Copy String Extraction:</b> Quét trực tiếp độ dài chuỗi ký tự kết thúc bằng null (\0)
 *       trước khi khởi tạo String, loại bỏ hoàn toàn các đối tượng String rác.</li>
 *   <li><b>Hỗ trợ cấu trúc đa dạng:</b> Bản ghi 40 bytes TFT/Face (với thuật toán tự động nhận diện layout),
 *       36 bytes TFT và 16 bytes Black &amp; White truyền thống.</li>
 * </ul>
 */
public final class ZkRecordParser {
	private ZkRecordParser() {}

	/**
	 * Giải mã dữ liệu nhị phân và đẩy từng bản ghi trực tiếp tới Consumer callback.
	 *
	 * @param rawData Mảng byte dữ liệu nhận từ thiết bị.
	 * @param consumer Callback nhận bản ghi chấm công.
	 */
	public static void parse(byte[] rawData, Consumer<ZkAttendanceLog> consumer) {
		if (rawData == null || rawData.length < 16 || consumer == null) {
			return;
		}

		int startOffset = 0;
		int totalBytes = rawData.length;

		// Kiểm tra 4 bytes đầu có phải header mô tả độ dài hoặc số lượng bản ghi
		if (totalBytes >= 4) {
			int headerVal = readInt32LE(rawData, 0);
			if (headerVal > 0) {
				int remBytes = totalBytes - 4;
				if (remBytes == headerVal * 40 || remBytes == headerVal * 36 || remBytes == headerVal * 16) {
					startOffset = 4;
				} else if (headerVal == remBytes && (remBytes % 40 == 0 || remBytes % 36 == 0 || remBytes % 16 == 0)) {
					startOffset = 4;
				}
			}
		}

		int remaining = totalBytes - startOffset;
		int recordSize = 40; // Mặc định chuẩn 40 bytes TFT
		if (remaining % 40 == 0) {
			recordSize = 40;
		} else if (remaining % 36 == 0) {
			recordSize = 36;
		} else if (remaining % 16 == 0) {
			recordSize = 16;
		} else if (remaining >= 40) {
			recordSize = 40;
		}

		int count = remaining / recordSize;
		for (int i = 0; i < count; i++) {
			int offset = startOffset + i * recordSize;
			ZkAttendanceLog log = parseSingleRecord(rawData, offset, recordSize);
			if (log != null) {
				consumer.accept(log);
			}
		}
	}

	/**
	 * Giải mã dữ liệu nhị phân và trả về danh sách List.
	 */
	public static List<ZkAttendanceLog> parse(byte[] rawData) {
		if (rawData == null || rawData.length < 16) {
			return Collections.emptyList();
		}
		List<ZkAttendanceLog> list = new ArrayList<>();
		parse(rawData, list::add);
		return Collections.unmodifiableList(list);
	}

	/**
	 * Giải mã một bản ghi đơn lẻ tại offset được chỉ định.
	 */
	public static ZkAttendanceLog parseSingleRecord(byte[] data, int offset, int recordSize) {
		if (recordSize == 40) {
			return parseTft40(data, offset);
		} else if (recordSize == 36) {
			return parseTft36(data, offset);
		} else if (recordSize == 16) {
			return parseBw16(data, offset);
		}
		return parseTft40(data, offset);
	}

	/**
	 * Giải mã bản ghi 40-byte TFT / Face với cơ chế tự thích ứng cấu trúc layout.
	 */
	private static ZkAttendanceLog parseTft40(byte[] d, int o) {
		if (o + 40 > d.length) {
			return null;
		}

		ZkAttendanceLog bestLog = null;
		int bestScore = -100;

		// Candidate 1: Chuẩn TFT phổ biến (Timestamp tại offset 27)
		// 0..1: UID (uint16 LE), 2..25: UserID (24 bytes string), 26: verifyMode, 27..30: timestamp, 31: inOutMode, 32..35: workCode
		long t27 = readUInt32LE(d, o + 27);
		LocalDateTime dt27 = ZkTimeUtils.decodeTime(t27);
		if (dt27 != null) {
			int uid = readUInt16LE(d, o);
			String userId = readCleanString(d, o + 2, 24);
			if (userId.isEmpty() && uid > 0) {
				userId = String.valueOf(uid);
			}
			int verifyMode = d[o + 26] & 0xFF;
			int inOutMode = d[o + 31] & 0xFF;
			int workCode = readInt32LE(d, o + 32);
			if (uid <= 0 && !userId.isEmpty()) {
				uid = parseInteger(userId, 0);
			}
			ZkAttendanceLog log1 = new ZkAttendanceLog(userId, uid, verifyMode, inOutMode, dt27, workCode, 40);
			int score = scoreLog(log1);
			if (score > bestScore) {
				bestScore = score;
				bestLog = log1;
			}
		}

		// Candidate 2: Face / TFT Variant (Timestamp tại offset 26)
		// 0..23: UserID (24 bytes string), 24: verifyMode, 25: inOutMode, 26..29: timestamp, 30..33: workCode
		long t26 = readUInt32LE(d, o + 26);
		LocalDateTime dt26 = ZkTimeUtils.decodeTime(t26);
		if (dt26 != null) {
			String userId = readCleanString(d, o, 24);
			int uid = parseInteger(userId, 0);
			int verifyMode = d[o + 24] & 0xFF;
			int inOutMode = d[o + 25] & 0xFF;
			int workCode = readInt32LE(d, o + 30);
			ZkAttendanceLog log2 = new ZkAttendanceLog(userId, uid, verifyMode, inOutMode, dt26, workCode, 40);
			int score = scoreLog(log2);
			if (score > bestScore) {
				bestScore = score;
				bestLog = log2;
			}
		}

		// Candidate 3: Timestamp tại offset 28
		long t28 = readUInt32LE(d, o + 28);
		LocalDateTime dt28 = ZkTimeUtils.decodeTime(t28);
		if (dt28 != null) {
			int uid = readUInt16LE(d, o);
			String userId = readCleanString(d, o + 2, 24);
			if (userId.isEmpty() && uid > 0) {
				userId = String.valueOf(uid);
			}
			int verifyMode = d[o + 26] & 0xFF;
			int inOutMode = d[o + 27] & 0xFF;
			int workCode = readInt32LE(d, o + 32);
			if (uid <= 0 && !userId.isEmpty()) {
				uid = parseInteger(userId, 0);
			}
			ZkAttendanceLog log3 = new ZkAttendanceLog(userId, uid, verifyMode, inOutMode, dt28, workCode, 40);
			int score = scoreLog(log3);
			if (score > bestScore) {
				bestScore = score;
				bestLog = log3;
			}
		}

		if (bestLog != null && bestScore > 0) {
			return bestLog;
		}

		// Quét dự phòng timestamp 4-byte hợp lệ từ offset 20 đến 36
		for (int probe = o + 20; probe <= o + 36; probe++) {
			long t = readUInt32LE(d, probe);
			LocalDateTime dt = ZkTimeUtils.decodeTime(t);
			if (dt != null && dt.getYear() >= 2015 && dt.getYear() <= 2035) {
				int uid = readUInt16LE(d, o);
				String userId = readCleanString(d, o + 2, 24);
				if (userId.isEmpty()) {
					userId = readCleanString(d, o, 24);
				}
				if (userId.isEmpty() && uid > 0) {
					userId = String.valueOf(uid);
				}
				int foundAt = probe - o;
				int verifyMode = (foundAt > 24) ? (d[o + foundAt - 1] & 0xFF) : 0;
				int inOutMode = (foundAt + 4 < 40) ? (d[o + foundAt + 4] & 0xFF) : 0;
				if (uid <= 0 && !userId.isEmpty()) {
					uid = parseInteger(userId, 0);
				}
				return new ZkAttendanceLog(userId, uid, verifyMode, inOutMode, dt, 0, 40);
			}
		}

		return null;
	}

	private static ZkAttendanceLog parseTft36(byte[] d, int o) {
		if (o + 36 > d.length) {
			return null;
		}
		int uid = readUInt16LE(d, o);
		String userId = readCleanString(d, o + 2, 24);
		if (userId.isEmpty() && uid > 0) {
			userId = String.valueOf(uid);
		}
		int verifyMode = d[o + 26] & 0xFF;
		long timeVal = readUInt32LE(d, o + 27);
		LocalDateTime time = ZkTimeUtils.decodeTime(timeVal);
		int inOutMode = d[o + 31] & 0xFF;
		if (uid <= 0 && !userId.isEmpty()) {
			uid = parseInteger(userId, 0);
		}
		return new ZkAttendanceLog(userId, uid, verifyMode, inOutMode, time, 0, 36);
	}

	private static ZkAttendanceLog parseBw16(byte[] d, int o) {
		if (o + 16 > d.length) {
			return null;
		}
		int uid = readUInt16LE(d, o);
		long timeVal = readUInt32LE(d, o + 4);
		LocalDateTime time = ZkTimeUtils.decodeTime(timeVal);
		int inOutMode = d[o + 8] & 0xFF;
		int verifyMode = d[o + 9] & 0xFF;
		String userId = String.valueOf(uid);
		return new ZkAttendanceLog(userId, uid, verifyMode, inOutMode, time, 0, 16);
	}

	private static int scoreLog(ZkAttendanceLog log) {
		if (log == null || log.getTimestamp() == null) {
			return -100;
		}
		int score = 0;
		int y = log.getTimestamp().getYear();
		if (y >= 2020 && y <= 2030) {
			score += 50;
		} else if (y >= 2010 && y <= 2035) {
			score += 20;
		} else {
			return -100;
		}

		String u = log.getUserId();
		if (u != null && !u.isEmpty()) {
			score += 20;
			boolean allDigit = true;
			for (int i = 0; i < u.length(); i++) {
				if (!Character.isDigit(u.charAt(i))) {
					allDigit = false;
					break;
				}
			}
			if (allDigit) {
				score += 15;
			}
		}

		int v = log.getVerifyMode();
		if (v == 1 || v == 15 || v == 2 || v == 3 || v == 4 || v == 0) {
			score += 15;
		}
		int s = log.getInOutMode();
		if (s >= 0 && s <= 5) {
			score += 10;
		}
		return score;
	}

	/**
	 * Đọc chuỗi ký tự UTF-8 không cấp phát mảng tạm (Zero-Copy Search).
	 */
	private static String readCleanString(byte[] d, int offset, int maxLen) {
		int end = offset;
		int limit = Math.min(d.length, offset + maxLen);
		while (end < limit && d[end] != 0) {
			end++;
		}
		int len = end - offset;
		if (len <= 0) {
			return "";
		}
		return new String(d, offset, len, StandardCharsets.UTF_8).trim();
	}

	public static int readInt32LE(byte[] b, int o) {
		return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
	}

	public static long readUInt32LE(byte[] b, int o) {
		return ((long) readInt32LE(b, o)) & 0xFFFFFFFFL;
	}

	public static int readUInt16LE(byte[] b, int o) {
		return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
	}

	private static int parseInteger(String s, int defaultVal) {
		if (s == null || s.isEmpty()) {
			return defaultVal;
		}
		try {
			return Integer.parseInt(s.trim());
		} catch (Exception e) {
			return defaultVal;
		}
	}
}
