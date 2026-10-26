package com.example.safari.payout;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PayoutWorker {

	private record Job(String payoutId, String bankAccount, long amount, String state) {
	}

	private static final Logger log = LoggerFactory.getLogger(PayoutWorker.class);

	private final JdbcTemplate jdbc;
	private final BankClient bank;
	private final PayoutService payouts;
	private final ExecutorService threads;
	private final Semaphore freeThreads;

	public PayoutWorker(JdbcTemplate jdbc, BankClient bank, PayoutService payouts,
			@Value("${mpesa.payouts.workers}") int workers) {
		this.jdbc = jdbc;
		this.bank = bank;
		this.payouts = payouts;
		this.threads = Executors.newFixedThreadPool(workers);
		this.freeThreads = new Semaphore(workers);
	}

	@PreDestroy
	void stop() {
		threads.shutdownNow();
	}

	@Scheduled(fixedDelay = 200)
	void poll() {
		int free = freeThreads.availablePermits();
		if (free == 0) {
			return;
		}
		List<Job> jobs;
		try {
			jobs = claim(free);
		} catch (Exception e) {
			log.warn("Could not claim payouts: {}", e.getMessage());
			return;
		}
		for (Job job : jobs) {
			freeThreads.acquireUninterruptibly();
			threads.submit(() -> {
				try {
					process(job);
				} catch (Exception e) {
					log.warn("Payout {} will be retried: {}", job.payoutId(), e.getMessage());
				} finally {
					freeThreads.release();
				}
			});
		}
	}

	private List<Job> claim(int limit) {
		return jdbc.query("""
				UPDATE payouts SET lease_until = now() + interval '20 seconds'
				WHERE payout_id IN (
				    SELECT payout_id FROM payouts
				    WHERE state IN ('NEW', 'SUBMITTED', 'UNKNOWN')
				      AND next_attempt_at <= now()
				      AND (lease_until IS NULL OR lease_until < now())
				    ORDER BY next_attempt_at
				    LIMIT ?
				    FOR UPDATE SKIP LOCKED)
				RETURNING payout_id, bank_account, amount, state
				""", (rs, i) -> new Job(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4)), limit);
	}

	private void process(Job job) {
		if (job.state().equals("NEW")) {
			submit(job);
		} else {
			checkStatus(job);
		}
	}

	private void submit(Job job) {
		BankClient.Answer answer = bank.submit(job.payoutId(), job.bankAccount(), job.amount());
		switch (answer.result()) {
			case "ACCEPTED" -> move(job, "SUBMITTED", answer.bankRef(), 20);
			case "REJECTED" -> payouts.finish(job.payoutId(), "FAILED", null);
			default -> move(job, "UNKNOWN", null, 0);
		}
	}

	private void checkStatus(Job job) {
		String status = bank.status(job.payoutId());
		switch (status) {
			case "SUCCEEDED", "FAILED" -> payouts.finish(job.payoutId(), status, null);
			case "REJECTED" -> payouts.finish(job.payoutId(), "FAILED", null);
			case "NOT_FOUND" -> move(job, "NEW", null, 0);
			default -> move(job, job.state(), null, 3);
		}
	}

	private void move(Job job, String state, String bankRef, int delaySeconds) {
		jdbc.update("""
				UPDATE payouts SET state = ?, bank_ref = COALESCE(?, bank_ref), lease_until = NULL,
				       next_attempt_at = now() + make_interval(secs => ?), updated_at = now()
				WHERE payout_id = ? AND state IN ('NEW', 'SUBMITTED', 'UNKNOWN')
				""", state, bankRef, delaySeconds, job.payoutId());
	}

}
