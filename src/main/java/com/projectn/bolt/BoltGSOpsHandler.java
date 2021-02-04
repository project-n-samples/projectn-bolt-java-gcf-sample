package com.projectn.bolt;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import java.io.IOException;

public class BoltGSOpsHandler implements HttpFunction {

    @Override
    public void service(HttpRequest request, HttpResponse response)
            throws IOException {

        BoltGSOpsClient boltGSOpsClient = new BoltGSOpsClient(response);
        boltGSOpsClient.processEvent(request);
    }
}
