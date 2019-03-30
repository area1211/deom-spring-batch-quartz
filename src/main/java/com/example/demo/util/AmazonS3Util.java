package com.example.demo.util;


import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class AmazonS3Util {

    private static Logger logger = LoggerFactory.getLogger(AmazonS3Util.class);

    private static final String accessKey = "";
    private static final String secretKey = "";
    private static final String region = "";
    private static final String bucketName = "";

    private static AmazonS3 s3client() {
        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(Regions.fromName(region))
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials)).build();
        return s3Client;
    }
    // 업로드
    public static void uploadFile(String fileName, byte[] imageByte) {
        try {
            //        byte[] bI = Base64.getDecoder.decode(base64Data);
            InputStream fis = new ByteArrayInputStream(imageByte);

            AmazonS3 s3 = s3client();
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(imageByte.length);
            metadata.setContentType("image/png");
            metadata.setCacheControl("public, max-age=31536000");
            s3.putObject(bucketName, fileName, fis, metadata);
            s3.setObjectAcl(bucketName, fileName, CannedAccessControlList.PublicRead);
//            File file = new File(uploadFilePath);
//            s3client().putObject(new PutObjectRequest(bucketName, fileName, file));
            System.out.println("================== Upload File - Done! ==================");
        } catch (AmazonServiceException ase) {
            logger.info("Caught an AmazonServiceException from PUT requests, rejected reasons:");
            logger.info("Error Message:    " + ase.getMessage());
            logger.info("HTTP Status Code: " + ase.getStatusCode());
            logger.info("AWS Error Code:   " + ase.getErrorCode());
            logger.info("Error Type:       " + ase.getErrorType());
            logger.info("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            logger.info("Caught an AmazonClientException: ");
            logger.info("Error Message: " + ace.getMessage());
        }
    }
    // 업로드
//    public static void uploadFile(String fileName, String uploadFilePath, byte[] imageByte) {
////        byte[] bI = Base64.getDecoder.decode(base64Data);
//
//
//
//        try {
//            InputStream fis = new ByteArrayInputStream(imageByte);
//
//            AmazonS3 s3 = s3client();
//            ObjectMetadata metadata = new ObjectMetadata();
//            metadata.setContentLength(imageByte.length);
//            metadata.setContentType("image/png");
//            metadata.setCacheControl("public, max-age=31536000");
//            s3.putObject(bucketName, fileName, fis, metadata);
//            s3.setObjectAcl(bucketName, fileName, CannedAccessControlList.PublicRead);
////            File file = new File(uploadFilePath);
////            s3client().putObject(new PutObjectRequest(bucketName, fileName, file));
//            System.out.println("================== Upload File - Done! ==================");
//        } catch (AmazonServiceException ase) {
//            logger.info("Caught an AmazonServiceException from PUT requests, rejected reasons:");
//            logger.info("Error Message:    " + ase.getMessage());
//            logger.info("HTTP Status Code: " + ase.getStatusCode());
//            logger.info("AWS Error Code:   " + ase.getErrorCode());
//            logger.info("Error Type:       " + ase.getErrorType());
//            logger.info("Request ID:       " + ase.getRequestId());
//        } catch (AmazonClientException ace) {
//            logger.info("Caught an AmazonClientException: ");
//            logger.info("Error Message: " + ace.getMessage());
//        }
//    }

    // 다운로드
    public static void downloadFile(String keyName) {

        try {
            System.out.println("Downloading an object");
            S3Object s3object = s3client().getObject(new GetObjectRequest(bucketName, keyName));
            System.out.println("Content-Type: " + s3object.getObjectMetadata().getContentType());
            // .txt 파일이면 파일 읽는 부분
            displayText(s3object.getObjectContent());
            logger.info("================== Downloade File - Done! ==================");
        } catch (AmazonServiceException ase) {
            logger.info("Caught an AmazonServiceException from GET requests, rejected reasons:");
            logger.info("Error Message:    " + ase.getMessage());
            logger.info("HTTP Status Code: " + ase.getStatusCode());
            logger.info("AWS Error Code:   " + ase.getErrorCode());
            logger.info("Error Type:       " + ase.getErrorType());

        } catch (AmazonClientException ace) {
            logger.info("Caught an AmazonClientException: ");
            logger.info("Error Message: " + ace.getMessage());
        }
    }

    // .txt 파일 읽어드리는 함수
    private static void displayText(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        while (true) {
            String line = null;
            try {
                line = reader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (line == null)
                break;
            System.out.println(line);
        }
    }

    // 파일 URL 가져오기
    public static String getFileURL(String fileName) {
        String imageName = (fileName).replace(File.separatorChar, '/');
        String imageUrl = s3client()
                .generatePresignedUrl(new GeneratePresignedUrlRequest(bucketName, imageName)).toString();
        return imageUrl;
    }
}
