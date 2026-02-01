package com.wzf.aliyunosspreview.data

import java.util.Date

data class OssCredentials(
    val accessKeyId: String,
    val accessKeySecret: String,
    val endpoint: String,
    val region: String,
)

data class OssBucket(
    val name: String,
    val location: String?,
)

data class OssObjectEntry(
    val key: String,
    val displayName: String,
    val isDirectory: Boolean,
    val size: Long?,
    val lastModified: Date?,
)
