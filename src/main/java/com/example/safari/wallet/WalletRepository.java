package com.example.safari.wallet;

import java.sql.Array;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.safari.error.ApiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WalletRepository {

	public record Wallet(String walletId, String status, long balance, long reserved, long available) {

		public boolean active() {
			return status.equals("ACTIVE");
		}

	}

	public record StatusChange(String walletId, String status, Instant changedAt) {
	}

	private final JdbcTemplate jdbc;

	public WalletRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Wallet> find(String walletId) {
		return jdbc.query("SELECT wallet_id, status, balance, reserved FROM wallets WHERE wallet_id = ?",
				(rs, i) -> wallet(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4)),
				walletId).stream().findFirst();
	}

	public Map<String, Wallet> lock(String... walletIds) {
		Map<String, Wallet> locked = new LinkedHashMap<>();
		jdbc.query(con -> {
			var ps = con.prepareStatement("""
					SELECT wallet_id, status, balance, reserved FROM wallets
					WHERE wallet_id = ANY (?) ORDER BY wallet_id FOR UPDATE
					""");
			Array ids = con.createArrayOf("varchar", walletIds);
			ps.setArray(1, ids);
			return ps;
		}, rs -> {
			locked.put(rs.getString(1), wallet(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4)));
		});
		for (String id : walletIds) {
			if (!locked.containsKey(id)) {
				throw ApiException.walletNotFound(id);
			}
		}
		return locked;
	}

	public void adjust(String walletId, long balanceDelta, long reservedDelta) {
		jdbc.update("UPDATE wallets SET balance = balance + ?, reserved = reserved + ? WHERE wallet_id = ?",
				balanceDelta, reservedDelta, walletId);
	}

	@Transactional
	public Optional<StatusChange> changeStatus(String walletId, String status) {
		jdbc.execute("SET LOCAL lock_timeout = '1500ms'");
		List<StatusChange> changed = jdbc.query("""
				UPDATE wallets SET status = ?, changed_at = now()
				WHERE wallet_id = ? AND status <> ?
				RETURNING wallet_id, status, changed_at
				""", (rs, i) -> statusChange(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()),
				status, walletId, status);
		if (!changed.isEmpty()) {
			return Optional.of(changed.get(0));
		}
		return jdbc.query("SELECT wallet_id, status, changed_at FROM wallets WHERE wallet_id = ?",
				(rs, i) -> statusChange(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()),
				walletId).stream().findFirst();
	}

	@Transactional
	public void resetToSeed() {
		jdbc.execute("TRUNCATE wallets, transfers, payouts, idempotency_keys");
		new ResourceDatabasePopulator(new ClassPathResource("seed.sql")).execute(jdbc.getDataSource());
	}

	private static Wallet wallet(String id, String status, long balance, long reserved) {
		return new Wallet(id, status, balance, reserved, balance - reserved);
	}

	private static StatusChange statusChange(String id, String status, Instant changedAt) {
		return new StatusChange(id, status, changedAt.truncatedTo(ChronoUnit.MILLIS));
	}

}
