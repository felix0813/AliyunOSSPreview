package com.wzf.aliyunosspreview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.wzf.aliyunosspreview.data.OssBucket
import com.wzf.aliyunosspreview.data.OssCredentials
import com.wzf.aliyunosspreview.data.OssObjectEntry
import com.wzf.aliyunosspreview.data.OssPreferences
import com.wzf.aliyunosspreview.data.OssRepository
import com.wzf.aliyunosspreview.ui.theme.AliyunOSSPreviewTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AliyunOSSPreviewTheme {
                OssApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun OssApp() {
    val context = LocalContext.current
    val repository = remember { OssRepository(context) }
    val preferences = remember { OssPreferences(context) }
    val coroutineScope = rememberCoroutineScope()

    var credentials by remember { mutableStateOf<OssCredentials?>(null) }
    var buckets by remember { mutableStateOf<List<OssBucket>>(emptyList()) }
    var selectedBucket by remember { mutableStateOf<OssBucket?>(null) }
    var prefix by remember { mutableStateOf("") }
    var objects by remember { mutableStateOf<List<OssObjectEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadBuckets(targetCredentials: OssCredentials) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            runCatching { repository.listBuckets(targetCredentials) }
                .onSuccess {
                    buckets = it
                    isLoading = false
                }
                .onFailure { throwable ->
                    errorMessage = throwable.message ?: "无法获取存储桶列表"
                    isLoading = false
                }
        }
    }

    fun loadObjects(targetCredentials: OssCredentials, bucket: OssBucket, targetPrefix: String) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            runCatching { repository.listObjects(targetCredentials, bucket.name, targetPrefix) }
                .onSuccess {
                    objects = it
                    prefix = targetPrefix
                    isLoading = false
                }
                .onFailure { throwable ->
                    errorMessage = throwable.message ?: "无法获取文件列表"
                    isLoading = false
                }
        }
    }

    LaunchedEffect(Unit) {
        val saved = preferences.loadCredentials()
        if (saved != null) {
            credentials = saved
            loadBuckets(saved)
        } else {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            OssTopBar(
                title = when {
                    credentials == null -> "OSS 登录"
                    selectedBucket == null -> "存储桶"
                    else -> selectedBucket?.name.orEmpty()
                },
                showBack = selectedBucket != null,
                onBack = {
                    if (selectedBucket != null) {
                        if (prefix.isBlank()) {
                            selectedBucket = null
                            objects = emptyList()
                        } else {
                            val newPrefix = prefix.trimEnd('/')
                                .substringBeforeLast('/', missingDelimiterValue = "")
                                .let { if (it.isBlank()) "" else "$it/" }
                            credentials?.let { loadObjects(it, selectedBucket!!, newPrefix) }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                credentials == null -> {
                    LoginScreen(
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onLogin = { newCredentials ->
                            coroutineScope.launch {
                                isLoading = true
                                errorMessage = null
                                preferences.saveCredentials(newCredentials)
                                credentials = newCredentials
                                loadBuckets(newCredentials)
                            }
                        }
                    )
                }
                selectedBucket == null -> {
                    BucketList(
                        buckets = buckets,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onRefresh = { credentials?.let { loadBuckets(it) } },
                        onBucketSelected = { bucket ->
                            selectedBucket = bucket
                            credentials?.let { loadObjects(it, bucket, "") }
                        }
                    )
                }
                else -> {
                    ObjectList(
                        bucket = selectedBucket!!,
                        prefix = prefix,
                        objects = objects,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onFolderClick = { entry ->
                            credentials?.let { loadObjects(it, selectedBucket!!, entry.key) }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OssTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(text = title) },
        navigationIcon = if (showBack) {
            {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clickable { onBack() }
                )
            }
        } else {
            {}
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (OssCredentials) -> Unit,
) {
    var accessKeyId by rememberSaveable { mutableStateOf("") }
    var accessKeySecret by rememberSaveable { mutableStateOf("") }
    var endpoint by rememberSaveable { mutableStateOf("https://oss-cn-hangzhou.aliyuncs.com") }
    var region by rememberSaveable { mutableStateOf("oss-cn-hangzhou") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "使用 AccessKey 登录 OSS", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = accessKeyId,
            onValueChange = { accessKeyId = it },
            label = { Text("AccessKey ID") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = accessKeySecret,
            onValueChange = { accessKeySecret = it },
            label = { Text("AccessKey Secret") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text("Endpoint") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = region,
            onValueChange = { region = it },
            label = { Text("Region") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onLogin(
                    OssCredentials(
                        accessKeyId = accessKeyId.trim(),
                        accessKeySecret = accessKeySecret.trim(),
                        endpoint = endpoint.trim(),
                        region = region.trim(),
                    )
                )
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (isLoading) "登录中..." else "登录")
        }
        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun BucketList(
    buckets: List<OssBucket>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onBucketSelected: (OssBucket) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onRefresh, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
            Text(text = if (isLoading) "加载中..." else "刷新存储桶")
        }
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }
        errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(buckets, key = { it.name }) { bucket ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBucketSelected(bucket) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(text = bucket.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "区域: ${bucket.location ?: "未知"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun ObjectList(
    bucket: OssBucket,
    prefix: String,
    objects: List<OssObjectEntry>,
    isLoading: Boolean,
    errorMessage: String?,
    onFolderClick: (OssObjectEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (prefix.isBlank()) "当前路径: /" else "当前路径: /$prefix",
            style = MaterialTheme.typography.bodyMedium
        )
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }
        errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(objects, key = { it.key }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = entry.isDirectory) {
                            onFolderClick(entry)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = entry.displayName, style = MaterialTheme.typography.bodyLarge)
                        if (!entry.isDirectory) {
                            Text(
                                text = "大小: ${entry.size ?: 0} bytes",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "桶: ${bucket.name}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AliyunOSSPreviewTheme {
        OssApp()
    }
}
