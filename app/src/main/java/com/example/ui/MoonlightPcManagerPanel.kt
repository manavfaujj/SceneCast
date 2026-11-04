package com.example.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sources.MoonlightPcDiscovery
import com.example.sources.MoonlightSessionController
import com.limelight.nvstream.http.NvHTTP
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Reliable in-studio Sunshine discovery, pairing and independent scene-layer connection UI. */
@Composable
fun MoonlightPcManagerPanel(
    discovery: MoonlightPcDiscovery,
    session: MoonlightSessionController,
    onAddLayerAndConnect: (String, Int) -> Unit
) {
    val status by session.status.collectAsState()
    val scope = rememberCoroutineScope()
    var pcs by remember { mutableStateOf(discovery.computers()) }
    var selectedHost by remember { mutableStateOf("") }
    var selectedPort by remember { mutableStateOf(NvHTTP.DEFAULT_HTTP_PORT.toString()) }
    var generatedPin by remember { mutableStateOf("") }
    var operation by remember { mutableStateOf("") }
    var scanActive by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    val pairedHosts = session.pairedHosts()
    val normalizedHost = selectedHost.trim()
    val port = selectedPort.toIntOrNull()
    val validTarget = normalizedHost.isNotBlank() && !normalizedHost.any(Char::isWhitespace) && port != null && port in 1..65535
    val isPaired = pairedHosts.contains(normalizedHost)

    LaunchedEffect(status) {
        if (operation == "connect" && (status.startsWith("Streaming", true) || status.startsWith("Connection failed", true) || status.startsWith("Moonlight disconnected", true) || status.startsWith("Unable to connect", true))) {
            operation = ""
        }
    }

    LaunchedEffect(discovery, scanActive) {
        if (scanActive) discovery.start()
        try {
            while (isActive) {
                pcs = discovery.computers()
                delay(900)
            }
        } finally {
            discovery.stop()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Moonlight PC", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Connect a Sunshine PC as its own SceneCast layer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(if (status.startsWith("Streaming", true)) "LIVE" else if (isPaired) "PAIRED" else "READY", modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = operation.isEmpty(), onClick = { scanActive = true; message = null }) { Text("Scan LAN") }
                OutlinedButton(enabled = scanActive, onClick = { scanActive = false; pcs = discovery.computers() }) { Text("Stop scan") }
                if (operation.isNotEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp)
                }
            }

            Text("DISCOVERED COMPUTERS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (pcs.isEmpty()) {
                Text(discovery.error() ?: if (scanActive) "Looking for Sunshine / GameStream PCs on your network…" else "No computers found. Tap Scan LAN to search again.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 190.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(pcs, key = { "${it.name}:${it.localAddress?.hostAddress}:${it.port}" }) { pc ->
                        val address = pc.localAddress?.hostAddress.orEmpty()
                        val selected = normalizedHost == address
                        Surface(
                            modifier = Modifier.fillMaxWidth().border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pc.name, fontWeight = FontWeight.Medium)
                                    Text("$address  ·  port ${pc.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedButton(onClick = { selectedHost = address; selectedPort = pc.port.toString(); message = null }) {
                                    Text(if (selected) "Selected" else "Select")
                                }
                            }
                        }
                    }
                }
            }

            Text("MANUAL CONNECTION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = selectedHost,
                onValueChange = { selectedHost = it.trim(); message = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("PC IP address or hostname") },
                singleLine = true,
                supportingText = { Text("Example: 192.168.1.20") }
            )
            OutlinedTextField(
                value = selectedPort,
                onValueChange = { value -> if (value.length <= 5 && value.all(Char::isDigit)) selectedPort = value },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Sunshine HTTP port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = port == null || port !in 1..65535
            )

            if (generatedPin.isNotBlank()) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("PAIRING PIN", style = MaterialTheme.typography.labelMedium)
                        Text(generatedPin, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Enter this PIN in the Sunshine pairing prompt on your PC.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = operation.isEmpty() && validTarget && !isPaired,
                    onClick = {
                        operation = "pair"
                        generatedPin = com.limelight.nvstream.http.PairingManager.generatePinString()
                        message = null
                        val pinToUse = generatedPin
                        scope.launch {
                            val result = session.pair(normalizedHost, port!!, pinToUse)
                            operation = ""
                            message = result.fold({ "Pairing successful. You can now connect this PC." }, { it.message ?: "Pairing failed." })
                        }
                    }
                ) { Text(if (isPaired) "Already paired" else "Pair PC") }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = operation.isEmpty() && validTarget && isPaired,
                    onClick = {
                        operation = "connect"
                        message = null
                        onAddLayerAndConnect(normalizedHost, port!!)
                        scope.launch { delay(45000); if (operation == "connect") { operation = ""; message = "Still waiting for Moonlight. Check the connection status below." } }
                    }
                ) { Text("Add layer + Connect") }
            }

            if (pairedHosts.isNotEmpty()) {
                Text("PAIRED PCS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                pairedHosts.forEach { host ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(host, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(enabled = operation.isEmpty(), onClick = {
                            selectedHost = host
                            selectedPort = session.savedPort(host).toString()
                            message = null
                        }) { Text("Use") }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(enabled = operation.isEmpty(), onClick = {
                            session.forget(host)
                            if (selectedHost == host) { generatedPin = ""; message = "Saved PC removed." }
                        }) { Text("Forget") }
                    }
                }
            } else {
                Text("No paired PCs yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(enabled = status != "Not connected" && operation.isEmpty(), onClick = {
                    session.stop()
                    operation = ""
                    message = "Moonlight disconnected."
                }) { Text("Disconnect") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.contains("fail", true) || it.contains("incorrect", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
    }
}
