package com.example;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.example.model.MultipartUploadRequest;

/**
 * 待完成：1、从rocketmq拉取异步任务
 *        2、文件下载
 */
public class MultiUploadTest {
    public static void main(String[] args) throws Exception {
        // Endpoint以华东1（杭州）为例，其它Region请按实际情况填写。
        String endpoint = "https://oss-cn-hangzhou.aliyuncs.com";
        // 阿里云账号AccessKey拥有所有API的访问权限，风险很高
        String accessKeyId = "yourAccessKeyId";
        String accessKeySecret = "yourAccessKeySecret";
        // 填写Bucket名称，例如examplebucket。
        String bucketName = "examplebucket";
        // 填写Object完整路径，例如exampledir/exampleobject.txt。Object完整路径中不能包含Bucket名称。
        String objectName = "exampledir/exampleobject.txt";

        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        AsyncPartCommitWorker asyncPartCommitWorker = new AsyncPartCommitWorker(ossClient);
        AsyncPartUploadWorker partUploadWorker = new AsyncPartUploadWorker(ossClient, asyncPartCommitWorker);
        partUploadWorker.asyncUpload(new MultipartUploadRequest(bucketName, objectName, "D:\\code\\work\\test.pdf"));
    }
}
