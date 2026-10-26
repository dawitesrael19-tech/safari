package com.example.safari.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ErrorHandler {

	public record ApiError(String error, String message) {
	}

	private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

	public static ResponseEntity<Object> toResponse(ApiException e) {
		return ResponseEntity.status(e.status()).body(new ApiError(e.error(), e.getMessage()));
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<Object> api(ApiException e) {
		return toResponse(e);
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
			MissingRequestHeaderException.class, HttpMediaTypeNotSupportedException.class })
	ResponseEntity<Object> validation(Exception e) {
		return toResponse(ApiException.validation("Request body or headers are invalid"));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<Object> noRoute(NoResourceFoundException e) {
		return toResponse(new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such endpoint"));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<Object> method(HttpRequestMethodNotSupportedException e) {
		return toResponse(new ApiException(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed"));
	}

	@ExceptionHandler(PessimisticLockingFailureException.class)
	ResponseEntity<Object> lockTimeout(PessimisticLockingFailureException e) {
		log.warn("Lock wait timed out: {}", e.getMessage());
		return toResponse(ApiException.busy());
	}

	@ExceptionHandler({ DataAccessResourceFailureException.class, TransientDataAccessResourceException.class })
	ResponseEntity<Object> databaseDown(Exception e) {
		log.warn("Database unavailable: {}", e.getMessage());
		return toResponse(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Database is unavailable"));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> unexpected(Exception e) {
		log.error("Unexpected error", e);
		return toResponse(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Unexpected error"));
	}

}
