package com.example.safari.transfer;

import com.example.safari.idempotency.Idempotency;
import com.example.safari.transfer.TransferService.ReversalResponse;
import com.example.safari.transfer.TransferService.TransferRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mpesa/v1/p2p/transfers")
public class TransferController {

	record ReversalRequest(String reason) {
	}

	private final TransferService transfers;
	private final Idempotency idempotency;

	public TransferController(TransferService transfers, Idempotency idempotency) {
		this.transfers = transfers;
		this.idempotency = idempotency;
	}

	@PostMapping
	ResponseEntity<Object> transfer(@RequestHeader(value = "Idempotency-Key", required = false) String key,
			@RequestBody TransferRequest request) {
		return idempotency.run(key, "transfer", request,
				() -> ResponseEntity.status(HttpStatus.CREATED).body(transfers.transfer(request)));
	}

	@PostMapping("/{transferId}/reversal")
	ReversalResponse reverse(@PathVariable String transferId, @RequestBody(required = false) ReversalRequest request) {
		return transfers.reverse(transferId, request == null ? null : request.reason());
	}

}
