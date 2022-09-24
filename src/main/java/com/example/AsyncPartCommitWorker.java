package com.example;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.PartETag;
import com.example.model.MultipartUploadResult;
import com.example.utils.CollectionUtils;
import com.example.utils.NamedThreadFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class AsyncPartCommitWorker {
    private static final int DEFAULT_RESOURCE_SIZE = 8;
    private static final int MAX_RESOURCE_SIZE = 16;
    private final Map<String, List<MultipartUploadResult>> uploadResultMap;

    private final ScheduledExecutorService scheduledExecutor;

    private final OSS ossClient;

    public AsyncPartCommitWorker(OSS ossClient) {
        this.ossClient = ossClient;
        uploadResultMap = new ConcurrentHashMap<>(DEFAULT_RESOURCE_SIZE);

        ThreadFactory threadFactory = new NamedThreadFactory("AsyncCommitWorker", 2, true);
        scheduledExecutor = new ScheduledThreadPoolExecutor(2, threadFactory);
        scheduledExecutor.scheduleAtFixedRate(this::doCommit, 10, 1000, TimeUnit.MILLISECONDS);
    }

    public void addToCommitQueue(MultipartUploadResult uploadResult, int totalSize) {
        CollectionUtils.computeIfAbsent(uploadResultMap, uploadResult.getUploadId(), key -> new ArrayList<>()).add(uploadResult);
        if (uploadResultMap.get(uploadResult.getUploadId()).size() < totalSize) {
            return;
        }
        CompletableFuture.runAsync(this::doCommit, scheduledExecutor)
                .thenRun(() -> addToCommitQueue(uploadResult, totalSize));
    }

    private void doCommit() {
        uploadResultMap.forEach(this::dealWithGroupedUploads);
    }

    private void dealWithGroupedUploads(String uploadId, List<MultipartUploadResult> uploadResults) {
        if (uploadId == null || uploadResults.isEmpty()) {
            return;
        }
        //1 complete multi part upload
        completeMultipartUpload(uploadResults);
        //2 send complete topic to model repository
        //3 remove key
        uploadResults.remove(uploadId);
    }

    private void completeMultipartUpload(List<MultipartUploadResult> uploadResults) {
        String bucketName = uploadResults.get(0).getBucketName();
        String objectName = uploadResults.get(0).getObjectName();
        String uploadId = uploadResults.get(0).getUploadId();
        List<PartETag> partETags = uploadResults.stream().map(MultipartUploadResult::getOssPartETag).collect(Collectors.toList());
        CompleteMultipartUploadRequest completeMultipartUploadRequest =
                new CompleteMultipartUploadRequest(bucketName, objectName, uploadId, partETags);

        // 完成分片上传。
        ossClient.completeMultipartUpload(completeMultipartUploadRequest);
    }
}
