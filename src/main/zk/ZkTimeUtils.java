package main.zk;

import java.time.LocalDateTime;

/**
 * Utilities for encoding and decoding ZKTeco 32-bit timestamps.
 */
public final class ZkTimeUtils {
	private ZkTimeUtils() {}

	/**
	 * Decodes a 32-bit ZKTeco encoded integer into LocalDateTime.
	 * Returns null if the encoded value does not represent a valid date.
	 */
	public static LocalDateTime decodeTime(long rawTime) {
		if (rawTime <= 0) {
			return null;
		}
		long t = rawTime;
		int second = (int) (t % 60);
		t /= 60;
		int minute = (int) (t % 60);
		t /= 60;
		int hour = (int) (t % 24);
		t /= 24;
		int day = (int) ((t % 31) + 1);
		t /= 31;
		int month = (int) ((t % 12) + 1);
		t /= 12;
		int year = (int) (t + 2000);

		if (year < 2000 || year > 2099 || month < 1 || month > 12 || day < 1 || day > 31
				|| hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59) {
			return null;
		}

		try {
			return LocalDateTime.of(year, month, day, hour, minute, second);
		} catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Encodes a LocalDateTime into a 32-bit ZKTeco timestamp.
	 */
	public static long encodeTime(LocalDateTime dt) {
		if (dt == null) {
			return 0;
		}
		int year = dt.getYear() - 2000;
		int month = dt.getMonthValue() - 1;
		int day = dt.getDayOfMonth() - 1;
		int hour = dt.getHour();
		int minute = dt.getMinute();
		int second = dt.getSecond();

		long datePart = ((long) year * 12 + month) * 31 + day;
		long timePart = (datePart * 24 + hour) * 60 + minute;
		return timePart * 60 + second;
	}
}
