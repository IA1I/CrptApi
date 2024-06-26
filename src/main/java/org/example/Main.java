package org.example;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws IOException {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 10, 10);

        CrptApi.Document document = null;
        String signature = "signature";

        crptApi.createDocument(document, signature);

        crptApi.close();
    }
}