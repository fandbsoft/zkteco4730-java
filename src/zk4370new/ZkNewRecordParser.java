package zk4370new;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Parser for binary attendance records returned by new firmware over the secure 4370 tunnel.
 */
public final class ZkNewRecordParser {
	private ZkNewRecordParser() {}

	public static List<ZkNewAttendanceLog> parse(byte[] rawData) {
		if (rawData == null || rawData.length < 16) {
			return Collections.emptyList();
		}
		List<ZkNewAttendanceLog> logs = new ArrayList<>();
		parse(rawData, logs::add);
		return Collections.unmodifiableList(logs);
	}

	public static void parse(byte[] rawData, Consumer<ZkNewAttendanceLog> consumer) {
		if (rawData == null || rawData.length < 16 || consumer == null) {
			return;
		}

		int startOffset = detectStartOffset(rawData);
		int remaining = rawData.length - startOffset;
		int recordSize = detectRecordSize(remaining);
		int count = remaining / recordSize;
		for (int i = 0; i < count; i++) {
			ZkNewAttendanceLog log = parseSingleRecord(rawData, startOffset + i * recordSize, recordSize);
			if (log != null) {
				consumer.accept(log);
			}
		}
	}

	public static void parseFixedSize(byte[] rawData, int recordSize, Consumer<ZkNewAttendanceLog> consumer) {
		if (rawData == null || consumer == null || recordSize <= 0 || rawData.length < recordSize) {
			return;
		}
		int count = rawData.length / recordSize;
		for (int i = 0; i < count; i++) {
			ZkNewAttendanceLog log = parseSingleRecord(rawData, i * recordSize, recordSize);
			if (log != null) {
				consumer.accept(log);
			}
		}
	}

	public static int detectRecordSize(int totalBytes) {
		if (totalBytes > 0 && totalBytes % 40 == 0) {
			return 40;
		}
		if (totalBytes > 0 && totalBytes % 36 == 0) {
			return 36;
		}
		if (totalBytes > 0 && totalBytes % 16 == 0) {
			return 16;
		}
		return 40;
	}

	public static ZkNewAttendanceLog parseSingleRecord(byte[] data, int offset, int recordSize) {
		if (recordSize == 16) {
			return parseBw16(data, offset);
		}
		if (recordSize == 36) {
			return parseTft36(data, offset);
		}
		return parseTft40(data, offset);
	}

	private static int detectStartOffset(byte[] rawData) {
		if (rawData.length < 20) {
			return 0;
		}
		int header = readInt32LE(rawData, 0);
		int remaining = rawData.length - 4;
		if (header > 0 && (header == remaining || header * 40 == remaining || header * 36 == remaining || header * 16 == remaining)
				&& (remaining % 40 == 0 || remaining % 36 == 0 || remaining % 16 == 0)) {
			return 4;
		}
		return 0;
	}

	private static ZkNewAttendanceLog parseTft40(byte[] d, int o) {
		if (o + 40 > d.length) {
			return null;
		}

		ZkNewAttendanceLog best = null;
		int bestScore = -100;

		ZkNewAttendanceLog c1 = makeCandidate(readCleanString(d, o + 2, 24), readUInt16LE(d, o),
				d[o + 26] & 0xFF, d[o + 31] & 0xFF, readUInt32LE(d, o + 27), readInt32LE(d, o + 32));
		int s1 = score(c1);
		if (s1 > bestScore) {
			best = c1;
			bestScore = s1;
		}

		ZkNewAttendanceLog c2 = makeCandidate(readCleanString(d, o, 24), 0,
				d[o + 24] & 0xFF, d[o + 25] & 0xFF, readUInt32LE(d, o + 26), readInt32LE(d, o + 30));
		int s2 = score(c2);
		if (s2 > bestScore) {
			best = c2;
			bestScore = s2;
		}

		ZkNewAttendanceLog c3 = makeCandidate(readCleanString(d, o + 2, 24), readUInt16LE(d, o),
				d[o + 26] & 0xFF, d[o + 27] & 0xFF, readUInt32LE(d, o + 28), readInt32LE(d, o + 32));
		int s3 = score(c3);
		if (s3 > bestScore) {
			best = c3;
			bestScore = s3;
		}

		if (best != null && bestScore > 0) {
			return best;
		}

		for (int probe = o + 20; probe <= o + 36; probe++) {
			LocalDateTime dt = ZkNewTimeUtils.decodeTime(readUInt32LE(d, probe));
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
				int verifyMode = foundAt > 24 ? d[o + foundAt - 1] & 0xFF : 0;
				int inOutMode = foundAt + 4 < 40 ? d[o + foundAt + 4] & 0xFF : 0;
				return new ZkNewAttendanceLog(userId, dt, verifyMode, inOutMode, 0);
			}
		}
		return null;
	}

	private static ZkNewAttendanceLog parseTft36(byte[] d, int o) {
		if (o + 36 > d.length) {
			return null;
		}
		int uid = readUInt16LE(d, o);
		String userId = readCleanString(d, o + 2, 24);
		if (userId.isEmpty() && uid > 0) {
			userId = String.valueOf(uid);
		}
		return new ZkNewAttendanceLog(userId, ZkNewTimeUtils.decodeTime(readUInt32LE(d, o + 27)),
				d[o + 26] & 0xFF, d[o + 31] & 0xFF, 0);
	}

	private static ZkNewAttendanceLog parseBw16(byte[] d, int o) {
		if (o + 16 > d.length) {
			return null;
		}
		int uid = readUInt16LE(d, o);
		return new ZkNewAttendanceLog(String.valueOf(uid), ZkNewTimeUtils.decodeTime(readUInt32LE(d, o + 4)),
				d[o + 9] & 0xFF, d[o + 8] & 0xFF, 0);
	}

	private static ZkNewAttendanceLog makeCandidate(String userId, int uid, int verifyMode, int inOutMode,
			long rawTime, int workCode) {
		LocalDateTime dt = ZkNewTimeUtils.decodeTime(rawTime);
		if (dt == null) {
			return null;
		}
		String id = userId == null ? "" : userId.trim();
		if (id.isEmpty() && uid > 0) {
			id = String.valueOf(uid);
		}
		return new ZkNewAttendanceLog(id, dt, verifyMode, inOutMode, workCode);
	}

	private static int score(ZkNewAttendanceLog log) {
		if (log == null || log.getTimestamp() == null) {
			return -100;
		}
		int year = log.getTimestamp().getYear();
		if (year < 2010 || year > 2035) {
			return -100;
		}
		int score = year >= 2020 && year <= 2030 ? 50 : 20;
		String userId = log.getUserId();
		if (userId != null && !userId.isEmpty()) {
			score += 20;
			if (userId.chars().allMatch(Character::isDigit)) {
				score += 15;
			}
		}
		int verify = log.getVerifyMode();
		if (verify == 0 || verify == 1 || verify == 2 || verify == 3 || verify == 4 || verify == 15) {
			score += 15;
		}
		int state = log.getInOutMode();
		if (state >= 0 && state <= 5) {
			score += 10;
		}
		return score;
	}

	private static String readCleanString(byte[] d, int offset, int maxLen) {
		int end = offset;
		int limit = Math.min(d.length, offset + maxLen);
		while (end < limit && d[end] != 0) {
			end++;
		}
		if (end <= offset) {
			return "";
		}
		return new String(d, offset, end - offset, StandardCharsets.UTF_8).trim();
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
}
