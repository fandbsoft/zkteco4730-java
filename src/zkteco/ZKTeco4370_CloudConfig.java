package zkteco;

import backendgame.com.core.KeepAttributes;

/**
 * Cấu hình máy chủ đám mây (Cloud Server / ADMS / WebServer) của thiết bị ZKTeco.
 * Dùng để đọc và hiển thị hoặc cập nhật thông số kết nối đám mây qua giao diện Web / API.
 */
public class ZKTeco4370_CloudConfig implements KeepAttributes {
	public String ipServer;
	public int portServer;
	public boolean isHttps;
	public boolean isPushEnabled;
	public boolean isDomainEnabled;

	public ZKTeco4370_CloudConfig() {
		this.ipServer = "";
		this.portServer = 80;
		this.isHttps = false;
		this.isPushEnabled = false;
		this.isDomainEnabled = false;
	}

	public ZKTeco4370_CloudConfig(String ipServer, int portServer, boolean isHttps) {
		this(ipServer, portServer, isHttps, true, false);
	}

	public ZKTeco4370_CloudConfig(String ipServer, int portServer, boolean isHttps, boolean isPushEnabled, boolean isDomainEnabled) {
		this.ipServer = ipServer != null ? ipServer.trim() : "";
		this.portServer = portServer > 0 ? portServer : (isHttps ? 443 : 80);
		this.isHttps = isHttps;
		this.isPushEnabled = isPushEnabled;
		this.isDomainEnabled = isDomainEnabled;
	}

	public String getIpServer() {
		return ipServer;
	}

	public void setIpServer(String ipServer) {
		this.ipServer = ipServer != null ? ipServer.trim() : "";
	}

	public int getPortServer() {
		return portServer;
	}

	public void setPortServer(int portServer) {
		this.portServer = portServer;
	}

	public boolean isHttps() {
		return isHttps;
	}

	public void setHttps(boolean https) {
		isHttps = https;
	}

	public boolean isPushEnabled() {
		return isPushEnabled;
	}

	public void setPushEnabled(boolean pushEnabled) {
		isPushEnabled = pushEnabled;
	}

	public boolean isDomainEnabled() {
		return isDomainEnabled;
	}

	public void setDomainEnabled(boolean domainEnabled) {
		isDomainEnabled = domainEnabled;
	}

	@Override
	public String toString() {
		return "ZKTeco4370_CloudConfig{" +
				"ipServer='" + ipServer + '\'' +
				", portServer=" + portServer +
				", isHttps=" + isHttps +
				", isPushEnabled=" + isPushEnabled +
				", isDomainEnabled=" + isDomainEnabled +
				'}';
	}
}