package zkteco;

import java.time.LocalDateTime;

final class ZKTeco4370_TimeCodec {
	private ZKTeco4370_TimeCodec() {}

	static LocalDateTime decodeTime(long rawTime) {
		if (rawTime <= 0) {
			return null;
		}
		long value = rawTime;
		int second = (int) (value % 60);
		value /= 60;
		int minute = (int) (value % 60);
		value /= 60;
		int hour = (int) (value % 24);
		value /= 24;
		int day = (int) ((value % 31) + 1);
		value /= 31;
		int month = (int) ((value % 12) + 1);
		value /= 12;
		int year = (int) (value + 2000);
		if (year < 2000 || year > 2099 || month < 1 || month > 12 || day < 1 || day > 31) {
			return null;
		}
		try {
			return LocalDateTime.of(year, month, day, hour, minute, second);
		} catch (RuntimeException ex) {
			return null;
		}
	}

	static long encodeTime(LocalDateTime value) {
		if (value == null) {
			return 0L;
		}
		long date = ((long) (value.getYear() - 2000) * 12 + value.getMonthValue() - 1) * 31
				+ value.getDayOfMonth() - 1;
		return (((date * 24 + value.getHour()) * 60 + value.getMinute()) * 60 + value.getSecond());
	}
}
