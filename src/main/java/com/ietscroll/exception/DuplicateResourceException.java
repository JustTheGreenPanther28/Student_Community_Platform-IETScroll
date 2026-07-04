package com.ietscroll.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ApiException {

	private static final long serialVersionUID = 1L;

	public DuplicateResourceException(String message) {
		super(message, HttpStatus.CONFLICT);
	}
}
