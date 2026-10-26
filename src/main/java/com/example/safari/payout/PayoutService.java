package com.example.safari.payout;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.example.safari.error.ApiException;
import com.example.safari.transfer.TransferService;
import com.example.safari.wallet.WalletRepository;
import com.example.safari.wallet.WalletRepository.Wallet;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayoutService {

	public record PayoutRequest(String walletId, String bankAccount, Long amount) {
	}

	public record Accepted(String payoutId, String status) {
	}

	public record PayoutView(String payoutId, String walletId, long amount, String status, String bankRef,
			boolean conflict, Instant updatedAt) {
	}

	private record Row(String walletId, long amount, String state) {
	}

	private final JdbcTemplate jdbc;
	private final WalletRepository wallets;

	public PayoutService(JdbcTemplate jdbc, WalletRepository wallets) {
		this.jdbc = jdbc;
		this.wallets = wallets;
	}

	public Accepted create(PayoutRequest r) {
		TransferService.checkAmount(r.amount());
		if (r.walletId() == null) {
			throw ApiException.validation("wallet_id is required");
		}
		if (r.bankAccount() == null || r.bankAccount().isEmpty() || r.bankAccount().length() > 64) {
			throw ApiException.validation("bank_account must be 1 to 64 characters");
		}

		Wallet wallet = wallets.lock(r.walletId()).get(r.walletId());
		if (!wallet.active()) {
			throw ApiException.walletNotActive(wallet.walletId());
		}
		if (wallet.available() < r.amount()) {
			throw ApiException.insufficientFunds(wallet.walletId(), wallet.available(), r.amount());
		}

		String payoutId = newPayoutId();
		wallets.adjust(wallet.walletId(), 0, r.amount());
		jdbc.update("""
				INSERT INTO payouts (payout_id, wallet_id, bank_account, amount, state, next_attempt_at)
				VALUES (?, ?, ?, ?, 'NEW', now())
				""", payoutId, wallet.walletId(), r.bankAccount(), r.amount());
		return new Accepted(payoutId, "PENDING");
	}

	public PayoutView get(String payoutId) {
		return jdbc.query("""
				SELECT payout_id, wallet_id, amount, state, bank_ref, conflict, updated_at
				FROM payouts WHERE payout_id = ?
				""", (rs, i) -> new PayoutView(rs.getString(1), rs.getString(2), rs.getLong(3),
				publicStatus(rs.getString(4)), rs.getString(5), rs.getBoolean(6),
				rs.getTimestamp(7).toInstant().truncatedTo(ChronoUnit.MILLIS)), payoutId)
				.stream().findFirst().orElseThrow(() -> notFound(payoutId));
	}

	@Transactional
	public void finish(String payoutId, String outcome, String bankRef) {
		jdbc.execute("SET LOCAL lock_timeout = '1500ms'");
		List<Row> found = jdbc.query("SELECT wallet_id, amount, state FROM payouts WHERE payout_id = ? FOR UPDATE",
				(rs, i) -> new Row(rs.getString(1), rs.getLong(2), rs.getString(3)), payoutId);
		if (found.isEmpty()) {
			throw notFound(payoutId);
		}
		Row p = found.get(0);

		if (p.state().equals("SUCCEEDED") || p.state().equals("FAILED")) {
			if (!p.state().equals(outcome)) {
				jdbc.update("UPDATE payouts SET conflict = TRUE, updated_at = now() WHERE payout_id = ?", payoutId);
			}
			return;
		}

		long paid = outcome.equals("SUCCEEDED") ? p.amount() : 0;
		wallets.adjust(p.walletId(), -paid, -p.amount());
		jdbc.update("""
				UPDATE payouts SET state = ?, bank_ref = COALESCE(?, bank_ref),
				       next_attempt_at = NULL, lease_until = NULL, updated_at = now()
				WHERE payout_id = ?
				""", outcome, bankRef, payoutId);
	}

	private static String publicStatus(String state) {
		return state.equals("SUCCEEDED") || state.equals("FAILED") ? state : "PENDING";
	}

	private static ApiException notFound(String payoutId) {
		return new ApiException(HttpStatus.NOT_FOUND, "PAYOUT_NOT_FOUND", payoutId + " does not exist");
	}

	private static String newPayoutId() {
		byte[] bytes = new byte[6];
		ThreadLocalRandom.current().nextBytes(bytes);
		return "P-" + HexFormat.of().formatHex(bytes);
	}

}
