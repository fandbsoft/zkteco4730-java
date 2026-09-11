package main.zk;

/**
 * Raised when a device answers on TCP/4370 but rejects the public pull protocol
 * used by the pure Java socket implementation.
 */
public class ZkUnsupportedProtocolException extends ZkException {
	private static final long serialVersionUID = 1L;

	public ZkUnsupportedProtocolException(String message, int responseCode) {
		super(message, responseCode);
	}
}
