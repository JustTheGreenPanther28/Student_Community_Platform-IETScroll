package com.ietscroll.exception;

import java.util.Date;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.cloudinary.api.exceptions.ApiException;

import jakarta.validation.ConstraintViolationException;

@ControllerAdvice
public class GlobalExceptionHandler {

	// All our own exceptions carry their correct status already
	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Object> handleApiException(ApiException exception) {
		ErrorMessage errorMessage = new ErrorMessage(new Date(), exception.getMessage());
		return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.BAD_REQUEST);
	}

	// ── Thrown by UserServiceImpl.getUserByEmail() when a lookup outside the
	//     Spring Security auth chain fails to find a user 
	@ExceptionHandler(UsernameNotFoundException.class)
	public ResponseEntity<Object> handleUsernameNotFound(UsernameNotFoundException exception) {
		ErrorMessage errorMessage = new ErrorMessage(new Date(), exception.getMessage());
		return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.NOT_FOUND);
	}

	// ── @Valid failures on @RequestBody (e.g. UserRegisterRequest) ──
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining(", "));
		ErrorMessage errorMessage = new ErrorMessage(new Date(),
				message.isBlank() ? "Invalid request body" : message);
		return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.BAD_REQUEST);
	}

	// ── @RequestParam / @PathVariable constraint failures (e.g. @Min on teamSize) ──
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

	// ── Malformed JSON body ──
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Object> handleUnreadable(HttpMessageNotReadableException exception) {
		ErrorMessage errorMessage = new ErrorMessage(new Date(), "Malformed request body");
		return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.BAD_REQUEST);
	}

	// ── Fallback for genuinely unanticipated failures — still 500, on purpose ──
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleOtherException(Exception exception, WebRequest request) {
		ErrorMessage errorMessage = new ErrorMessage(new Date(), exception.getMessage());
		return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
	}
}

