package com.projectn.bolt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import javax.xml.bind.DatatypeConverter;

import com.google.cloud.storage.*;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class App {
    // Indicates if source bucket is cleaned post crunch.
    enum BucketClean {
        // bucket is cleaned post crunch
        ON,
        // bucket is not cleaned post crunch
        OFF
    }

    private static String getObject(Storage storage, String bucketName, String objectName) throws IOException, NoSuchAlgorithmException{
        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        byte[] content = blob.getContent();
        // Parse the MD5 of the returned object
        MessageDigest md = MessageDigest.getInstance("MD5");
        String md5;

        // If Object is gzip encoded, compute MD5 on the decompressed object.
        String encoding = blob.getContentEncoding();
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
        return md5;
    }

    public static void main(String[] args){
        String bucketName = "bolt-test-gs-code";
        String objectName = "file_1.txt";
        
        try {
            Storage gsStorage = StorageOptions.getDefaultInstance().getService();
            String gsMD5Output = getObject(gsStorage, bucketName, objectName);
            System.out.println("From GCS "+gsMD5Output);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String boltMD5Output = "";            
        try {
            String boltUrl = System.getenv("BOLT_URL");
            Storage boltStorage = StorageOptions.newBuilder().setHost(boltUrl).build().getService();
            System.out.println(boltUrl);
            boltMD5Output = getObject(boltStorage, bucketName, objectName);
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
        System.out.println("From Bolt "+boltMD5Output);
        
    }
}
