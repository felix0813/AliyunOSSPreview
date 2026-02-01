package com.wzf.aliyunosspreview.data

import android.content.Context
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.alibaba.sdk.android.oss.model.GetObjectRequest
import com.alibaba.sdk.android.oss.model.DeleteObjectRequest
import com.alibaba.sdk.android.oss.model.ListBucketsRequest
import com.alibaba.sdk.android.oss.model.ListObjectsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class OssRepository(private val context: Context) {
    private fun buildClient(credentials: OssCredentials): OSSClient {
        val provider = OSSPlainTextAKSKCredentialProvider(
            credentials.accessKeyId,
            credentials.accessKeySecret,
        )
        return OSSClient(context, credentials.endpoint, provider)
    }

    suspend fun listBuckets(credentials: OssCredentials): List<OssBucket> =
        withContext(Dispatchers.IO) {
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
            delimiter = "/"
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
        val files = result.objectSummaries.orEmpty().map { summary ->
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

    suspend fun listAllObjects(
        credentials: OssCredentials,
        bucketName: String,
        prefix: String,
    ): List<String> = withContext(Dispatchers.IO) {
        val client = buildClient(credentials)
        val keys = mutableListOf<String>()
        var marker: String? = null
        var truncated: Boolean
        do {
            val request = ListObjectsRequest(bucketName).apply {
                setPrefix(prefix)
                setMaxKeys(1000)
                marker?.let { setMarker(it) }
            }
            val result = client.listObjects(request)
            val objectKeys = result.objectSummaries.orEmpty()
                .mapNotNull { it.key }
                .filter { it.isNotBlank() && !it.endsWith("/") }
            keys.addAll(objectKeys)
            marker = result.nextMarker
            truncated = result.isTruncated
        } while (truncated)
        keys
    }

    suspend fun listAllObjectEntries(
        credentials: OssCredentials,
        bucketName: String,
        prefix: String,
    ): List<OssObjectEntry> = withContext(Dispatchers.IO) {
        val client = buildClient(credentials)
        val entries = mutableListOf<OssObjectEntry>()
        var marker: String? = null
        var truncated: Boolean
        do {
            val request = ListObjectsRequest(bucketName).apply {
                setPrefix(prefix)
                setMaxKeys(1000)
                marker?.let { setMarker(it) }
            }
            val result = client.listObjects(request)
            result.objectSummaries.orEmpty()
                .mapNotNull { summary ->
                    val key = summary.key
                    if (key.isNullOrBlank() || key.endsWith("/")) {
                        null
                    } else {
                        OssObjectEntry(
                            key = key,
                            displayName = key.substringAfterLast('/'),
                            isDirectory = false,
                            size = summary.size,
                            lastModified = summary.lastModified,
                        )
                    }
                }
                .forEach { entries.add(it) }
            marker = result.nextMarker
            truncated = result.isTruncated
        } while (truncated)
        entries
    }

    suspend fun fetchObjectText(
        credentials: OssCredentials,
        bucketName: String,
        key: String,
    ): String = withContext(Dispatchers.IO) {
        val client = buildClient(credentials)
        val ossObject = client.getObject(GetObjectRequest(bucketName, key))
        ossObject.objectContent.use { input ->
            input.bufferedReader().use { reader -> reader.readText() }
        }
    }

    suspend fun downloadObject(
        credentials: OssCredentials,
        bucketName: String,
        key: String,
        targetFile: File,
    ) = withContext(Dispatchers.IO) {
        val client = buildClient(credentials)
        targetFile.parentFile?.mkdirs()
        val ossObject = client.getObject(GetObjectRequest(bucketName, key))
        ossObject.objectContent.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    suspend fun deleteObjects(
        credentials: OssCredentials,
        bucketName: String,
        keys: List<String>,
    ) = withContext(Dispatchers.IO) {
        if (keys.isEmpty()) return@withContext
        val client = buildClient(credentials)
        keys.forEach { key ->
            client.deleteObject(DeleteObjectRequest(bucketName, key))
        }
    }
}
