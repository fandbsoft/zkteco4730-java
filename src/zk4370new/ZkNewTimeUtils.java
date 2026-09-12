package zk4370new;

import java.time.LocalDateTime;

/**
 * Encode/decode ZKTeco packed 32-bit timestamps.
 */
public final class ZkNewTimeUtils {
	private ZkNewTimeUtils() {}

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
		} catch (RuntimeException ex) {
			return null;
		}
	}

	public static long encodeTime(LocalDateTime dt) {
		if (dt == null) {
			return 0;
		}
		int year = dt.getYear() - 2000;
		int month = dt.getMonthValue() - 1;
		int day = dt.getDayOfMonth() - 1;
		long date = ((long) year * 12 + month) * 31 + day;
		return (((date * 24 + dt.getHour()) * 60 + dt.getMinute()) * 60 + dt.getSecond());
	}
}
