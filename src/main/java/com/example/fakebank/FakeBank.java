package com.example.fakebank;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
class FakeBank {

    private final ObjectMapper mapper;
    private final BankConfig defaults;
    private volatile BankConfig config;
    private final AtomicLong generation = new AtomicLong();
    private final Map<String, BankPayout> payouts = new ConcurrentHashMap<>();
    private final AtomicLong totalPaid = new AtomicLong();
    private final ScheduledExecutorService timers = Executors.newScheduledThreadPool(4);
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    record Sample(String payoutId, String bankAccount, long amount, String status, String bankRef) {
    }

    FakeBank(ObjectMapper mapper, BankConfig defaults,
             @Value("${bank.preload-samples:false}") boolean preloadSamples) throws IOException {
        this.mapper = mapper;
        this.defaults = defaults;
        this.config = copy(defaults);
        if (preloadSamples) {
            loadSamples();
        }
    }

    synchronized int loadSamples() throws IOException {
        List<Sample> samples;
        try (InputStream in = new ClassPathResource("bank-samples.json").getInputStream()) {
            samples = mapper.readValue(in, new TypeReference<List<Sample>>() {});
        }
        for (Sample s : samples) {
            BankPayout payout = BankPayout.sample(s.payoutId(), s.amount(), s.status(), s.bankRef());
            if (payouts.putIfAbsent(s.payoutId(), payout) == null && s.status().equals("SUCCEEDED")) {
                totalPaid.addAndGet(s.amount());
            }
        }
        return samples.size();
    }

    CompletableFuture<ResponseEntity<Object>> submit(String payoutId, long amount) {
        BankConfig cfg = config;
        long gen = generation.get();
        double roll = random().nextDouble();
        boolean noResponse = roll < cfg.getNoResponseRate();
        boolean reject = !noResponse && roll < cfg.getNoResponseRate() + cfg.getRejectRate();
        long latency = between(cfg.getSubmitLatencyMinMs(), cfg.getSubmitLatencyMaxMs());

        if (noResponse && random().nextDouble() >= cfg.getNoResponseProcessedRate()) {
            return hang(cfg);
        }

        boolean[] isNew = new boolean[1];
        BankPayout payout = payouts.computeIfAbsent(payoutId, id -> {
            isNew[0] = true;
            return reject ? BankPayout.rejected(id, amount) : BankPayout.accepted(id, amount, newBankRef());
        });
        if (isNew[0] && payout.result().equals("ACCEPTED")) {
            timers.schedule(() -> decide(payout, gen), random().nextLong(latency + 1), TimeUnit.MILLISECONDS);
        }

        Map<String, Object> body;
        if (payout.amount() != amount) {
            body = Map.of("result", "REJECTED", "reason", "PAYOUT_ID_AMOUNT_MISMATCH");
        } else if (payout.result().equals("REJECTED")) {
            body = Map.of("result", "REJECTED", "reason", payout.reason());
        } else {
            body = Map.of("result", "ACCEPTED", "bank_ref", payout.bankRef());
        }
        if (noResponse) {
            return hang(cfg);
        }
        return after(latency, () -> ResponseEntity.ok(body));
    }

    CompletableFuture<ResponseEntity<Object>> status(String payoutId) {
        BankConfig cfg = config;
        return after(between(cfg.getStatusLatencyMinMs(), cfg.getStatusLatencyMaxMs()), () -> {
            BankPayout payout = payouts.get(payoutId);
            return ResponseEntity.ok(Map.of("status", payout == null ? "NOT_FOUND" : payout.status().get()));
        });
    }

    private void decide(BankPayout payout, long gen) {
        if (gen != generation.get()) {
            return;
        }
        BankConfig cfg = config;
        String outcome = random().nextDouble() < cfg.getFailRate() ? "FAILED" : "SUCCEEDED";
        if (!payout.status().compareAndSet("IN_PROGRESS", outcome)) {
            return;
        }
        if (outcome.equals("SUCCEEDED")) {
            totalPaid.addAndGet(payout.amount());
        }
        String event = json(Map.of(
                "event_id", UUID.randomUUID().toString(),
                "payout_id", payout.payoutId(),
                "bank_ref", payout.bankRef(),
                "status", outcome));
        int copies = random().nextDouble() < cfg.getDuplicateRate() ? 2 + random().nextInt(2) : 1;
        long delay = between(cfg.getCallbackDelayMinMs(), cfg.getCallbackDelayMaxMs());
        for (int i = 0; i < copies; i++) {
            timers.schedule(() -> sendCallback(event, 0, gen), delay, TimeUnit.MILLISECONDS);
        }
    }

    private void sendCallback(String event, int attempt, long gen) {
        if (gen != generation.get()) {
            return;
        }
        BankConfig cfg = config;
        List<String> urls = cfg.getCallbackUrls();
        HttpRequest request = HttpRequest.newBuilder(URI.create(urls.get(random().nextInt(urls.size()))))
                .timeout(Duration.ofMillis(cfg.getCallbackTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(event))
                .build();
        long started = System.currentTimeMillis();
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            boolean ok = error == null && response.statusCode() / 100 == 2;
            if (!ok && attempt < cfg.getCallbackMaxRetries()) {
                long wait = Math.max(0, cfg.getCallbackRetryIntervalMs() - (System.currentTimeMillis() - started));
                timers.schedule(() -> sendCallback(event, attempt + 1, gen), wait, TimeUnit.MILLISECONDS);
            }
        });
    }

    BankConfig config() {
        return config;
    }

    synchronized void updateConfig(Map<String, Object> patch) throws Exception {
        config = mapper.readerForUpdating(copy(config))
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue((JsonNode) mapper.valueToTree(patch));
    }

    synchronized void reset() {
        generation.incrementAndGet();
        payouts.clear();
        totalPaid.set(0);
        config = copy(defaults);
    }

    long totalPaid() {
        return totalPaid.get();
    }

    List<Map<String, Object>> payouts() {
        return payouts.values().stream()
                .sorted(Comparator.comparing(BankPayout::payoutId))
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("payout_id", p.payoutId());
                    row.put("amount", p.amount());
                    row.put("status", p.status().get());
                    row.put("bank_ref", p.bankRef());
                    return row;
                })
                .toList();
    }

    private CompletableFuture<ResponseEntity<Object>> hang(BankConfig cfg) {
        return after(cfg.getHangMs(), () -> ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(Map.of("error", "GATEWAY_TIMEOUT")));
    }

    private <T> CompletableFuture<T> after(long millis, Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier,
                CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS, timers));
    }

    private static ThreadLocalRandom random() {
        return ThreadLocalRandom.current();
    }

    private static long between(long min, long max) {
        return min >= max ? min : random().nextLong(min, max + 1);
    }

    private static String newBankRef() {
        byte[] bytes = new byte[8];
        random().nextBytes(bytes);
        return "BR" + HexFormat.of().withUpperCase().formatHex(bytes);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private BankConfig copy(BankConfig source) {
        return mapper.convertValue(source, BankConfig.class);
    }

}
