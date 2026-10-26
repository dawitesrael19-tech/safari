package com.example.safari.payout;

import com.example.safari.error.ApiException;
import com.example.safari.idempotency.Idempotency;
import com.example.safari.payout.PayoutService.PayoutRequest;
import com.example.safari.payout.PayoutService.PayoutView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mpesa/v1")
public class PayoutController {

	record BankCallback(String eventId, String payoutId, String bankRef, String status) {
	}

	private final PayoutService payouts;
	private final Idempotency idempotency;

	public PayoutController(PayoutService payouts, Idempotency idempotency) {
		this.payouts = payouts;
		this.idempotency = idempotency;
	}

	@PostMapping("/payouts")
	ResponseEntity<Object> create(@RequestHeader(value = "Idempotency-Key", required = false) String key,
			@RequestBody PayoutRequest request) {
		return idempotency.run(key, "payout", request,
				() -> ResponseEntity.status(HttpStatus.ACCEPTED).body(payouts.create(request)));
	}

	@GetMapping("/payouts/{payoutId}")
	PayoutView get(@PathVariable String payoutId) {
		return payouts.get(payoutId);
	}

	@PostMapping("/callbacks/bank")
	ResponseEntity<Void> bankCallback(@RequestBody BankCallback callback) {
		if (callback.payoutId() == null
				|| !("SUCCEEDED".equals(callback.status()) || "FAILED".equals(callback.status()))) {
			throw ApiException.validation("payout_id and a status of SUCCEEDED or FAILED are required");
		}
		payouts.finish(callback.payoutId(), callback.status(), callback.bankRef());
		return ResponseEntity.ok().build();
	}

}
