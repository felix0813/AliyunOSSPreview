package com.wzf.aliyunosspreview.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wzf.aliyunosspreview.data.OssBucket

@Composable
fun BucketList(
    buckets: List<OssBucket>,
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
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
            Text(text = if (isLoading) "Loading..." else "Refresh buckets")
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
            items(buckets, key = { it.name }) { bucket ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBucketSelected(bucket) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(text = bucket.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Region: ${bucket.location ?: "unknown"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
