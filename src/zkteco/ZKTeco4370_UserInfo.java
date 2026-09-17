package zkteco;

import java.time.LocalDateTime;
import java.time.ZoneId;

import backendgame.com.core.KeepAttributes;

/**
 * User record returned by a ZKTeco terminal over port 4370.
 */
public final class ZKTeco4370_UserInfo implements KeepAttributes {
	private final int uid;
	private final String userId;
	private final String name;
	private final String password;
	private final long cardNumber;
	private final int privilege;
	private final boolean enabled;
	private final int group;
	private final int timeZone;
	private final LocalDateTime createdAt;

	public ZKTeco4370_UserInfo(String userId, String name, LocalDateTime createdAt) {
		this(0, userId, name, "", 0L, 0, true, 0, 0, createdAt);
	}

	public ZKTeco4370_UserInfo(String userId, String name, LocalDateTime createdAt, int privilege, boolean enabled) {
		this(0, userId, name, "", 0L, privilege, enabled, 0, 0, createdAt);
	}

	public ZKTeco4370_UserInfo(int uid, String userId, String name, String password, long cardNumber,
			int privilege, boolean enabled, int group, int timeZone, LocalDateTime createdAt) {
		this.uid = uid;
		this.userId = userId != null ? userId.trim() : "";
		this.name = name != null ? name.trim() : "";
		this.password = password != null ? password.trim() : "";
		this.cardNumber = cardNumber;
		this.privilege = privilege;
		this.enabled = enabled;
		this.group = group;
		this.timeZone = timeZone;
		this.createdAt = createdAt;
	}

	public int getUid() { return uid; }
	public String getUserId() { return userId; }
	public String getName() { return name; }
	public String getPassword() { return password; }
	public long getCardNumber() { return cardNumber; }
	public String getCardNumberHex() {
		return cardNumber > 0 ? String.format("%08X", cardNumber) : "";
	}
	public int getPrivilege() { return privilege; }
	public boolean isEnabled() { return enabled; }
	public int getGroup() { return group; }
	public int getTimeZone() { return timeZone; }
	public LocalDateTime getCreatedAt() { return createdAt; }

	public String getPrivilegeName() {
		return switch (privilege) {
			case 0 -> "User";
			case 1 -> "Enroller";
			case 2 -> "Manager";
			case 3, 14 -> "Administrator";
			default -> "Custom(" + privilege + ")";
		};
	}

	public long getCreatedAtEpochMilli() {
		return createdAt != null ? createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0L;
	}

	public long getCreatedAtEpochSecond() {
		return createdAt != null ? createdAt.atZone(ZoneId.systemDefault()).toEpochSecond() : 0L;
	}

	ZKTeco4370_UserInfo withCreatedAt(LocalDateTime value) {
		return new ZKTeco4370_UserInfo(uid, userId, name, password, cardNumber, privilege, enabled, group, timeZone, value);
	}

	@Override
	public String toString() {
		return "ZKTeco4370_UserInfo{" +
				"uid=" + uid +
				", userId='" + userId + '\'' +
				", name='" + name + '\'' +
				", password='" + (password.isEmpty() ? "" : "******") + '\'' +
				", cardNumber=" + cardNumber +
				", privilege=" + privilege + " (" + getPrivilegeName() + ")" +
				", enabled=" + enabled +
				", group=" + group +
				", timeZone=" + timeZone +
				", createdAt=" + createdAt +
				'}';
	}
}