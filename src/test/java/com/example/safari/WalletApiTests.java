package com.example.safari;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WalletApiTests {

	@Autowired
	TestRestTemplate http;

	@BeforeEach
	void reset() {
		http.postForEntity("/mpesa/internal/reset", null, Void.class);
	}

	@Test
	void transferMovesMoneyBetweenWallets() {
		ResponseEntity<Map> response = transfer("key-1", "W001", "W002", 10000);

		assertThat(response.getStatusCode().value()).isEqualTo(201);
		assertThat(response.getBody().get("status")).isEqualTo("COMPLETED");
		assertThat(response.getBody().get("from_available_after")).isEqualTo(990000);
		assertThat(balance("W001")).isEqualTo(990000);
		assertThat(balance("W002")).isEqualTo(1010000);
	}

	@Test
	void transferWithoutEnoughMoneyChangesNothing() {
		ResponseEntity<Map> response = transfer("key-1", "W001", "W002", 1000001);

		assertThat(response.getStatusCode().value()).isEqualTo(422);
		assertThat(response.getBody().get("error")).isEqualTo("INSUFFICIENT_FUNDS");
		assertThat(balance("W001")).isEqualTo(1000000);
		assertThat(balance("W002")).isEqualTo(1000000);
	}

	@Test
	void transferRulesReturnTheRightErrors() {
		assertThat(transfer("key-1", "W001", "W001", 100).getBody().get("error")).isEqualTo("SAME_WALLET");
		assertThat(transfer("key-2", "W001", "W999", 100).getStatusCode().value()).isEqualTo(404);
		assertThat(transfer("key-3", "W101", "W001", 100).getBody().get("error")).isEqualTo("WALLET_NOT_ACTIVE");
		assertThat(transfer("key-4", "W001", "W002", 0).getStatusCode().value()).isEqualTo(400);
		assertThat(balance("W001")).isEqualTo(1000000);
	}

	@Test
	void sameKeyReplaysAndDifferentBodyConflicts() {
		ResponseEntity<Map> first = transfer("same-key", "W001", "W002", 10000);
		ResponseEntity<Map> again = transfer("same-key", "W001", "W002", 10000);
		ResponseEntity<Map> different = transfer("same-key", "W001", "W002", 20000);

		assertThat(again.getStatusCode().value()).isEqualTo(201);
		assertThat(again.getBody().get("transfer_id")).isEqualTo(first.getBody().get("transfer_id"));
		assertThat(different.getStatusCode().value()).isEqualTo(409);
		assertThat(different.getBody().get("error")).isEqualTo("IDEMPOTENCY_CONFLICT");
		assertThat(balance("W001")).isEqualTo(990000);
	}

	@Test
	void concurrentRequestsWithOneKeyDebitOnce() throws Exception {
		List<ResponseEntity<Map>> responses = runAtOnce(20, i -> transfer("shared-key", "W001", "W002", 10000));

		assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode().value()).isIn(201, 409));
		assertThat(responses.stream().filter(r -> r.getStatusCode().value() == 201)
				.map(r -> r.getBody().get("transfer_id")).distinct()).hasSize(1);
		assertThat(balance("W001")).isEqualTo(990000);
	}

	@Test
	void concurrentTransfersNeverOverdraw() throws Exception {
		List<ResponseEntity<Map>> responses = runAtOnce(150, i -> transfer("k-" + i, "W001", "W00" + (2 + i % 8), 10000));

		long created = responses.stream().filter(r -> r.getStatusCode().value() == 201).count();
		long refused = responses.stream().filter(r -> r.getStatusCode().value() == 422).count();
		assertThat(created).isEqualTo(100);
		assertThat(refused).isEqualTo(50);
		assertThat(balance("W001")).isEqualTo(0);
	}

	@Test
	void transferCanBeReversedOnlyOnce() throws Exception {
		Object transferId = transfer("key-1", "W010", "W011", 5000).getBody().get("transfer_id");

		List<ResponseEntity<Map>> responses = runAtOnce(10, i -> http.postForEntity(
				"/mpesa/v1/p2p/transfers/" + transferId + "/reversal", Map.of("reason", "dispute"), Map.class));

		assertThat(responses.stream().filter(r -> r.getStatusCode().value() == 200)).hasSize(1);
		assertThat(responses.stream().filter(r -> r.getStatusCode().value() == 409)).hasSize(9);
		assertThat(balance("W010")).isEqualTo(1000000);
		assertThat(balance("W011")).isEqualTo(1000000);
	}

	@Test
	void frozenWalletCannotSendButCanReceiveReversal() {
		Object transferId = transfer("key-1", "W030", "W031", 5000).getBody().get("transfer_id");
		setStatus("W030", "FROZEN");

		assertThat(transfer("key-2", "W030", "W031", 100).getBody().get("error")).isEqualTo("WALLET_NOT_ACTIVE");
		ResponseEntity<Map> reversal = http.postForEntity(
				"/mpesa/v1/p2p/transfers/" + transferId + "/reversal", Map.of("reason", "dispute"), Map.class);
		assertThat(reversal.getStatusCode().value()).isEqualTo(200);
		assertThat(balance("W030")).isEqualTo(1000000);
	}

	@Test
	void payoutReservesMoneyAndIsPending() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Idempotency-Key", "payout-1");
		ResponseEntity<Map> created = http.postForEntity("/mpesa/v1/payouts", new HttpEntity<>(
				Map.of("wallet_id", "M001", "bank_account", "ACC-001", "amount", 5000), headers), Map.class);

		assertThat(created.getStatusCode().value()).isEqualTo(202);
		assertThat(created.getBody().get("status")).isEqualTo("PENDING");
		Map wallet = wallet("M001");
		assertThat(wallet.get("balance")).isEqualTo(50000000);
		assertThat(wallet.get("reserved")).isEqualTo(5000);
		assertThat(wallet.get("available")).isEqualTo(49995000);

		Map payout = http.getForObject("/mpesa/v1/payouts/" + created.getBody().get("payout_id"), Map.class);
		assertThat(payout.get("status")).isEqualTo("PENDING");
		assertThat(payout.get("amount")).isEqualTo(5000);
	}

	private ResponseEntity<Map> transfer(String key, String from, String to, long amount) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Idempotency-Key", key);
		Map<String, Object> body = Map.of("from_wallet", from, "to_wallet", to, "amount", amount, "reference", "test");
		return http.postForEntity("/mpesa/v1/p2p/transfers", new HttpEntity<>(body, headers), Map.class);
	}

	private void setStatus(String walletId, String status) {
		http.exchange("/mpesa/v1/wallets/" + walletId + "/status", HttpMethod.PUT,
				new HttpEntity<>(Map.of("status", status, "reason", "test")), Map.class);
	}

	private Map wallet(String walletId) {
		return http.getForObject("/mpesa/v1/wallets/" + walletId, Map.class);
	}

	private long balance(String walletId) {
		return ((Number) wallet(walletId).get("balance")).longValue();
	}

	private interface Call {
		ResponseEntity<Map> run(int i);
	}

	private static List<ResponseEntity<Map>> runAtOnce(int count, Call call) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(count);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			int n = i;
			Callable<ResponseEntity<Map>> task = () -> {
				start.await();
				return call.run(n);
			};
			futures.add(pool.submit(task));
		}
		start.countDown();
		List<ResponseEntity<Map>> results = new ArrayList<>();
		for (Future<ResponseEntity<Map>> f : futures) {
			results.add(f.get());
		}
		pool.shutdown();
		return results;
	}

}
