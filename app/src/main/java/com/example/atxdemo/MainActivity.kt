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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import java.net.URLEncoder
import kotlin.math.max
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import okio.ByteString
import java.util.concurrent.TimeUnit

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
    var command by remember { mutableStateOf("pm list packages -f -3") }
    var expanded by remember { mutableStateOf(false) }
    var selectedPort by remember { mutableStateOf(7980) }
    var host by remember { mutableStateOf("127.0.0.1") }
    var path by remember { mutableStateOf("/shell") }
    var useGet by remember { mutableStateOf(false) }
    var timeoutText by remember { mutableStateOf("60") }
    var output by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var wsUrl by remember { mutableStateOf("") }
    var wsConnected by remember { mutableStateOf(false) }
    var wsStatus by remember { mutableStateOf("") }
    var webSocket by remember { mutableStateOf<WebSocket?>(null) }
    val wsClient = remember {
        OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "ATX 命令发送", style = MaterialTheme.typography.titleLarge)

        Box(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("输入命令") },
                trailingIcon = {
                    Button(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起" else "推荐")
                    }
                }
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("pm list packages -f -3") },
                    onClick = {
                        command = "pm list packages -f -3"
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host") },
                modifier = Modifier.weight(1f)
            )
            TextField(
                value = path,
                onValueChange = { path = it },
                label = { Text("Path") },
                modifier = Modifier.weight(1f)
            )
            TextField(
                value = timeoutText,
                onValueChange = { timeoutText = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text("Timeout") },
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                RadioButton(selected = !useGet, onClick = { useGet = false })
                Text(text = "POST")
            }
            Row {
                RadioButton(selected = useGet, onClick = { useGet = true })
                Text(text = "GET")
            }
        }

        Button(
            onClick = {
                if (loading) return@Button
                loading = true
                output = ""
                scope.launch {
                    val timeout = timeoutText.toIntOrNull() ?: 60
                    val result = sendCommand(host, selectedPort, path, useGet, command, timeout)
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
        Text(text = "目标: http://" + host + ":" + selectedPort + path)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (wsConnected) return@Button
                wsUrl = buildWsUrl(host, selectedPort, path)
                val req = Request.Builder().url(wsUrl).build()
                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        wsConnected = true
                        wsStatus = "已连接"
                        scope.launch { output = appendOutputLine(output, "WS Open: " + wsUrl) }
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        scope.launch { output = appendOutputLine(output, "WS Message: " + text) }
                        if (text.trim() == "ping") {
                            webSocket.send("pong")
                            scope.launch { output = appendOutputLine(output, "WS Auto Pong") }
                        }
                    }
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        scope.launch { output = appendOutputLine(output, "WS Binary " + bytes.size() + " bytes") }
                    }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        scope.launch { output = appendOutputLine(output, "WS Closing: " + code + " " + reason) }
                        wsConnected = false
                        webSocket.close(code, reason)
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        wsConnected = false
                        wsStatus = "已断开"
                        scope.launch { output = appendOutputLine(output, "WS Closed: " + code + " " + reason) }
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        wsConnected = false
                        wsStatus = "失败: " + (t.message ?: "")
                        scope.launch { output = appendOutputLine(output, "WS Failure: " + (t.message ?: "") ) }
                    }
                }
                webSocket = wsClient.newWebSocket(req, listener)
            }) { Text("连接WS") }

            Button(onClick = {
                webSocket?.send("ping")
                scope.launch { output = appendOutputLine(output, "WS Send: ping") }
            }, enabled = wsConnected) { Text("发送Ping") }

            Button(onClick = {
                webSocket?.close(1000, "bye")
                webSocket = null
            }, enabled = wsConnected) { Text("断开WS") }
        }

        if (wsUrl.isNotEmpty()) {
            Text(text = "WS: " + wsUrl + " 状态: " + wsStatus)
        }
    }
}

private fun sanitizePath(p: String): String = if (p.startsWith("/")) p else "/" + p

private fun buildBase(host: String, port: Int, path: String): String = "http://" + host + ":" + port + sanitizePath(path)

private fun buildWsUrl(host: String, port: Int, path: String): String = "ws://" + host + ":" + port + sanitizePath(path)

private fun buildForm(command: String, timeout: Int): String {
    val encodedCmd = URLEncoder.encode(command, "UTF-8")
    return "command=" + encodedCmd + "&timeout=" + timeout
}

private suspend fun sendCommand(host: String, port: Int, path: String, useGet: Boolean, command: String, timeout: Int): String = withContext(Dispatchers.IO) {
    if (host.isBlank()) return@withContext "请求失败: IllegalArgumentException: Host 为空"
    if (port !in 1..65535) return@withContext "请求失败: IllegalArgumentException: 端口无效"
    val base = buildBase(host, port, path)
    val url = if (useGet) {
        URL(base + "?" + buildForm(command, timeout))
    } else {
        URL(base)
    }
    val conn = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 5000
        readTimeout = max(5000, (timeout + 5) * 1000)
        requestMethod = if (useGet) "GET" else "POST"
        doOutput = !useGet
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
    }
    return@withContext try {
        if (!useGet) {
            val body = buildForm(command, timeout)
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { w ->
                w.write(body)
                w.flush()
            }
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
        "URL: " + url.toString() + "\nHTTP " + code + "\n" + sb.toString()
    } catch (t: Throwable) {
        "请求失败: " + t.javaClass.simpleName + ": " + (t.message ?: "") + "\nURL: " + url.toString()
    } finally {
        conn.disconnect()
    }
}

private fun appendOutputLine(current: String, line: String): String {
    return if (current.isEmpty()) line else current + "\n" + line
}

@Preview(showBackground = true)
@Composable
fun CommandScreenPreview() {
    AtxDemoTheme {
        CommandScreen()
    }
}
