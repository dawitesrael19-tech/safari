package com.example.safari.wallet;

import com.example.safari.error.ApiException;
import com.example.safari.wallet.WalletRepository.StatusChange;
import com.example.safari.wallet.WalletRepository.Wallet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mpesa")
public class WalletController {

	record StatusRequest(String status, String reason) {
	}

	private final WalletRepository wallets;

	public WalletController(WalletRepository wallets) {
		this.wallets = wallets;
	}

	@GetMapping("/v1/wallets/{walletId}")
	Wallet get(@PathVariable String walletId) {
		return wallets.find(walletId).orElseThrow(() -> ApiException.walletNotFound(walletId));
	}

	/*updates here */
	@PutMapping("/v1/wallets/{walletId}/status")
	StatusChange setStatus(@PathVariable String walletId, @RequestBody StatusRequest request) {
		if (!"ACTIVE".equals(request.status()) && !"FROZEN".equals(request.status())) {
			throw ApiException.validation("status must be ACTIVE or FROZEN");
		}
		return wallets.changeStatus(walletId, request.status())
				.orElseThrow(() -> ApiException.walletNotFound(walletId));
	}

	@PostMapping("/internal/reset")
	void reset() {
		wallets.resetToSeed();
	}

}
