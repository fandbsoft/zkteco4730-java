package main.zk;

import java.io.IOException;

/**
 * Custom exception representing errors encountered during ZKTeco standalone socket communications.
 */
public class ZkException extends IOException {
	private static final long serialVersionUID = 1L;
	private final int responseCode;

	public ZkException(String message) {
		super(message);
		this.responseCode = -1;
	}

	public ZkException(String message, int responseCode) {
		super(message + " (Response code: " + responseCode + ")");
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
