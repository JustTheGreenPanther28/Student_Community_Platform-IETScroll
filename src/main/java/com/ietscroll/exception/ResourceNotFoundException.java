package com.ietscroll.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

	private static final long serialVersionUID = 1L;

	public ResourceNotFoundException(String message) {
		super(message, HttpStatus.NOT_FOUND);
	}
}
