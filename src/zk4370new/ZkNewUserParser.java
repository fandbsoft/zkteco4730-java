package zk4370new;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parser for user records returned by CMD_DATA_WRRQ 1503 / function 5.
 */
public final class ZkNewUserParser {
	private ZkNewUserParser() {}

	public static List<ZkNewUserInfo> parse(byte[] rawData) {
		if (rawData == null || rawData.length < 28) {
			return Collections.emptyList();
		}

		int start = detectStartOffset(rawData);
		int remaining = rawData.length - start;
		int recordSize = detectRecordSize(remaining);
		if (recordSize <= 0) {
			return Collections.emptyList();
		}

		List<ZkNewUserInfo> users = new ArrayList<>();
		int count = remaining / recordSize;
		for (int i = 0; i < count; i++) {
			ZkNewUserInfo user = parseRecord(rawData, start + i * recordSize, recordSize);
			if (user != null && !user.getUserId().isEmpty()) {
				users.add(user);
			}
		}
		return users;
	}

	private static int detectStartOffset(byte[] rawData) {
		if (rawData.length >= 32) {
			int header = readInt32LE(rawData, 0);
			int remaining = rawData.length - 4;
			if (header > 0 && header <= remaining && (header % 72 == 0 || header % 28 == 0)) {
				return 4;
			}
		}
		return 0;
	}

	private static int detectRecordSize(int bytes) {
		if (bytes > 0 && bytes % 72 == 0) {
			return 72;
		}
		if (bytes > 0 && bytes % 28 == 0) {
			return 28;
		}
		return 0;
	}

	private static ZkNewUserInfo parseRecord(byte[] data, int offset, int recordSize) {
		if (recordSize == 72) {
			return parseRecord72(data, offset);
		}
		return parseRecord28(data, offset);
	}

	private static ZkNewUserInfo parseRecord72(byte[] d, int o) {
		if (o + 72 > d.length) {
			return null;
		}
		int uid = readUInt16LE(d, o);
		String name = readNullTerminated(d, o + 11, 24, StandardCharsets.UTF_8);
		String pin = readNullTerminated(d, o + 48, 24, StandardCharsets.US_ASCII);
		if (pin.isEmpty() && uid > 0) {
			pin = String.valueOf(uid);
		}

		int rawPrivilege = d[o + 2] & 0xFF;
		int privilege = rawPrivilege == 0x0E ? 3 : rawPrivilege;
		boolean enabled = true;
		return new ZkNewUserInfo(pin, name, null, privilege, enabled);
	}

	private static ZkNewUserInfo parseRecord28(byte[] d, int o) {
		if (o + 28 > d.length) {
			return null;
		}
		int uid = readUInt16LE(d, o);
		String name = readNullTerminated(d, o + 11, 8, StandardCharsets.UTF_8);
		int rawPrivilege = d[o + 2] & 0xFF;
		int privilege = rawPrivilege == 0x0E ? 3 : rawPrivilege;
		return new ZkNewUserInfo(String.valueOf(uid), name, null, privilege, true);
	}

	private static String readNullTerminated(byte[] d, int offset, int maxLen, java.nio.charset.Charset charset) {
		int end = offset;
		int limit = Math.min(d.length, offset + maxLen);
		while (end < limit && d[end] != 0) {
			end++;
		}
		if (end <= offset) {
			return "";
		}
		return new String(d, offset, end - offset, charset).trim();
	}

	private static int readInt32LE(byte[] b, int o) {
		return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
	}

	private static int readUInt16LE(byte[] b, int o) {
		return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
	}
}
