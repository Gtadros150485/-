package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class CrptApi {


    private final ScheduledExecutorService scheduler;
    private final Semaphore semaphore;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.semaphore = new Semaphore(requestLimit);
        this.objectMapper = new ObjectMapper();

        long delay = timeUnit.toMillis(1);
        scheduler.scheduleAtFixedRate(() -> {
            semaphore.release(requestLimit - semaphore.availablePermits());
        }, delay, delay, TimeUnit.MILLISECONDS);
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        semaphore.acquire();
        sendPostRequest(document, signature);
    }

    private void sendPostRequest(Document document, String signature) throws IOException {
        URL url = new URL("https://ismp.crpt.ru/api/v3/lk/documents/create");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);

        String jsonInputString = objectMapper.writeValueAsString(new RequestPayload(document, signature));

        try (var os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to create document: HTTP code " + code);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    // Inner class to hold the request payload
    static class RequestPayload {
        public final Document description;
        public final String signature;

        public RequestPayload(Document description, String signature) {
            this.description = description;
            this.signature = signature;
        }
    }

    // Inner class to represent the document
    static class Document {
        public String participantInn;
        public String docId;
        public String docStatus;
        public String docType;
        public boolean importRequest;
        public String ownerInn;
        public String producerInn;
        public String productionDate;
        public String productionType;
        public Product[] products;
        public String regDate;
        public String regNumber;

        static class Product {
            public String certificateDocument;
            public String certificateDocumentDate;
            public String certificateDocumentNumber;
            public String ownerInn;
            public String producerInn;
            public String productionDate;
            public String tnvedCode;
            public String uitCode;
            public String uituCode;
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 5);

        Document document = new Document();
        // Set the fields of the document
        // Example:
        document.participantInn = "123456789";
        document.docId = "doc123";
        document.docStatus = "NEW";
        document.docType = "LP_INTRODUCE_GOODS";
        document.importRequest = true;
        document.ownerInn = "owner123";
        document.producerInn = "producer123";
        document.productionDate = "2023-01-01";
        document.productionType = "TYPE";
        document.regDate = "2023-01-01";
        document.regNumber = "reg123";
        document.products = new Document.Product[]{new Document.Product()};
        // Set fields for the products as needed

        api.createDocument(document, "signature");
        api.shutdown();
    }
}
