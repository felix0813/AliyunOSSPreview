package com.wzf.aliyunosspreview

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.wzf.aliyunosspreview.data.OssBucket
import com.wzf.aliyunosspreview.data.OssCredentials
import com.wzf.aliyunosspreview.data.OssObjectEntry
import com.wzf.aliyunosspreview.data.OssPreferences
import com.wzf.aliyunosspreview.data.OssRepository
import com.wzf.aliyunosspreview.ui.screens.BucketList
import com.wzf.aliyunosspreview.ui.screens.LoginScreen
import com.wzf.aliyunosspreview.ui.screens.MarkdownPreviewScreen
import com.wzf.aliyunosspreview.ui.screens.ObjectList
import com.wzf.aliyunosspreview.ui.screens.OssTopBar
import com.wzf.aliyunosspreview.ui.theme.AliyunOSSPreviewTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.net.toUri

enum class ConflictAction {
    OVERWRITE,
    RENAME,
    SKIP
}

data class DownloadConflict(
    val entry: OssObjectEntry,
    val targetFile: File
)

data class RenameRequest(
    val entry: OssObjectEntry,
    val targetFile: File
)

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
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var markdownTitle by remember { mutableStateOf<String?>(null) }
    var markdownContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var copyToSystemDownloads by remember { mutableStateOf(false) }
    var showApkDownloadConfirm by remember { mutableStateOf(false) }
    var showApkInstallConfirm by remember { mutableStateOf(false) }
    var showApkInstallPermission by remember { mutableStateOf(false) }
    var pendingApkEntry by remember { mutableStateOf<OssObjectEntry?>(null) }
    var pendingApkFile by remember { mutableStateOf<File?>(null) }
    var activeConflict by remember { mutableStateOf<DownloadConflict?>(null) }
    var conflictDeferred by remember { mutableStateOf<CompletableDeferred<ConflictAction>?>(null) }
    var activeRename by remember { mutableStateOf<RenameRequest?>(null) }
    var renameDeferred by remember { mutableStateOf<CompletableDeferred<File?>?>(null) }
    var renameInput by remember { mutableStateOf("") }

    fun resetSelection() {
        selectionMode = false
        selectedKeys = emptySet()
    }

    fun suggestRename(file: File): String {
        val name = file.name
        val dotIndex = name.lastIndexOf('.')
        val base = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext = if (dotIndex > 0) name.substring(dotIndex) else ""
        return "$base (1)$ext"
    }

    fun isSameFile(localFile: File, entry: OssObjectEntry): Boolean {
        val remoteSize = entry.size
        return remoteSize != null && localFile.length() == remoteSize
    }

    suspend fun resolveConflict(entry: OssObjectEntry, targetFile: File): ConflictAction {
        val deferred = CompletableDeferred<ConflictAction>()
        conflictDeferred = deferred
        activeConflict = DownloadConflict(entry, targetFile)
        return deferred.await()
    }

    suspend fun resolveRename(entry: OssObjectEntry, targetFile: File): File? {
        val deferred = CompletableDeferred<File?>()
        renameDeferred = deferred
        activeRename = RenameRequest(entry, targetFile)
        renameInput = suggestRename(targetFile)
        return deferred.await()
    }

    suspend fun resolveTargetFile(
        entry: OssObjectEntry,
        initialFile: File,
    ): Pair<File, Boolean>? {
        var targetFile = initialFile
        while (true) {
            if (!targetFile.exists()) {
                return targetFile to true
            }
            if (isSameFile(targetFile, entry)) {
                return targetFile to false
            }
            when (resolveConflict(entry, targetFile)) {
                ConflictAction.OVERWRITE -> return targetFile to true
                ConflictAction.SKIP -> return null
                ConflictAction.RENAME -> {
                    val renamed = resolveRename(entry, targetFile) ?: return null
                    targetFile = renamed
                }
            }
        }
    }

    fun canInstallApk(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri()
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun installApk(apkFile: File) {
        if (!apkFile.exists()) return
        if (!canInstallApk()) {
            pendingApkFile = apkFile
            showApkInstallPermission = true
            return
        }
        val apkUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun loadBuckets(targetCredentials: OssCredentials) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            infoMessage = null
            runCatching { repository.listBuckets(targetCredentials) }
                .onSuccess {
                    buckets = it
                    isLoading = false
                }
                .onFailure { throwable ->
                    errorMessage = throwable.message ?: "Failed to load buckets"
                    isLoading = false
                }
        }
    }

    fun loadObjects(targetCredentials: OssCredentials, bucket: OssBucket, targetPrefix: String) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            infoMessage = null
            runCatching { repository.listObjects(targetCredentials, bucket.name, targetPrefix) }
                .onSuccess {
                    objects = it
                    prefix = targetPrefix
                    resetSelection()
                    isLoading = false
                }
                .onFailure { throwable ->
                    errorMessage = throwable.message ?: "Failed to load objects"
                    isLoading = false
                }
        }
    }

    fun loadMarkdown(targetCredentials: OssCredentials, bucket: OssBucket, key: String) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            infoMessage = null
            runCatching { repository.fetchObjectText(targetCredentials, bucket.name, key) }
                .onSuccess { content ->
                    markdownTitle = key.substringAfterLast('/')
                    markdownContent = content
                    isLoading = false
                }
                .onFailure { throwable ->
                    errorMessage = throwable.message ?: "Failed to load markdown"
                    isLoading = false
                }
        }
    }

    fun downloadSelection(
        targetCredentials: OssCredentials,
        bucket: OssBucket,
        copyToSystemDownloads: Boolean,
    ) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            infoMessage = null
            val appDownloadRoot = context.getExternalFilesDir("downloads") ?: context.filesDir
            val appBucketDir = File(appDownloadRoot, bucket.name)
            val systemBucketDir = if (copyToSystemDownloads) {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    bucket.name
                )
            } else {
                null
            }
            runCatching {
                val entriesToDownload = mutableMapOf<String, OssObjectEntry>()
                selectedKeys.forEach { key ->
                    val entry = objects.find { it.key == key }
                    if (entry?.isDirectory == true) {
                        repository.listAllObjectEntries(targetCredentials, bucket.name, key)
                            .forEach { child -> entriesToDownload[child.key] = child }
                    } else {
                        val resolvedEntry = entry ?: OssObjectEntry(
                            key = key,
                            displayName = key.substringAfterLast('/'),
                            isDirectory = false,
                            size = null,
                            lastModified = null,
                        )
                        entriesToDownload[resolvedEntry.key] = resolvedEntry
                    }
                }
                var downloadedCount = 0
                var reusedCount = 0
                var skippedCount = 0
                entriesToDownload.values.forEach { entry ->
                    val appTargetFile = File(appBucketDir, entry.key)
                    val resolved = resolveTargetFile(entry, appTargetFile)
                    if (resolved == null) {
                        skippedCount += 1
                        return@forEach
                    }
                    val (targetFile, shouldDownload) = resolved
                    if (shouldDownload) {
                        repository.downloadObject(targetCredentials, bucket.name, entry.key, targetFile)
                        downloadedCount += 1
                    } else {
                        reusedCount += 1
                    }
                    systemBucketDir?.let { systemDir ->
                        val relativePath = appBucketDir.toURI().relativize(targetFile.toURI()).path
                        val systemTargetFile = File(systemDir, relativePath)
                        systemTargetFile.parentFile?.mkdirs()
                        targetFile.copyTo(systemTargetFile, overwrite = true)
                    }
                }
                infoMessage = buildString {
                    append("Downloaded $downloadedCount files to ${appBucketDir.absolutePath}")
                    if (reusedCount > 0) append(", reused $reusedCount existing files")
                    if (skippedCount > 0) append(", skipped $skippedCount files")
                    if (systemBucketDir != null) {
                        append(" and copied to ${systemBucketDir.absolutePath}")
                    }
                }
                resetSelection()
                isLoading = false
            }.onFailure { throwable ->
                errorMessage = throwable.message ?: "Download failed"
                isLoading = false
            }
        }
    }

    fun deleteSelection(targetCredentials: OssCredentials, bucket: OssBucket) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            infoMessage = null
            runCatching {
                val keysToDelete = mutableSetOf<String>()
                selectedKeys.forEach { key ->
                    val entry = objects.find { it.key == key }
                    if (entry?.isDirectory == true) {
                        keysToDelete.addAll(repository.listAllObjects(targetCredentials, bucket.name, key))
                        if (key.endsWith("/")) {
                            keysToDelete.add(key)
                        }
                    } else {
                        keysToDelete.add(key)
                    }
                }
                repository.deleteObjects(targetCredentials, bucket.name, keysToDelete.toList())
                infoMessage = "Deleted ${keysToDelete.size} files"
                resetSelection()
                loadObjects(targetCredentials, bucket, prefix)
            }.onFailure { throwable ->
                errorMessage = throwable.message ?: "Delete failed"
                isLoading = false
            }
        }
    }

    fun downloadAndInstallApk(
        targetCredentials: OssCredentials,
        bucket: OssBucket,
        entry: OssObjectEntry
    ) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            infoMessage = null
            val appDownloadRoot = context.getExternalFilesDir("downloads") ?: context.filesDir
            val appTargetFile = File(appDownloadRoot, entry.key)
            runCatching {
                val resolved = resolveTargetFile(entry, appTargetFile)
                if (resolved == null) {
                    isLoading = false
                    return@runCatching
                }
                val (targetFile, shouldDownload) = resolved
                if (shouldDownload) {
                    repository.downloadObject(targetCredentials, bucket.name, entry.key, targetFile)
                    infoMessage = "Downloaded APK to ${targetFile.absolutePath}"
                } else {
                    infoMessage = "APK already downloaded at ${targetFile.absolutePath}"
                }
                pendingApkFile = targetFile
                showApkInstallConfirm = true
                isLoading = false
            }.onFailure { throwable ->
                errorMessage = throwable.message ?: "Download failed"
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

    fun handleObjectListBack() {
        if (prefix.isBlank()) {
            selectedBucket = null
            objects = emptyList()
            resetSelection()
        } else {
            val newPrefix = prefix.trimEnd('/')
                .substringBeforeLast('/', missingDelimiterValue = "")
                .let { if (it.isBlank()) "" else "$it/" }
            credentials?.let { loadObjects(it, selectedBucket!!, newPrefix) }
        }
    }

    if (markdownContent != null) {
        BackHandler {
            markdownTitle = null
            markdownContent = null
        }
        MarkdownPreviewScreen(
            title = markdownTitle ?: "Markdown",
            content = markdownContent.orEmpty(),
            onBack = {
                markdownTitle = null
                markdownContent = null
            }
        )
        return
    }

    BackHandler(enabled = selectedBucket != null) {
        if (selectedBucket != null) {
            handleObjectListBack()
        }
    }

    Scaffold(
        topBar = {
            OssTopBar(
                title = when {
                    credentials == null -> "OSS Login"
                    selectedBucket == null -> "Buckets"
                    else -> selectedBucket?.name.orEmpty()
                },
                showBack = selectedBucket != null,
                onBack = {
                    if (selectedBucket != null) {
                        handleObjectListBack()
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
                        infoMessage = infoMessage,
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
                        infoMessage = infoMessage,
                        selectionMode = selectionMode,
                        selectedKeys = selectedKeys,
                        onSelectionModeChange = { enabled ->
                            selectionMode = enabled
                            if (!enabled) {
                                selectedKeys = emptySet()
                            }
                        },
                        onToggleSelection = { key ->
                            selectedKeys = if (selectedKeys.contains(key)) {
                                selectedKeys - key
                            } else {
                                selectedKeys + key
                            }
                        },
                        onDownloadSelected = {
                            showDownloadDialog = true
                        },
                        onDeleteSelected = {
                            credentials?.let { deleteSelection(it, selectedBucket!!) }
                        },
                        onFolderClick = { entry ->
                            credentials?.let { loadObjects(it, selectedBucket!!, entry.key) }
                        },
                        onMarkdownClick = { entry ->
                            credentials?.let { loadMarkdown(it, selectedBucket!!, entry.key) }
                        },
                        onApkClick = { entry ->
                            pendingApkEntry = entry
                            showApkDownloadConfirm = true
                        }
                    )
                }
            }
        }
    }

    if (showApkDownloadConfirm) {
        AlertDialog(
            onDismissRequest = {
                showApkDownloadConfirm = false
                pendingApkEntry = null
            },
            title = { Text(text = "Download APK") },
            text = {
                Text(
                    text = "Download ${pendingApkEntry?.key?.substringAfterLast('/') ?: "this APK"}?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetCredentials = credentials
                        val targetBucket = selectedBucket
                        val targetEntry = pendingApkEntry
                        if (targetCredentials != null && targetBucket != null && targetEntry != null) {
                            downloadAndInstallApk(targetCredentials, targetBucket, targetEntry)
                        }
                        showApkDownloadConfirm = false
                        pendingApkEntry = null
                    },
                    enabled = !isLoading
                ) {
                    Text(text = "Download")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showApkDownloadConfirm = false
                        pendingApkEntry = null
                    },
                    enabled = !isLoading
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    if (showApkInstallConfirm) {
        AlertDialog(
            onDismissRequest = {
                showApkInstallConfirm = false
                pendingApkFile = null
            },
            title = { Text(text = "Install APK") },
            text = {
                Text(
                    text = "Download complete. Install now?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingApkFile?.let { installApk(it) }
                        showApkInstallConfirm = false
                        pendingApkFile = null
                    },
                    enabled = !isLoading
                ) {
                    Text(text = "Install")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showApkInstallConfirm = false
                        pendingApkFile = null
                    },
                    enabled = !isLoading
                ) {
                    Text(text = "Later")
                }
            }
        )
    }

    if (showApkInstallPermission) {
        AlertDialog(
            onDismissRequest = {
                showApkInstallPermission = false
            },
            title = { Text(text = "Allow app installs") },
            text = {
                Text(
                    text = "To install this APK, allow installs from this app in system settings."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showApkInstallPermission = false
                        openInstallPermissionSettings()
                    },
                    enabled = !isLoading
                ) {
                    Text(text = "Open settings")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showApkInstallPermission = false
                    },
                    enabled = !isLoading
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    if (activeConflict != null) {
        val conflict = activeConflict!!
        AlertDialog(
            onDismissRequest = {
                conflictDeferred?.complete(ConflictAction.SKIP)
                conflictDeferred = null
                activeConflict = null
            },
            title = { Text(text = "File already exists") },
            text = {
                Text(
                    text = "A file named ${conflict.targetFile.name} already exists. What do you want to do?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        conflictDeferred?.complete(ConflictAction.OVERWRITE)
                        conflictDeferred = null
                        activeConflict = null
                    },
                    enabled = !isLoading
                ) {
                    Text(text = "Overwrite")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            conflictDeferred?.complete(ConflictAction.RENAME)
                            conflictDeferred = null
                            activeConflict = null
                        },
                        enabled = !isLoading
                    ) {
                        Text(text = "Rename")
                    }
                    Button(
                        onClick = {
                            conflictDeferred?.complete(ConflictAction.SKIP)
                            conflictDeferred = null
                            activeConflict = null
                        },
                        enabled = !isLoading
                    ) {
                        Text(text = "Skip")
                    }
                }
            }
        )
    }

    if (activeRename != null) {
        val renameRequest = activeRename!!
        AlertDialog(
            onDismissRequest = {
                renameDeferred?.complete(null)
                renameDeferred = null
                activeRename = null
            },
            title = { Text(text = "Rename file") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Choose a new name for ${renameRequest.targetFile.name}:")
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameInput.trim()
                        val newFile = if (trimmed.isBlank()) {
                            null
                        } else {
                            File(renameRequest.targetFile.parentFile, trimmed)
                        }
                        renameDeferred?.complete(newFile)
                        renameDeferred = null
                        activeRename = null
                    },
                    enabled = !isLoading && renameInput.isNotBlank()
                ) {
                    Text(text = "Save")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        renameDeferred?.complete(null)
                        renameDeferred = null
                        activeRename = null
                    },
                    enabled = !isLoading
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text(text = "Download files") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Save files to the app folder. Do you want to copy them to the system Downloads folder too?")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { copyToSystemDownloads = !copyToSystemDownloads }
                    ) {
                        Checkbox(
                            checked = copyToSystemDownloads,
                            onCheckedChange = { copyToSystemDownloads = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Also copy to system Downloads")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetCredentials = credentials
                        val targetBucket = selectedBucket
                        if (targetCredentials != null && targetBucket != null) {
                            downloadSelection(
                                targetCredentials,
                                targetBucket,
                                copyToSystemDownloads,
                            )
                        }
                        showDownloadDialog = false
                    },
                    enabled = !isLoading
                ) {
                    Text(text = "Download")
                }
            },
            dismissButton = {
                Button(onClick = { showDownloadDialog = false }, enabled = !isLoading) {
                    Text(text = "Cancel")
                }
            }
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
