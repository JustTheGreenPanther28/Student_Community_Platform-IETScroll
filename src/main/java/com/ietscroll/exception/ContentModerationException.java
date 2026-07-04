package com.ietscroll.exception;

import org.springframework.http.HttpStatus;

public class ContentModerationException extends ApiException {

	private static final long serialVersionUID = 1L;

	public ContentModerationException(String message) {
		super(message, HttpStatus.UNPROCESSABLE_ENTITY);
	}
}
