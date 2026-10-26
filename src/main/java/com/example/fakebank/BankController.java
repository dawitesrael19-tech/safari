package com.example.fakebank;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bank")
class BankController {

	record SubmitRequest(String payoutId, String bankAccount, Long amount) {
	}

	private final FakeBank bank;

	BankController(FakeBank bank) {
		this.bank = bank;
	}

	@PostMapping("/payouts")
	CompletableFuture<ResponseEntity<Object>> submit(@RequestBody SubmitRequest request) {
		if (request.payoutId() == null || request.amount() == null || request.amount() <= 0) {
			return CompletableFuture.completedFuture(ResponseEntity.badRequest().body(Map.of("error", "VALIDATION_FAILED")));
		}
		return bank.submit(request.payoutId(), request.amount());
	}

	@GetMapping("/payouts/{payoutId}")
	CompletableFuture<ResponseEntity<Object>> status(@PathVariable String payoutId) {
		return bank.status(payoutId);
	}

	@GetMapping("/admin/config")
	BankConfig config() {
		return bank.config();
	}

	@PutMapping("/admin/config")
	ResponseEntity<Object> updateConfig(@RequestBody Map<String, Object> patch) {
		try {
			bank.updateConfig(patch);
			return ResponseEntity.ok(bank.config());
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("error", "VALIDATION_FAILED"));
		}
	}

	@PostMapping("/admin/reset")
	void reset() {
		bank.reset();
	}

	@GetMapping("/admin/stats")
	Map<String, Object> stats() {
		return Map.of("total_paid", bank.totalPaid());
	}

}
