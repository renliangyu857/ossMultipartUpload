package com.example.model;

public class MultipartUploadRequest {
    private String bucketName;
    private String objectName;
    private String filePath;

    public MultipartUploadRequest(String bucketName, String objectName, String filePath) {
        this.bucketName = bucketName;
        this.objectName = objectName;
        this.filePath = filePath;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
