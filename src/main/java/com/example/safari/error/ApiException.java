package com.example.safari.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String error;

	public ApiException(HttpStatus status, String error, String message) {
		super(message);
		this.status = status;
		this.error = error;
	}

	public HttpStatus status() {
		return status;
	}

	public String error() {
		return error;
	}

	public static ApiException validation(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
	}

	public static ApiException walletNotFound(String walletId) {
		return new ApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", walletId + " does not exist");
	}

	public static ApiException walletNotActive(String walletId) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "WALLET_NOT_ACTIVE", walletId + " is not ACTIVE");
	}

	public static ApiException insufficientFunds(String walletId, long available, long requested) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS",
				walletId + " available " + available + ", requested " + requested);
	}

	public static ApiException busy() {
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Wallet is busy, retry");
	}

}
