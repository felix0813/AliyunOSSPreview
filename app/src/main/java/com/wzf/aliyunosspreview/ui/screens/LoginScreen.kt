package com.wzf.aliyunosspreview.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wzf.aliyunosspreview.data.OssCredentials

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
        Text(text = "Login with AccessKey", style = MaterialTheme.typography.titleMedium)
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
            Text(text = if (isLoading) "Logging in..." else "Login")
        }
        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}
