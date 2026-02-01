package com.wzf.aliyunosspreview.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wzf.aliyunosspreview.data.OssBucket
import com.wzf.aliyunosspreview.data.OssObjectEntry

@Composable
fun ObjectList(
    bucket: OssBucket,
    prefix: String,
    objects: List<OssObjectEntry>,
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    selectionMode: Boolean,
    selectedKeys: Set<String>,
    onSelectionModeChange: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onDownloadSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onFolderClick: (OssObjectEntry) -> Unit,
    onMarkdownClick: (OssObjectEntry) -> Unit,
    onApkClick: (OssObjectEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (prefix.isBlank()) "Path: /" else "Path: /$prefix",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onSelectionModeChange(!selectionMode) },
                enabled = !isLoading
            ) {
                Text(text = if (selectionMode) "Done" else "Select")
            }
            if (selectionMode) {
                Button(
                    onClick = onDownloadSelected,
                    enabled = selectedKeys.isNotEmpty() && !isLoading
                ) {
                    Text(text = "Download (${selectedKeys.size})")
                }
                Button(
                    onClick = onDeleteSelected,
                    enabled = selectedKeys.isNotEmpty() && !isLoading
                ) {
                    Text(text = "Delete (${selectedKeys.size})")
                }
            }
        }
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }
        infoMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.primary) }
        errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(objects, key = { it.key }) { entry ->
                val isMarkdown = entry.key.endsWith(".md", ignoreCase = true)
                val isApk = entry.key.endsWith(".apk", ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when {
                                selectionMode -> onToggleSelection(entry.key)
                                entry.isDirectory -> onFolderClick(entry)
                                isMarkdown -> onMarkdownClick(entry)
                                isApk -> onApkClick(entry)
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectionMode) {
                        Checkbox(
                            checked = selectedKeys.contains(entry.key),
                            onCheckedChange = { onToggleSelection(entry.key) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = entry.displayName, style = MaterialTheme.typography.bodyLarge)
                        if (!entry.isDirectory) {
                            Text(
                                text = "Size: ${entry.size ?: 0} bytes",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (isMarkdown) {
                                Text(
                                    text = "Markdown",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (isApk) {
                                Text(
                                    text = "APK (tap to install)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Bucket: ${bucket.name}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
