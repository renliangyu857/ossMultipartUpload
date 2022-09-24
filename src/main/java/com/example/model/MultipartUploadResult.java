package com.example.model;


import com.aliyun.oss.model.PartETag;

public class MultipartUploadResult {
    private String bucketName;
    private String objectName;
    private String uploadId;
    private PartETag ossPartETag;

    public MultipartUploadResult(String bucketName,String objectName, String uploadId, PartETag ossPartETag) {
        this.bucketName = bucketName;
        this.objectName = objectName;
        this.uploadId = uploadId;
        this.ossPartETag = ossPartETag;
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

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public PartETag getOssPartETag() {
        return ossPartETag;
    }

    public void setOssPartETag(PartETag ossPartETag) {
        this.ossPartETag = ossPartETag;
    }
}
