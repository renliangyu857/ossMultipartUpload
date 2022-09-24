package com.example;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.*;
import com.example.model.MultipartUploadRequest;
import com.example.model.MultipartUploadResult;
import com.example.utils.NamedThreadFactory;
import io.netty.util.NettyRuntime;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 待完成：1、提交到线程池的任务被拒绝后，1）延时等待 2）备用线程池选择
 *        2、分片大小和线程个数调优
 *        3、断点续传
 *        4、失败后碎片异步清理
 */
public class AsyncPartUploadWorker {
    private static final int ASYNC_UPLOAD_BUFFER_LIMIT = 20;
    private static final int THREAD_SIZE = NettyRuntime.availableProcessors() * 2; //待调优 todo
    private static final long PART_SIZE = 50 * 1024 * 1024L;//50MB //待调优 todo

    private final ThreadPoolExecutor threadPoolExecutor;

    private final OSS ossClient;

    private final AsyncPartCommitWorker asyncPartCommitWorker;

    public AsyncPartUploadWorker(final OSS ossClient, final AsyncPartCommitWorker asyncPartCommitWorker) {
        this.ossClient = ossClient;
        this.asyncPartCommitWorker = asyncPartCommitWorker;
        threadPoolExecutor = new ThreadPoolExecutor(THREAD_SIZE,
                THREAD_SIZE,
                60 * 2,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(ASYNC_UPLOAD_BUFFER_LIMIT),
                new NamedThreadFactory("AsyncPartUploadWorker", THREAD_SIZE, true)
        );
    }

    public void asyncUpload(final MultipartUploadRequest uploadRequest) {
        final File sampleFile = new File(uploadRequest.getFilePath());
        if (sampleFile.length() <= PART_SIZE) {
            doSimpleUpload(uploadRequest, sampleFile);
        } else {
            doMultipartUpload(uploadRequest, sampleFile);
        }
    }

    private void doSimpleUpload(MultipartUploadRequest uploadRequest, File sampleFile) {
        threadPoolExecutor.submit(new SimpleUploadRunnable(new PutObjectRequest(uploadRequest.getBucketName(), uploadRequest.getObjectName(), sampleFile)));
    }

    private void doMultipartUpload(MultipartUploadRequest uploadRequest, File sampleFile) {
        // 创建InitiateMultipartUploadRequest对象。
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(uploadRequest.getBucketName(), uploadRequest.getObjectName());

        // 初始化分片。
        InitiateMultipartUploadResult upresult = ossClient.initiateMultipartUpload(request);
        // 返回uploadId，它是分片上传事件的唯一标识。可以根据该uploadId发起相关的操作，例如取消分片上传、查询分片上传等。
        String uploadId = upresult.getUploadId();

        // partETags是PartETag的集合。PartETag由分片的ETag和分片号组成。
        // 每个分片的大小，用于计算文件有多少个分片。单位为字节。
        // 填写本地文件的完整路径。如果未指定本地路径，则默认从示例程序所属项目对应本地路径中上传文件。
        long fileLength = sampleFile.length();
        int partCount = (int) (fileLength / PART_SIZE);
        if (fileLength % PART_SIZE != 0) {
            partCount++;
        }
        // 遍历分片上传。
        try {
            for (int i = 0; i < partCount; i++) {
                long startPos = i * PART_SIZE;
                long curPartSize = (i + 1 == partCount) ? (fileLength - startPos) : PART_SIZE;
                InputStream instream = new FileInputStream(sampleFile);
                // 跳过已经上传的分片。
                instream.skip(startPos);
                UploadPartRequest uploadPartRequest = new UploadPartRequest();
                uploadPartRequest.setBucketName(uploadPartRequest.getBucketName());
                uploadPartRequest.setKey(uploadRequest.getObjectName());
                uploadPartRequest.setUploadId(uploadId);
                uploadPartRequest.setInputStream(instream);
                // 设置分片大小。除了最后一个分片没有大小限制，其他的分片最小为100 KB。
                uploadPartRequest.setPartSize(curPartSize);
                // 设置分片号。每一个上传的分片都有一个分片号，取值范围是1~10000，如果超出此范围，OSS将返回InvalidArgument错误码。
                uploadPartRequest.setPartNumber(i + 1);
                threadPoolExecutor.submit(new PartUploadRunnable(uploadPartRequest, uploadRequest.getObjectName(), partCount));
            }
        } catch (Exception e) {

        }
    }


    private class SimpleUploadRunnable implements Runnable {
        private PutObjectRequest putObjectRequest;

        public SimpleUploadRunnable(final PutObjectRequest putObjectRequest) {
            this.putObjectRequest = putObjectRequest;
        }

        @Override
        public void run() {
            ossClient.putObject(putObjectRequest);
        }
    }


    private class PartUploadRunnable implements Runnable {
        private UploadPartRequest uploadPartRequest;
        private String objectName;
        private int totalSize;

        public PartUploadRunnable(final UploadPartRequest partRequest, final String objectName, final int totalSize) {
            this.uploadPartRequest = partRequest;
            this.objectName = objectName;
            this.totalSize = totalSize;
        }

        @Override
        public void run() {
            // 每个分片不需要按顺序上传，甚至可以在不同客户端上传，OSS会按照分片号排序组成完整的文件。
            UploadPartResult uploadPartResult = ossClient.uploadPart(uploadPartRequest);
            // 每次上传分片之后，OSS的返回结果包含PartETag。PartETag将被保存在partETags中。
            asyncPartCommitWorker.addToCommitQueue(new MultipartUploadResult(uploadPartRequest.getBucketName(), objectName, uploadPartRequest.getUploadId(), uploadPartResult.getPartETag()), totalSize);
        }
    }

}
