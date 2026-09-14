package zkteco;

import java.time.LocalDateTime;
import java.time.ZoneId;

import backendgame.com.core.KeepAttributes;

/**
 * User record returned by a ZKTeco terminal.
 */
public final class ZKTeco4370_UserInfo implements KeepAttributes{
	private final String userId;
	private final String name;
	private final LocalDateTime createdAt;
	private final int privilege;
	private final boolean enabled;

	public ZKTeco4370_UserInfo(String userId, String name, LocalDateTime createdAt) {
		this(userId, name, createdAt, 0, true);
	}

	public ZKTeco4370_UserInfo(String userId, String name, LocalDateTime createdAt, int privilege, boolean enabled) {
		this.userId = userId != null ? userId.trim() : "";
		this.name = name != null ? name.trim() : "";
		this.createdAt = createdAt;
		this.privilege = privilege;
		this.enabled = enabled;
	}

	public String getUserId() { return userId; }
	public String getName() { return name; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public int getPrivilege() { return privilege; }
	public boolean isEnabled() { return enabled; }

	public long getCreatedAtEpochMilli() {
		return createdAt != null ? createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0L;
	}

	public long getCreatedAtEpochSecond() {
		return createdAt != null ? createdAt.atZone(ZoneId.systemDefault()).toEpochSecond() : 0L;
	}

	ZKTeco4370_UserInfo withCreatedAt(LocalDateTime value) {
		return new ZKTeco4370_UserInfo(userId, name, value, privilege, enabled);
	}

	@Override
	public String toString() {
		return "ZKTeco4370_UserInfo{" +
				"userId='" + userId + '\'' +
				", name='" + name + '\'' +
				", createdAt=" + createdAt +
				", privilege=" + privilege +
				", enabled=" + enabled +
				'}';
	}
}
