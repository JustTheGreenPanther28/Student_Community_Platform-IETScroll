package com.ietscroll.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a user hits a per-account cap (max active lost/found item
 * requests, max team members, etc.). Maps to 429 TOO MANY REQUESTS, which is
 * a closer semantic fit than 400/409 for "you've hit your quota."
 */
public class LimitExceededException extends ApiException {

	private static final long serialVersionUID = 1L;

	public LimitExceededException(String message) {
		super(message, HttpStatus.TOO_MANY_REQUESTS);
	}
}
