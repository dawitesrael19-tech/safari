package com.example.safari.payout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BankClient {

	public record Answer(String result, String bankRef) {

		static final Answer UNKNOWN = new Answer("UNKNOWN", null);

	}

	private final ObjectMapper mapper;
	private final String bankUrl;
	private final HttpClient http = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(2))
			.build();

	public BankClient(ObjectMapper mapper, @Value("${mpesa.bank.url}") String bankUrl) {
		this.mapper = mapper;
		this.bankUrl = bankUrl;
	}

	public Answer submit(String payoutId, String bankAccount, long amount) {
		try {
			String body = mapper.writeValueAsString(
					Map.of("payout_id", payoutId, "bank_account", bankAccount, "amount", amount));
			HttpRequest request = HttpRequest.newBuilder(URI.create(bankUrl + "/bank/payouts"))
					.timeout(Duration.ofSeconds(8))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body))
					.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return Answer.UNKNOWN;
			}
			JsonNode json = mapper.readTree(response.body());
			return new Answer(json.path("result").asText(), json.path("bank_ref").asText(null));
		} catch (Exception e) {
			return Answer.UNKNOWN;
		}
	}

	public String status(String payoutId) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(bankUrl + "/bank/payouts/" + payoutId))
					.timeout(Duration.ofSeconds(5))
					.GET()
					.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return "UNKNOWN";
			}
			return mapper.readTree(response.body()).path("status").asText("UNKNOWN");
		} catch (Exception e) {
			return "UNKNOWN";
		}
	}

}
