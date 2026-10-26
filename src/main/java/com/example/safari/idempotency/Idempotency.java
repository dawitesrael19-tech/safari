package com.example.safari.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

import com.example.safari.error.ApiException;
import com.example.safari.error.ErrorHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class Idempotency {

	private record Stored(String requestHash, int status, String body) {
	}

	private final JdbcTemplate jdbc;
	private final TransactionTemplate tx;
	private final ObjectMapper mapper;

	public Idempotency(JdbcTemplate jdbc, TransactionTemplate tx, ObjectMapper mapper) {
		this.jdbc = jdbc;
		this.tx = tx;
		this.mapper = mapper;
	}

	public ResponseEntity<Object> run(String key, String scope, Object request, Supplier<ResponseEntity<Object>> work) {
		if (key == null || key.isBlank() || key.length() > 64) {
			throw ApiException.validation("Idempotency-Key header is required, at most 64 characters");
		}
		String hash = sha256(scope + ":" + json(request));
		boolean[] keyTaken = new boolean[1];
		try {
			return tx.execute(status -> {
				jdbc.execute("SET LOCAL lock_timeout = '1500ms'");
				int inserted = jdbc.update("""
						INSERT INTO idempotency_keys (idem_key, request_hash) VALUES (?, ?)
						ON CONFLICT (idem_key) DO NOTHING
						""", key, hash);
				keyTaken[0] = true;
				if (inserted == 0) {
					return replay(key, hash);
				}
				ResponseEntity<Object> response;
				try {
					response = work.get();
				} catch (ApiException e) {
					response = ErrorHandler.toResponse(e);
				}
				int code = response.getStatusCode().value();
				String body = json(response.getBody());
				jdbc.update("UPDATE idempotency_keys SET response_status = ?, response_body = ? WHERE idem_key = ?",
						code, body, key);
				return jsonResponse(code, body);
			});
		} catch (PessimisticLockingFailureException e) {
			if (!keyTaken[0]) {
				throw new ApiException(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS",
						"A request with this Idempotency-Key is still running");
			}
			throw ApiException.busy();
		}
	}

	@Scheduled(fixedDelay = 600_000)
	void expireOldKeys() {
		jdbc.update("DELETE FROM idempotency_keys WHERE created_at < now() - interval '24 hours'");
	}

	private ResponseEntity<Object> replay(String key, String hash) {
		List<Stored> rows = jdbc.query(
				"SELECT request_hash, response_status, response_body FROM idempotency_keys WHERE idem_key = ?",
				(rs, i) -> new Stored(rs.getString(1), rs.getInt(2), rs.getString(3)), key);
		Stored stored = rows.get(0);
		if (!stored.requestHash().equals(hash)) {
			throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
					"Idempotency-Key was already used with a different request");
		}
		return jsonResponse(stored.status(), stored.body());
	}

	private static ResponseEntity<Object> jsonResponse(int status, String body) {
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
	}

	private String json(Object value) {
		try {
			return mapper.writeValueAsString(value);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static String sha256(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
