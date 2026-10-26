package com.example.safari.transfer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.example.safari.error.ApiException;
import com.example.safari.wallet.WalletRepository;
import com.example.safari.wallet.WalletRepository.Wallet;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

	public record TransferRequest(String fromWallet, String toWallet, Long amount, String reference) {
	}

	public record TransferResponse(String transferId, String status, long fromAvailableAfter, Instant createdAt) {
	}

	public record ReversalResponse(String transferId, String reversalId, String status, long amount) {
	}

	private record Transfer(String fromWallet, String toWallet, long amount, String status) {
	}

	private final JdbcTemplate jdbc;
	private final WalletRepository wallets;

	public TransferService(JdbcTemplate jdbc, WalletRepository wallets) {
		this.jdbc = jdbc;
		this.wallets = wallets;
	}

	public TransferResponse transfer(TransferRequest r) {
		checkAmount(r.amount());
		if (r.fromWallet() == null || r.toWallet() == null) {
			throw ApiException.validation("from_wallet and to_wallet are required");
		}
		if (r.reference() == null || r.reference().isEmpty() || r.reference().length() > 64) {
			throw ApiException.validation("reference must be 1 to 64 characters");
		}
		if (r.fromWallet().equals(r.toWallet())) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SAME_WALLET", "from_wallet and to_wallet are the same");
		}

		Map<String, Wallet> locked = wallets.lock(r.fromWallet(), r.toWallet());
		Wallet from = locked.get(r.fromWallet());
		Wallet to = locked.get(r.toWallet());
		if (!from.active()) {
			throw ApiException.walletNotActive(from.walletId());
		}
		if (!to.active()) {
			throw ApiException.walletNotActive(to.walletId());
		}
		if (from.available() < r.amount()) {
			throw ApiException.insufficientFunds(from.walletId(), from.available(), r.amount());
		}

		wallets.adjust(from.walletId(), -r.amount(), 0);
		wallets.adjust(to.walletId(), r.amount(), 0);
		return jdbc.queryForObject("""
				INSERT INTO transfers (transfer_id, from_wallet, to_wallet, amount, reference, status)
				VALUES ('T-' || nextval('transfer_seq'), ?, ?, ?, ?, 'COMPLETED')
				RETURNING transfer_id, created_at
				""", (rs, i) -> new TransferResponse(rs.getString(1), "COMPLETED", from.available() - r.amount(),
				rs.getTimestamp(2).toInstant().truncatedTo(ChronoUnit.MILLIS)),
				from.walletId(), to.walletId(), r.amount(), r.reference());
	}

	@Transactional
	public ReversalResponse reverse(String transferId, String reason) {
		if (reason != null && reason.length() > 255) {
			throw ApiException.validation("reason must be at most 255 characters");
		}
		jdbc.execute("SET LOCAL lock_timeout = '1500ms'");
		List<Transfer> found = jdbc.query(
				"SELECT from_wallet, to_wallet, amount, status FROM transfers WHERE transfer_id = ? FOR UPDATE",
				(rs, i) -> new Transfer(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4)), transferId);
		if (found.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", transferId + " does not exist");
		}
		Transfer t = found.get(0);
		if (t.status().equals("REVERSED")) {
			throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REVERSED", transferId + " is already reversed");
		}
		if (!t.status().equals("COMPLETED")) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_REVERSIBLE", transferId + " is not COMPLETED");
		}

		Wallet debited = wallets.lock(t.fromWallet(), t.toWallet()).get(t.toWallet());
		if (!debited.active()) {
			throw ApiException.walletNotActive(debited.walletId());
		}
		if (debited.available() < t.amount()) {
			throw ApiException.insufficientFunds(debited.walletId(), debited.available(), t.amount());
		}

		wallets.adjust(t.toWallet(), -t.amount(), 0);
		wallets.adjust(t.fromWallet(), t.amount(), 0);
		String reversalId = jdbc.queryForObject("""
				UPDATE transfers SET status = 'REVERSED', reversal_id = 'R-' || nextval('reversal_seq'),
				       reversal_reason = ?, reversed_at = now()
				WHERE transfer_id = ? RETURNING reversal_id
				""", String.class, reason, transferId);
		return new ReversalResponse(transferId, reversalId, "REVERSED", t.amount());
	}

	public static void checkAmount(Long amount) {
		if (amount == null || amount < 1 || amount > 5_000_000) {
			throw ApiException.validation("amount must be 1 to 5000000");
		}
	}

}
