package com.ietscroll.exception;

import java.sql.SQLException;
import java.util.Date;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import jakarta.validation.ConstraintViolationException;

@ControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Object> handleApiException(ApiException exception) {
		ErrorMessage errorMessage = new ErrorMessage(new Date(), exception.getMessage());
		return new ResponseEntity<>(errorMessage, new HttpHeaders(), exception.getStatus());
	}

	@ExceptionHandler(UsernameNotFoundException.class)
	public ResponseEntity<Object> handleUsernameNotFound(UsernameNotFoundException exception) {
		ErrorMessage errorMessage = new ErrorMessage(new Date(), exception.getMessage());
		return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining(", "));
		ErrorMessage errorMessage = new ErrorMessage(new Date(),
				message.isBlank() ? "Invalid request body" : message);
		return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception) {
		ErrorMessage errorMessage = new ErrorMessage(new Date(), exception.getMessage());
		return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.BAD_REQUEST);
	}

	// ── Missing multipart part, e.g. forgetting "image" or "data" ──
	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<Object> handleMissingPart(MissingServletRequestPartException exception) {
		ErrorMessage errorMessage = new ErrorMessage(new Date(),
				"Missing required part: " + exception.getRequestPartName());
		return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Object> handleUnreadable(HttpMessageNotReadableException exception) {
		ErrorMessage errorMessage = new ErrorMessage(new Date(), "Malformed request body.");
		return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.BAD_REQUEST);
	}

	// ── Abstract Database Errors (Prevent raw SQL/Hibernate/Postgres leaks to UI) ──
	@ExceptionHandler({DataAccessException.class, SQLException.class, TransactionException.class})
	public ResponseEntity<Object> handleDatabaseException(Exception exception) {
		log.error("Database error occurred: ", exception);
		ErrorMessage errorMessage = new ErrorMessage(new Date(),
				"A temporary database issue occurred. Please try again in a few moments.");
		return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleOtherException(Exception exception, WebRequest request) {
		log.error("Unhandled exception occurred: ", exception);

		String rawMessage = exception.getMessage();
		String userMessage = "Something went wrong. Please try again.";

		// Sanitize technical messages so internal stack/DB traces are never exposed to the UI
		if (rawMessage != null && !rawMessage.isBlank()) {
			String lower = rawMessage.toLowerCase();
			boolean isTechnical = lower.contains("sql") || lower.contains("hibernate")
					|| lower.contains("prepared statement") || lower.contains("postgres")
					|| lower.contains("exception") || lower.contains("connection")
					|| lower.contains("rollback");

			if (!isTechnical) {
				userMessage = rawMessage;
			}
		}

		ErrorMessage errorMessage = new ErrorMessage(new Date(), userMessage);
		return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
	}
}
