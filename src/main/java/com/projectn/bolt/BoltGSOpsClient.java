package com.projectn.bolt;

import com.google.api.gax.paging.Page;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.storage.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import javax.xml.bind.DatatypeConverter;
import java.util.Date;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class BoltGSOpsClient {

    // request types supported
    enum RequestType {
        LIST_OBJECTS,
        LIST_BUCKETS,
        GET_BUCKET_MD,
        GET_OBJECT_MD,
        UPLOAD_OBJECT,
        DOWNLOAD_OBJECT,
        DELETE_OBJECT
    }

    // endpoints supported
    enum SdkType {
        // Google Cloud Storage.
        GS,
        BOLT
    }

    private Storage storage;
    private String boltUrl;
    private static final Gson gson = new Gson();
    private HttpResponse response;

    public BoltGSOpsClient(HttpResponse response) {
        storage = null;
        this.response = response;
    }

    public void processEvent(HttpRequest request) throws IOException {

        try {
            // Parse JSON Request.
            String bucketName = null;
            String objectName = null;
            String value = null;
            RequestType requestType = null;
            SdkType sdkType = null;

            JsonElement requestParsed = gson.fromJson(request.getReader(), JsonElement.class);
            JsonObject requestJson = null;

            if (requestParsed != null && requestParsed.isJsonObject()) {
                requestJson = requestParsed.getAsJsonObject();
            }

            if (requestJson != null) {
                if (requestJson.has("bucket")) {
                    bucketName = requestJson.get("bucket").getAsString();
                }
                if (requestJson.has("requestType")) {
                    String requestTypeStr = requestJson.get("requestType").getAsString();
                    requestType = RequestType.valueOf(requestTypeStr.toUpperCase());
                }
                if (requestJson.has("sdkType")) {
                    String sdkTypeStr = requestJson.get("sdkType").getAsString();
                    sdkType = (sdkTypeStr != null && !sdkTypeStr.isEmpty()) ?
                            SdkType.valueOf(sdkTypeStr.toUpperCase()) : null;
                }
                if (requestJson.has("key")) {
                    objectName= requestJson.get("key").getAsString();
                }
                if (requestJson.has("value")) {
                    value = requestJson.get("value").getAsString();
                }
            }

            boltUrl = System.getenv("BOLT_URL");

            // create an Google/Bolt Storage service Object depending on the 'sdkType'
            // If sdkType is not specified, create an Google Storage Service Object.
            if (sdkType == null || sdkType == SdkType.GS) {
                storage = StorageOptions.getDefaultInstance().getService();
            } else if (sdkType == SdkType.BOLT) {
                storage = StorageOptions.newBuilder().setHost(boltUrl).build().getService();
            }

            // Perform a GS / Bolt operation based on the input 'requestType'
            switch (requestType) {
                case LIST_OBJECTS:
                    listObjects(bucketName);
                    break;
                case LIST_BUCKETS:
                    listBuckets();
                case GET_BUCKET_MD:
                    getBucketMetadata(bucketName);
                    break;
                case GET_OBJECT_MD:
                    getObjectMetadata(bucketName, objectName);
                    break;
                case UPLOAD_OBJECT:
                    uploadObject(bucketName, objectName, value);
                    break;
                case DOWNLOAD_OBJECT:
                    downloadObject(bucketName, objectName);
                    break;
                case DELETE_OBJECT:
                    deleteObject(bucketName, objectName);
                    break;
                default:
                    break;
            }
        } catch (JsonParseException | IOException e) {
            response.getWriter().write("Error parsing JSON: " + e.getMessage());
        } catch (StorageException e) {
            response.getWriter().write("ErrorCode: " + e.getCode());
            response.getWriter().newLine();
            response.getWriter().write("ErrorMessage: " + e.getMessage());
        } catch (Exception e) {
            response.getWriter().write("ErrorMessage: " + e.getMessage());
        }
    }

    private void listObjects(String bucketName) throws Exception {
        Bucket bucket = storage.get(bucketName);
        Page<Blob> blobs = bucket.list();

        BufferedWriter writer = response.getWriter();
        for (Blob blob : blobs.iterateAll()) {
            writer.write(blob.getName());
            writer.newLine();
        }
    }

    private void listBuckets() throws Exception {
        Page<Bucket> buckets = storage.list();

        BufferedWriter writer = response.getWriter();
        for (Bucket bucket : buckets.iterateAll()) {
            writer.write(bucket.getName());
            writer.newLine();
        }
    }

    private void getBucketMetadata(String bucketName) throws Exception {
        Bucket bucket = storage.get(bucketName,
                Storage.BucketGetOption.fields(
                        Storage.BucketField.values()));

        BufferedWriter writer = response.getWriter();
        writer.write("BucketName: " + bucket.getName());
        writer.newLine();
        writer.write("Location: " + bucket.getLocation());
        writer.newLine();
        writer.write("StorageClass: " + bucket.getStorageClass().name());
        writer.newLine();
        writer.write("VersioningEnabled: " + bucket.versioningEnabled());
    }

    private void uploadObject(String bucketName, String objectName, String value) throws Exception {
        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
        Blob blob = storage.create(blobInfo, value.getBytes(UTF_8));

        BufferedWriter writer = response.getWriter();
        writer.write("ETag: " + blob.getEtag());
        writer.newLine();
        writer.write("MD5: " + blob.getMd5());
        writer.newLine();
        writer.write("MD5HexString: " + blob.getMd5ToHexString());
    }

    private void downloadObject(String bucketName, String objectName) throws Exception {
        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        byte[] content = blob.getContent();

        // Parse the MD5 of the returned object
        MessageDigest md = MessageDigest.getInstance("MD5");
        String md5;

        // If Object is gzip encoded, compute MD5 on the decompressed object.
        String encoding = blob.getContentEncoding();
        System.out.println(encoding);
        if ((encoding != null && encoding.equalsIgnoreCase("gzip")) ||
                objectName.endsWith(".gz")) {

            // MD5 of the object after gzip decompression.
            ByteArrayInputStream bis = new ByteArrayInputStream(content);
            GZIPInputStream gis = new GZIPInputStream(bis);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;

            while ((len = gis.read(buffer)) > 0) {
                output.write(buffer, 0, len);
            }

            md.update(output.toByteArray());
            md5 = DatatypeConverter.printHexBinary(md.digest()).toUpperCase();
            output.close();
            gis.close();
        } else {
            md.update(content);
            md5 = DatatypeConverter.printHexBinary(md.digest()).toUpperCase();
        }

        BufferedWriter writer = response.getWriter();
        writer.write("md5: " + md5);
    }

    private void getObjectMetadata(String bucketName, String objectName) throws Exception {
        Blob blob = storage.get(bucketName, objectName,
                Storage.BlobGetOption.fields(Storage.BlobField.values()));

        BufferedWriter writer = response.getWriter();
        writer.write("ContentEncoding: " + blob.getContentEncoding());
        writer.newLine();
        writer.write("ETag: " + blob.getEtag());
        writer.newLine();
        writer.write("Md5Hash: " + blob.getMd5());
        writer.newLine();
        writer.write("Md5HexString: " + blob.getMd5ToHexString());
        writer.newLine();
        writer.write("Size: " + blob.getSize());
        writer.newLine();
        writer.write("StorageClass: " + blob.getStorageClass());
        writer.newLine();
        writer.write("TimeCreated: " + new Date(blob.getCreateTime()));
        writer.newLine();
        writer.write("Last Metadata Update: " + new Date(blob.getUpdateTime()));
        writer.newLine();
        if (blob.getRetentionExpirationTime() != null) {
            writer.write("retentionExpirationTime: " + new Date(blob.getRetentionExpirationTime()));
        }
    }

    private void deleteObject(String bucketName, String objectName) throws Exception {
        boolean deleted = storage.delete(bucketName, objectName);

        BufferedWriter writer = response.getWriter();
        writer.write("Deleted: " + deleted);
    }
}
