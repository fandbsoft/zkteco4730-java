package zkteco;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class UserParser {
	private UserParser() {}

	static List<UserInfo> parse(byte[] rawData) {
		if (rawData == null || rawData.length < 28) {
			return Collections.emptyList();
		}
		int start = detectStartOffset(rawData);
		int remaining = rawData.length - start;
		int recordSize = detectRecordSize(remaining);
		if (recordSize <= 0) {
			return Collections.emptyList();
		}

		List<UserInfo> users = new ArrayList<>();
		int count = remaining / recordSize;
		for (int i = 0; i < count; i++) {
			UserInfo user = recordSize == 72
					? parseRecord72(rawData, start + i * recordSize)
					: parseRecord28(rawData, start + i * recordSize);
			if (user != null && !user.getUserId().isEmpty()) {
				users.add(user);
			}
		}
		return users;
	}

	private static int detectStartOffset(byte[] rawData) {
		if (rawData.length >= 32) {
			int header = RecordParser.readInt32LE(rawData, 0);
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

	private static UserInfo parseRecord72(byte[] data, int offset) {
		if (offset + 72 > data.length) {
			return null;
		}
		int uid = RecordParser.readUInt16LE(data, offset);
		String name = readNullTerminated(data, offset + 11, 24, StandardCharsets.UTF_8);
		String pin = readNullTerminated(data, offset + 48, 24, StandardCharsets.US_ASCII);
		if (pin.isEmpty() && uid > 0) {
			pin = String.valueOf(uid);
		}
		int rawPrivilege = data[offset + 2] & 0xFF;
		int privilege = rawPrivilege == 0x0E ? 3 : rawPrivilege;
		return new UserInfo(pin, name, null, privilege, true);
	}

	private static UserInfo parseRecord28(byte[] data, int offset) {
		if (offset + 28 > data.length) {
			return null;
		}
		int uid = RecordParser.readUInt16LE(data, offset);
		String name = readNullTerminated(data, offset + 11, 8, StandardCharsets.UTF_8);
		int rawPrivilege = data[offset + 2] & 0xFF;
		int privilege = rawPrivilege == 0x0E ? 3 : rawPrivilege;
		return new UserInfo(String.valueOf(uid), name, null, privilege, true);
	}

	private static String readNullTerminated(byte[] data, int offset, int maxLen, java.nio.charset.Charset charset) {
		int end = offset;
		int limit = Math.min(data.length, offset + maxLen);
		while (end < limit && data[end] != 0) {
			end++;
		}
		return end > offset ? new String(data, offset, end - offset, charset).trim() : "";
	}
}
