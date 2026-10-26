package com.example.fakebank;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bank")
public class BankConfig {

	private double noResponseRate = 0.12;
	private double noResponseProcessedRate = 0.5;
	private double rejectRate = 0.08;
	private double failRate = 0.05;
	private double duplicateRate = 0.30;
	private long submitLatencyMinMs = 2000;
	private long submitLatencyMaxMs = 6000;
	private long statusLatencyMinMs = 1000;
	private long statusLatencyMaxMs = 3000;
	private long hangMs = 60000;
	private long callbackDelayMinMs = 1000;
	private long callbackDelayMaxMs = 10000;
	private long callbackTimeoutMs = 2000;
	private long callbackRetryIntervalMs = 3000;
	private int callbackMaxRetries = 5;
	private List<String> callbackUrls = List.of(
			"http://localhost:8081/mpesa/v1/callbacks/bank",
			"http://localhost:8082/mpesa/v1/callbacks/bank");

    public double getNoResponseRate() {
        return noResponseRate;
    }

    public void setNoResponseRate(double noResponseRate) {
        this.noResponseRate = noResponseRate;
    }

    public double getNoResponseProcessedRate() {
        return noResponseProcessedRate;
    }

    public void setNoResponseProcessedRate(double noResponseProcessedRate) {
        this.noResponseProcessedRate = noResponseProcessedRate;
    }

    public double getRejectRate() {
        return rejectRate;
    }

    public void setRejectRate(double rejectRate) {
        this.rejectRate = rejectRate;
    }

    public double getFailRate() {
        return failRate;
    }

    public void setFailRate(double failRate) {
        this.failRate = failRate;
    }

    public double getDuplicateRate() {
        return duplicateRate;
    }

    public void setDuplicateRate(double duplicateRate) {
        this.duplicateRate = duplicateRate;
    }

 	public long getSubmitLatencyMinMs(){
		return this.submitLatencyMinMs;
	}


    public void setSubmitLatencyMinMs(long submitLatencyMinMs) {
        this.submitLatencyMinMs = submitLatencyMinMs;
    }

    public long getSubmitLatencyMaxMs() {
        return submitLatencyMaxMs;
    }

    public void setSubmitLatencyMaxMs(long submitLatencyMaxMs) {
        this.submitLatencyMaxMs = submitLatencyMaxMs;
    }

    public long getStatusLatencyMinMs() {
        return statusLatencyMinMs;
    }

    public void setStatusLatencyMinMs(long statusLatencyMinMs) {
        this.statusLatencyMinMs = statusLatencyMinMs;
    }

    public long getStatusLatencyMaxMs() {
        return statusLatencyMaxMs;
    }

    public void setStatusLatencyMaxMs(long statusLatencyMaxMs) {
        this.statusLatencyMaxMs = statusLatencyMaxMs;
    }

    public long getHangMs() {
        return hangMs;
    }

    public void setHangMs(long hangMs) {
        this.hangMs = hangMs;
    }

    public long getCallbackDelayMinMs() {
        return callbackDelayMinMs;
    }

    public void setCallbackDelayMinMs(long callbackDelayMinMs) {
        this.callbackDelayMinMs = callbackDelayMinMs;
    }

    public long getCallbackDelayMaxMs() {
        return callbackDelayMaxMs;
    }

    public void setCallbackDelayMaxMs(long callbackDelayMaxMs) {
        this.callbackDelayMaxMs = callbackDelayMaxMs;
    }

    public long getCallbackTimeoutMs() {
        return callbackTimeoutMs;
    }

    public void setCallbackTimeoutMs(long callbackTimeoutMs) {
        this.callbackTimeoutMs = callbackTimeoutMs;
    }

    public long getCallbackRetryIntervalMs() {
        return callbackRetryIntervalMs;
    }

    public void setCallbackRetryIntervalMs(long callbackRetryIntervalMs) {
        this.callbackRetryIntervalMs = callbackRetryIntervalMs;
    }

    public int getCallbackMaxRetries() {
        return callbackMaxRetries;
    }

    public void setCallbackMaxRetries(int callbackMaxRetries) {
        this.callbackMaxRetries = callbackMaxRetries;
    }

    public List<String> getCallbackUrls() {
        return callbackUrls;
    }

    public void setCallbackUrls(List<String> callbackUrls) {
        this.callbackUrls = callbackUrls;
    }
}
