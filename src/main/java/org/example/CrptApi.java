package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class CrptApi implements Closeable {
    private static final int CORE_POOL_SIZE = 1;
    private static final long INITIAL_DELAY = 0L;
    private static final long TIMEOUT_FOR_CLOSING = 1L;
    private static final TimeUnit TIME_UNIT_FOR_CLOSING = TimeUnit.HOURS;
    private static final String API_URI = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final int requestLimit;
    private final AtomicInteger currentRequestNumber;
    private final ScheduledExecutorService executorService;
    private final ReentrantLock lock;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;


    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this(timeUnit, 1L, requestLimit);
    }

    public CrptApi(TimeUnit timeUnit, long delay, int requestLimit) {
        this.requestLimit = requestLimit;
        this.currentRequestNumber = new AtomicInteger();
        this.executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE);

        var task = new TaskForUpdateRequestNumber(currentRequestNumber);
        this.executorService.scheduleWithFixedDelay(task, INITIAL_DELAY, delay, timeUnit);
        this.lock = new ReentrantLock();
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
    }

    public void createDocument(Document document, String signature) {
        isMethodAvailable();
        String body = mapToJson(document);
        HttpRequest request = buildRequest(body, signature);
        log.info("Built http request: {}", request);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Http response received: {}", response);
        } catch (IOException | InterruptedException e) {
            log.error("Something went wrong: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static HttpRequest buildRequest(String body, String signature) {
        return HttpRequest.newBuilder()
                .uri(URI.create(API_URI))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private String mapToJson(Document document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            log.error("Something went wrong while mapping document to json");
            throw new RuntimeException(e);
        }
    }

    private void isMethodAvailable() {
        lock.lock();
        try {
            while (currentRequestNumber.get() >= requestLimit) {
            }
        } finally {
            currentRequestNumber.incrementAndGet();
            lock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(TIMEOUT_FOR_CLOSING, TIME_UNIT_FOR_CLOSING)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }


    @Getter
    @AllArgsConstructor
    public class Document {
        private Description description;
        private long docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private LocalDate productionDate;
        private String productionType;
        private List<Product> products;
        private LocalDate regDate;
        private String regNumber;
    }

    @Getter
    @AllArgsConstructor
    public class Description {
        private String participantInn;
    }

    @Getter
    @AllArgsConstructor
    public class Product {
        private String certificateDocument;
        private LocalDate certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private LocalDate productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }

    private class TaskForUpdateRequestNumber implements Runnable {
        private static final int INITIAL_REQUEST_NUMBER = 1;
        private final AtomicInteger currentRequestNumber;

        public TaskForUpdateRequestNumber(AtomicInteger currentRequestNumber) {
            this.currentRequestNumber = currentRequestNumber;
        }

        @Override
        public void run() {
            currentRequestNumber.set(INITIAL_REQUEST_NUMBER);
            log.info("Number of requests updated");
        }
    }
}