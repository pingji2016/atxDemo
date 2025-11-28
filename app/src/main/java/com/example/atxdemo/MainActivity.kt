package com.example.atxdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.atxdemo.ui.theme.AtxDemoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AtxDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CommandScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun CommandScreen(modifier: Modifier = Modifier) {
    var command by remember { mutableStateOf("adb shell pm") }
    var expanded by remember { mutableStateOf(false) }
    var selectedPort by remember { mutableStateOf(7980) }
    var output by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "ATX 命令发送", style = MaterialTheme.typography.titleLarge)

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                label = { Text("输入命令") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )

            androidx.compose.material3.ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("adb shell pm") },
                    onClick = {
                        command = "adb shell pm"
                        expanded = false
                    }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.padding(end = 16.dp)
            ) {
                RadioButton(
                    selected = selectedPort == 7980,
                    onClick = { selectedPort = 7980 }
                )
                Text(text = "端口 7980", modifier = Modifier.padding(start = 4.dp))
            }
            Row {
                RadioButton(
                    selected = selectedPort == 7912,
                    onClick = { selectedPort = 7912 }
                )
                Text(text = "端口 7912", modifier = Modifier.padding(start = 4.dp))
            }
        }

        Button(
            onClick = {
                if (loading) return@Button
                loading = true
                output = ""
                scope.launch {
                    val result = sendCommand(selectedPort, command)
                    output = result
                    loading = false
                }
            },
            enabled = !loading
        ) {
            Text(text = if (loading) "发送中..." else "确认发送")
        }

        Text(text = "输出", style = MaterialTheme.typography.titleMedium)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = output,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .verticalScroll(scroll)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "目标: http://127.0.0.1:${'$'}selectedPort/exec")
    }
}

private suspend fun sendCommand(port: Int, command: String): String = withContext(Dispatchers.IO) {
    val url = URL("http://127.0.0.1:${'$'}port/exec")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 5000
        readTimeout = 15000
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "text/plain; charset=utf-8")
    }
    return@withContext try {
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { w ->
            w.write(command)
            w.flush()
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val sb = StringBuilder()
        BufferedReader(stream.reader(Charsets.UTF_8)).use { r ->
            var line: String?
            while (true) {
                line = r.readLine()
                if (line == null) break
                sb.appendLine(line)
            }
        }
        sb.toString()
    } catch (t: Throwable) {
        "请求失败: ${'$'}{t.message}"
    } finally {
        conn.disconnect()
    }
}

@Preview(showBackground = true)
@Composable
fun CommandScreenPreview() {
    AtxDemoTheme {
        CommandScreen()
    }
}
