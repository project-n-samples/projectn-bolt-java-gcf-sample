package com.projectn.bolt;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.storage.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.xml.bind.DatatypeConverter;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;

/**
 * BoltGSValidateObjHandler represents a Google Cloud Function that is invoked by an HTTP Request and performs
 * data validation tests
 */
public class BoltGSValidateObjHandler implements HttpFunction {

    // Indicates if source bucket is cleaned post crunch.
    enum BucketClean {
        // bucket is cleaned post crunch
        ON,
        // bucket is not cleaned post crunch
        OFF
    }

    private static final Gson gson = new Gson();

    /**
     * service serves an incoming HTTP Request for performing data validation tests.
     *
     * service accepts the following input parameters as part of the HTTP Request:
     * 1) bucket - bucket name
     * 2) key - key name
     *
     * service retrieves the object from Bolt and GS (if BucketClean is OFF), computes and returns their
     * corresponding MD5 hash. If the object is gzip encoded, object is decompressed before computing its MD5.
     * @param request incoming http request
     * @param response outgoing http response
     * @return md5s of object retrieved from Bolt and GS.
     */
    @Override
    public void service(HttpRequest request, HttpResponse response)
            throws IOException {

        // Parse JSON Request.
        String bucketName = null;
        String objectName = null;
        BucketClean bucketClean = BucketClean.OFF;

        JsonElement requestParsed = gson.fromJson(request.getReader(), JsonElement.class);
        JsonObject requestJson = null;

        if (requestParsed != null && requestParsed.isJsonObject()) {
            requestJson = requestParsed.getAsJsonObject();
        }

        if (requestJson != null) {
            if (requestJson.has("bucket")) {
                bucketName = requestJson.get("bucket").getAsString();
            }
            if (requestJson.has("key")) {
                objectName= requestJson.get("key").getAsString();
            }
            if (requestJson.has("bucketClean")) {
                String bucketCleanStr = requestJson.get("bucketClean").getAsString();
                bucketClean = (bucketCleanStr != null && !bucketCleanStr.isEmpty()) ?
                        BucketClean.valueOf(bucketCleanStr.toUpperCase()) : BucketClean.OFF;
            }
        }

        Storage gsStorage = StorageOptions.getDefaultInstance().getService();
        String boltUrl = System.getenv("BOLT_URL").replace("{region}", BoltGSOpsClient.region());
        Storage boltStorage = StorageOptions.newBuilder().setHost(boltUrl).build().getService();

        try {
            // Download Object from Bolt.
            Blob boltBlob = boltStorage.get(BlobId.of(bucketName, objectName));

            // Get Object from GS if bucket clean is off.
            Blob gsBlob = null;
            if (bucketClean == BucketClean.OFF) {
                gsBlob = gsStorage.get(BlobId.of(bucketName, objectName));
            }

            // Parse the MD5 of the returned object
            MessageDigest md = MessageDigest.getInstance("MD5");
            String gsMd5, boltMd5;

            // If Object is gzip encoded, compute MD5 on the decompressed object.
            String encoding = gsBlob.getContentEncoding();
            if ((encoding != null && encoding.equalsIgnoreCase("gzip")) ||
                    objectName.endsWith(".gz")) {
                GZIPInputStream gis;
                ByteArrayInputStream bis;
                ByteArrayOutputStream output;
                byte[] buffer = new byte[1024];
                int len;

                // MD5 of the GS object after gzip decompression.
                bis = new ByteArrayInputStream(gsBlob.getContent());
                gis = new GZIPInputStream(bis);
                output = new ByteArrayOutputStream();

                while ((len = gis.read(buffer)) > 0) {
                    output.write(buffer, 0, len);
                }

                md.update(output.toByteArray());
                gsMd5 = DatatypeConverter.printHexBinary(md.digest()).toUpperCase();
                output.close();
                gis.close();

                // MD5 of the Bolt object after gzip decompression.
                bis = new ByteArrayInputStream(boltBlob.getContent());
                gis = new GZIPInputStream(bis);
                output = new ByteArrayOutputStream();

                while ((len = gis.read(buffer)) > 0) {
                    output.write(buffer, 0, len);
                }

                md.reset();
                md.update(output.toByteArray());
                boltMd5 = DatatypeConverter.printHexBinary(md.digest()).toUpperCase();
                output.close();
                gis.close();
            } else  {
                // MD5 of the GS object
                md.update(gsBlob.getContent());
                gsMd5 = DatatypeConverter.printHexBinary(md.digest()).toUpperCase();

                //MD5 of the Bolt object
                md.reset();
                md.update(boltBlob.getContent());
                boltMd5 = DatatypeConverter.printHexBinary(md.digest()).toUpperCase();
            }

            BufferedWriter writer = response.getWriter();
            writer.write("gs-md5: " + gsMd5);
            writer.newLine();
            writer.write("bolt-md5: " + boltMd5);
        } catch (StorageException e) {
            response.getWriter().write("ErrorCode: " + e.getCode());
            response.getWriter().newLine();
            response.getWriter().write("ErrorMessage: " + e.getMessage());
        } catch (Exception e) {
            response.getWriter().write("ErrorMessage: " + e.getMessage());
        }
    }
}
