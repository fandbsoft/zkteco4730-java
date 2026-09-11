package main.zk;

/**
 * Exception thrown when a ZKTeco device rejects legacy communication or signals
 * an authentication challenge (such as response code 6001 or 2032).
 * <p>
 * Signals to {@link ZkClient} to transition to {@link ZkSmartAdapter} for modern
 * encrypted firmware negotiation.
 */
public class ZkAuthChallengeException extends ZkException {
	private static final long serialVersionUID = 1L;

	public ZkAuthChallengeException(String message, int errorCode) {
		super(message, errorCode);
	}

	public ZkAuthChallengeException(int errorCode) {
		super("Device requires modern firmware authentication (Response code: " + errorCode + ")", errorCode);
	}
}
