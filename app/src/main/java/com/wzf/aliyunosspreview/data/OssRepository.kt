package com.wzf.aliyunosspreview.data

import android.content.Context
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.alibaba.sdk.android.oss.model.ListBucketsRequest
import com.alibaba.sdk.android.oss.model.ListObjectsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OssRepository(private val context: Context) {
    private fun buildClient(credentials: OssCredentials): OSSClient {
        val provider = OSSPlainTextAKSKCredentialProvider(
            credentials.accessKeyId,
            credentials.accessKeySecret,
        )
        return OSSClient(context, credentials.endpoint, provider)
    }

    suspend fun listBuckets(credentials: OssCredentials): List<OssBucket> = withContext(Dispatchers.IO) {
        val client = buildClient(credentials)
        val request = ListBucketsRequest()
        val result = client.listBuckets(request)
        result.buckets
            .map { OssBucket(name = it.name, location = it.location) }
            .filter { it.location == null || it.location == credentials.region }
            .sortedBy { it.name }
    }

    suspend fun listObjects(
        credentials: OssCredentials,
        bucketName: String,
        prefix: String,
    ): List<OssObjectEntry> = withContext(Dispatchers.IO) {
        val client = buildClient(credentials)
        val request = ListObjectsRequest(bucketName).apply {
            setPrefix(prefix)
            setDelimiter("/")
            setMaxKeys(1000)
        }
        val result = client.listObjects(request)
        val directories = result.commonPrefixes.orEmpty().map { key ->
            val displayName = key.removePrefix(prefix).trimEnd('/')
            OssObjectEntry(
                key = key,
                displayName = displayName,
                isDirectory = true,
                size = null,
                lastModified = null,
            )
        }
        val files = result.objectSummarys.orEmpty().map { summary ->
            val displayName = summary.key.removePrefix(prefix)
            OssObjectEntry(
                key = summary.key,
                displayName = displayName,
                isDirectory = false,
                size = summary.size,
                lastModified = summary.lastModified,
            )
        }
        (directories + files).sortedWith(
            compareBy<OssObjectEntry> { !it.isDirectory }.thenBy { it.displayName }
        )
    }
}
