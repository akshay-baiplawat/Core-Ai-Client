package com.stridetech.coreaiclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private const val DEFAULT_HF_URL =
    "https://huggingface.co/bartowski/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                CoreAiClientScreen()
            }
        }
    }
}

@Composable
private fun CoreAiClientScreen(vm: CoreAiViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsState()
    var apiKey by rememberSaveable { mutableStateOf("") }
    var modelId by rememberSaveable { mutableStateOf("") }
    var downloadUrl by rememberSaveable { mutableStateOf(DEFAULT_HF_URL) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var templateJson by rememberSaveable { mutableStateOf("") }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("CoreAI Tester", style = MaterialTheme.typography.headlineSmall)

            Zone1Connection(
                apiKey = apiKey,
                onApiKeyChange = { apiKey = it },
                isConnected = uiState.isConnected,
                onBind = { vm.bind(apiKey) },
            )
            Zone2Status(
                onGetActive = { vm.getActiveModel(apiKey) },
                onGetDownloaded = { vm.getDownloadedModels(apiKey) },
                onGetLoaded = { vm.getLoadedModels(apiKey) },
                onValidate = { vm.validateKey(apiKey) },
                onGetCatalog = { vm.getCatalog(apiKey) },
            )
            Zone3Acquisition(
                modelId = modelId,
                onModelIdChange = { modelId = it },
                downloadUrl = downloadUrl,
                onDownloadUrlChange = { downloadUrl = it },
                onDownload = { vm.download(apiKey, modelId, downloadUrl) },
            )
            Zone4Lifecycle(
                onLoad = { vm.load(apiKey, modelId) },
                onUnload = { vm.unload(apiKey, modelId) },
                onDelete = { vm.delete(apiKey, modelId) },
                onSetActive = { vm.setActive(apiKey, modelId) },
            )
            Zone5Inference(
                prompt = prompt,
                onPromptChange = { prompt = it },
                isInferring = uiState.isInferring,
                onRun = { vm.runInference(apiKey, prompt) },
            )
            Zone6Context(
                onSetFull = { vm.setContextMode(apiKey, "FULL_PROMPT") },
                onSetPer = { vm.setContextMode(apiKey, "PER_CLIENT") },
                onGetMode = { vm.getContextMode(apiKey) },
                onReset = { vm.resetChatContext(apiKey, modelId) },
            )
            Zone7CustomTemplate(
                templateJson = templateJson,
                onTemplateJsonChange = { templateJson = it },
                onApply = { vm.setCustomChatTemplate(apiKey, modelId, templateJson) },
                onClear = { vm.setCustomChatTemplate(apiKey, modelId, "") },
            )
            ConsoleLog(entries = uiState.consoleLog)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Zone1Connection(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    isConnected: Boolean,
    onBind: () -> Unit,
) {
    SectionHeader("1 — Connection")
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text("API Key") },
        placeholder = { Text("Enter your API key") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusChip(connected = isConnected)
        Button(onClick = onBind, enabled = !isConnected) { Text("Bind") }
    }
}

@Composable
private fun Zone2Status(
    onGetActive: () -> Unit,
    onGetDownloaded: () -> Unit,
    onGetLoaded: () -> Unit,
    onValidate: () -> Unit,
    onGetCatalog: () -> Unit,
) {
    SectionHeader("2 — Engine Status")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onGetActive, modifier = Modifier.weight(1f)) {
            Text("Get Active", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(onClick = onGetDownloaded, modifier = Modifier.weight(1f)) {
            Text("Get Downloaded", style = MaterialTheme.typography.labelSmall)
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onGetLoaded, modifier = Modifier.weight(1f)) {
            Text("Get Loaded", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(onClick = onValidate, modifier = Modifier.weight(1f)) {
            Text("Validate Key", style = MaterialTheme.typography.labelSmall)
        }
    }
    OutlinedButton(onClick = onGetCatalog, modifier = Modifier.fillMaxWidth()) {
        Text("Get Catalog", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Zone3Acquisition(
    modelId: String,
    onModelIdChange: (String) -> Unit,
    downloadUrl: String,
    onDownloadUrlChange: (String) -> Unit,
    onDownload: () -> Unit,
) {
    SectionHeader("3 — Model Acquisition")
    OutlinedTextField(
        value = modelId,
        onValueChange = onModelIdChange,
        label = { Text("Model ID (blank = default)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = downloadUrl,
        onValueChange = onDownloadUrlChange,
        label = { Text("Download URL") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Download") }
}

@Composable
private fun Zone4Lifecycle(
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: () -> Unit,
) {
    SectionHeader("4 — RAM & Disk Lifecycle")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onLoad, modifier = Modifier.weight(1f)) {
            Text("Load", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(onClick = onUnload, modifier = Modifier.weight(1f)) {
            Text("Unload", style = MaterialTheme.typography.labelSmall)
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onDelete,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { Text("Delete Model", style = MaterialTheme.typography.labelSmall) }
        OutlinedButton(onClick = onSetActive, modifier = Modifier.weight(1f)) {
            Text("Set Active", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Zone5Inference(
    prompt: String,
    onPromptChange: (String) -> Unit,
    isInferring: Boolean,
    onRun: () -> Unit,
) {
    SectionHeader("5 — Inference")
    OutlinedTextField(
        value = prompt,
        onValueChange = onPromptChange,
        label = { Text("Prompt") },
        placeholder = { Text("Ask something…") },
        minLines = 3,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onRun, enabled = !isInferring, modifier = Modifier.weight(1f)) {
            Text("Run Inference")
        }
        if (isInferring) CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun Zone6Context(
    onSetFull: () -> Unit,
    onSetPer: () -> Unit,
    onGetMode: () -> Unit,
    onReset: () -> Unit,
) {
    SectionHeader("6 — Context & Session")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onSetFull, modifier = Modifier.weight(1f)) {
            Text("Set FULL_PROMPT", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(onClick = onSetPer, modifier = Modifier.weight(1f)) {
            Text("Set PER_CLIENT", style = MaterialTheme.typography.labelSmall)
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onGetMode, modifier = Modifier.weight(1f)) {
            Text("Get Context Mode", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
            Text("Reset Chat Context", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Zone7CustomTemplate(
    templateJson: String,
    onTemplateJsonChange: (String) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
) {
    SectionHeader("7 — Custom Chat Template")
    OutlinedTextField(
        value = templateJson,
        onValueChange = onTemplateJsonChange,
        label = { Text("Template JSON") },
        placeholder = { Text("{\"userMessagePrefix\":\"...\", \"stopToken\":\"...\"}") },
        minLines = 4,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onApply, modifier = Modifier.weight(1f)) {
            Text("Apply Template", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
            Text("Clear Template", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ConsoleLog(entries: List<String>) {
    SectionHeader("Console Log")
    val scrollState = rememberScrollState()
    LaunchedEffect(entries.size) { scrollState.animateScrollTo(scrollState.maxValue) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp, max = 260.dp),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        SelectionContainer {
            Text(
                text = entries.joinToString("\n").ifEmpty { "No log entries yet." },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(scrollState),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(4.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider()
}

@Composable
private fun StatusChip(connected: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (connected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = if (connected) "Connected" else "Disconnected",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
