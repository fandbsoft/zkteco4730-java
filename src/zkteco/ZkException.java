package zkteco;

import java.io.IOException;

/**
 * Checked exception for ZKTeco socket protocol failures.
 */
public class ZkException extends IOException {
	private static final long serialVersionUID = 1L;

	private final int responseCode;

	public ZkException(String message) {
		super(message);
		this.responseCode = -1;
	}

	public ZkException(String message, int responseCode) {
		super(message + " (response=" + responseCode + ")");
		this.responseCode = responseCode;
	}

	public ZkException(String message, Throwable cause) {
		super(message, cause);
		this.responseCode = -1;
	}

	public int getResponseCode() {
		return responseCode;
	}
}
