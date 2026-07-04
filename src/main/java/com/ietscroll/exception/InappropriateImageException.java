package com.ietscroll.exception;

import org.springframework.http.HttpStatus;

public class InappropriateImageException extends ApiException {
	private static final long serialVersionUID = -1761799238418095213L;

	public InappropriateImageException(String reason) {
		super("Image rejected due to: " + reason, HttpStatus.UNPROCESSABLE_ENTITY);
	}
}