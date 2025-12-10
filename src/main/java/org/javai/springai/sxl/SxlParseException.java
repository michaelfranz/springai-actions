package org.javai.springai.sxl;

/**
 * Exception thrown when parsing S-expression input fails.
 */
public class SxlParseException extends RuntimeException {

	public SxlParseException(String message) {
		super(message);
	}

	public SxlParseException(String message, Throwable cause) {
		super(message, cause);
	}
}

