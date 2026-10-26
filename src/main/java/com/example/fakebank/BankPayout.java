package com.example.fakebank;

import java.util.concurrent.atomic.AtomicReference;

record BankPayout(String payoutId, long amount, String result, String bankRef, String reason,
		AtomicReference<String> status) {

	static BankPayout accepted(String payoutId, long amount, String bankRef) {
		return new BankPayout(payoutId, amount, "ACCEPTED", bankRef, null, new AtomicReference<>("IN_PROGRESS"));
	}

	static BankPayout sample(String payoutId, long amount, String status, String bankRef) {
		if (status.equals("REJECTED")) {
			return rejected(payoutId, amount);
		}
		return new BankPayout(payoutId, amount, "ACCEPTED", bankRef, null, new AtomicReference<>(status));
	}

	static BankPayout rejected(String payoutId, long amount) {
		return new BankPayout(payoutId, amount, "REJECTED", null, "BENEFICIARY_ACCOUNT_INVALID",
				new AtomicReference<>("REJECTED"));
	}

}
