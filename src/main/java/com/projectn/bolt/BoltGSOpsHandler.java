package com.projectn.bolt;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import java.io.IOException;

/**
 * BoltGSOpsHandler represents a Google Cloud Function that is invoked by an HTTP Request.
 */
public class BoltGSOpsHandler implements HttpFunction {

    /**
     * service serves an incoming HTTP Request.
     *
     * service accepts the following input parameters as part of the HTTP Request:
     * 1) sdkType - Endpoint to which request is sent. The following values are supported:
     *    GS - The Request is sent to Google Cloud Storage.
     *    Bolt - The Request is sent to Bolt, whose endpoint is configured via 'BOLT_URL' environment variable
     *
     * 2) requestType - type of request / operation to be performed. The following requests are supported:
     *    a) list_objects - list objects
     *    b) list_buckets - list buckets
     *    c) get_object_md - head object
     *    d) get_bucket_md - head bucket
     *    e) download_object - get object (md5 hash)
     *    f) upload_object - upload object
     *    g) delete_object - delete object
     *
     * 3) bucket - bucket name
     *
     * 4) key - key name
     *
     * Following are examples of events, for various requests, that can be used to invoke the handler function.
     * a) Listing objects from Bolt bucket:
     *     {"requestType": "list_objects", "sdkType": "BOLT", "bucket": "<bucket>"}
     *
     * b) Listing buckets from GS:
     *     {"requestType": "list_buckets", "sdkType": "GS"}
     *
     * c) Get Bolt object metadata (GET_OBJECT_MD):
     *     {"requestType": "get_object_md", "sdkType": "BOLT", "bucket": "<bucket>", "key": "<key>"}
     *
     * d) Check if GS bucket exists (GET_BUCKET_MD):
     *     {"requestType": "get_bucket_md","sdkType": "GS", "bucket": "<bucket>"}
     *
     * e) Download object (its MD5 Hash) from Bolt:
     *     {"requestType": "download_object", "sdkType": "BOLT", "bucket": "<bucket>", "key": "<key>"}
     *
     * f) Upload object to Bolt:
     *     {"requestType": "upload_object", "sdkType": "BOLT", "bucket": "<bucket>", "key": "<key>", "value": "<value>"}
     *
     * g) Delete object from Bolt:
     *     {"requestType": "delete_object", "sdkType": "BOLT", "bucket": "<bucket>", "key": "<key>"}
     *
     * @param request incoming Http Request
     * @param response outgoing Http Response
     * @throws IOException
     */
    @Override
    public void service(HttpRequest request, HttpResponse response)
            throws IOException {

        BoltGSOpsClient boltGSOpsClient = new BoltGSOpsClient(response);
        boltGSOpsClient.processEvent(request);
    }
}
