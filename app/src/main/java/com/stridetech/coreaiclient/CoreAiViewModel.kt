package com.stridetech.coreaiclient

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stridetech.coreai.ICoreAiCallback
import com.stridetech.coreai.ICoreAiInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalTime

private const val SERVICE_PACKAGE = "com.stridetech.coreai"
private const val SERVICE_CLASS = "com.stridetech.coreai.service.CoreAiService"
private const val MAX_LOG_ENTRIES = 100

data class CoreAiUiState(
    val isConnected: Boolean = false,
    val isInferring: Boolean = false,
    val consoleLog: List<String> = emptyList(),
)

class CoreAiViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(CoreAiUiState())
    val uiState: StateFlow<CoreAiUiState> = _uiState.asStateFlow()

    private var service: ICoreAiInterface? = null

    @Volatile private var tokenBuffer = StringBuilder()
    @Volatile private var pendingApiKey = ""

    private val callback = object : ICoreAiCallback.Stub() {
        override fun onModelStateChanged(isReady: Boolean, activeModelName: String?) {
            log("[CB] ModelState: ready=$isReady name=${activeModelName ?: "null"}")
        }

        override fun onError(errorMessage: String?) {
            log("[CB] Error: ${errorMessage ?: "unknown"}")
            _uiState.update { it.copy(isInferring = false) }
        }

        override fun onInferenceResult(resultJson: String?) {
            val parsed = parseResult(resultJson)
            val msg = if (parsed.success) {
                "[CB] Inference done (${parsed.latencyMs}ms): ${parsed.completion?.take(120) ?: ""}"
            } else {
                "[CB] Inference failed: ${parsed.error ?: resultJson}"
            }
            log(msg)
            _uiState.update { it.copy(isInferring = false) }
        }

        override fun onInferenceToken(token: String?) {
            token ?: return
            tokenBuffer.append(token)
        }

        override fun onInferenceComplete(latencyMs: Long) {
            val result = tokenBuffer.toString().trim()
            tokenBuffer.clear()
            val preview = if (result.isNotEmpty()) ": ${result.take(120)}" else " (no tokens received)"
            log("[CB] Inference complete (${latencyMs}ms)$preview")
            _uiState.update { it.copy(isInferring = false) }
        }

        override fun onModelTransferProgress(modelId: String?, percent: Int) {
            log("[CB] Transfer $modelId: $percent%")
        }

        override fun onModelTransferComplete(modelId: String?, filePath: String?) {
            log("[CB] Transfer complete: $modelId → $filePath")
        }

        override fun onModelTransferError(modelId: String?, errorMessage: String?) {
            log("[CB] Transfer error: $modelId — $errorMessage")
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = ICoreAiInterface.Stub.asInterface(binder)
            service = svc
            try {
                svc.registerCallback(callback)
                // Tester sends raw prompts, so PER_CLIENT mode is required — the service
                // applies the correct chat template automatically based on the active model.
                if (pendingApiKey.isNotBlank()) {
                    svc.setContextMode(pendingApiKey, "PER_CLIENT")
                    log("Context mode → PER_CLIENT (service applies chat template)")
                }
            } catch (e: Exception) {
                log("onServiceConnected setup failed: ${e.message}")
            }
            _uiState.update { it.copy(isConnected = true) }
            log("Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            _uiState.update { it.copy(isConnected = false) }
            log("Service disconnected")
        }
    }

    // ── Zone 1 ─────────────────────────────────────────────────────────────

    fun bind(apiKey: String) {
        if (service != null) { log("Already connected"); return }
        pendingApiKey = apiKey
        log("Binding to CoreAI service…")
        val intent = Intent().apply {
            component = ComponentName(SERVICE_PACKAGE, SERVICE_CLASS)
        }
        val bound = getApplication<Application>().bindService(
            intent, connection, Context.BIND_AUTO_CREATE,
        )
        if (!bound) log("bindService returned false — is CoreAI installed?")
    }

    // ── Zone 2 ─────────────────────────────────────────────────────────────

    fun validateKey(apiKey: String) = ioOp("validateApiKey") { svc ->
        val valid = svc.validateApiKey(apiKey)
        log("validateApiKey → $valid")
    }

    fun getActiveModel(apiKey: String) = ioOp("getActiveModel") { svc ->
        val result = svc.getActiveModel(apiKey)
        log("getActiveModel → $result")
    }

    fun getDownloadedModels(apiKey: String) = ioOp("getDownloadedModels") { svc ->
        val raw = svc.getDownloadedModels(apiKey)
        if (raw == null) {
            log("getDownloadedModels → null")
            return@ioOp
        }
        val (models, error) = parseDownloadedModels(raw)
        when {
            error != null -> log("getDownloadedModels error: $error")
            models.isEmpty() -> log("getDownloadedModels → [] (no models on disk)")
            else -> {
                log("getDownloadedModels → ${models.size} model(s):")
                models.forEach { log("  • ${it.modelId} (${it.sizeBytes / 1_048_576} MB)") }
            }
        }
    }

    fun getLoadedModels(apiKey: String) = ioOp("getLoadedModels") { svc ->
        val result = svc.getLoadedModels(apiKey)
        log("getLoadedModels → $result")
    }

    fun getCatalog(apiKey: String) = ioOp("getCatalog") { svc ->
        val raw = svc.getCatalog(apiKey)
        if (raw == null) { log("getCatalog → null"); return@ioOp }
        val obj = org.json.JSONObject(raw)
        val error = obj.optString("error").takeIf { it.isNotEmpty() }
        if (error != null) { log("getCatalog error: $error"); return@ioOp }
        val arr = obj.optJSONArray("models")
        if (arr == null || arr.length() == 0) { log("getCatalog → [] (empty catalog)"); return@ioOp }
        log("getCatalog → ${arr.length()} model(s):")
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val sizeMb = m.optLong("file_size") / 1_048_576
            log("  • ${m.optString("id")} — ${m.optString("name")} (${sizeMb} MB)")
        }
    }

    // ── Zone 3 ─────────────────────────────────────────────────────────────

    fun download(apiKey: String, modelId: String, url: String) = ioOp("downloadCatalogModel") { svc ->
        svc.downloadCatalogModel(apiKey, modelId, url, callback)
        log("downloadCatalogModel(${modelId.ifBlank { "<default>" }}) dispatched")
    }

    // ── Zone 4 ─────────────────────────────────────────────────────────────

    fun load(apiKey: String, modelId: String) = ioOp("loadModel") { svc ->
        svc.loadModel(apiKey, modelId, callback)
        log("loadModel(${modelId.ifBlank { "<default>" }}) dispatched")
    }

    fun unload(apiKey: String, modelId: String) = ioOp("unloadModel") { svc ->
        svc.unloadModel(apiKey, modelId, callback)
        log("unloadModel(${modelId.ifBlank { "<default>" }}) dispatched")
    }

    fun delete(apiKey: String, modelId: String) = ioOp("deleteModel") { svc ->
        svc.deleteModel(apiKey, modelId, callback)
        log("deleteModel(${modelId.ifBlank { "<default>" }}) dispatched")
    }

    fun setActive(apiKey: String, modelId: String) = ioOp("setActiveModel") { svc ->
        svc.setActiveModel(apiKey, modelId)
        log("setActiveModel(${modelId.ifBlank { "<default>" }}) called")
    }

    // ── Zone 5 ─────────────────────────────────────────────────────────────

    fun runInference(apiKey: String, prompt: String) {
        if (apiKey.isBlank()) { log("API key required"); return }
        if (prompt.isBlank()) { log("Prompt required"); return }
        val svc = service ?: run { log("runInference: not connected — bind first"); return }
        _uiState.update { it.copy(isInferring = true) }
        log("runInference dispatched…")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = svc.runInference(apiKey, prompt)
                val parsed = parseResult(result)
                if (!parsed.success) {
                    log("runInference sync error: ${parsed.error ?: result}")
                    _uiState.update { it.copy(isInferring = false) }
                }
            } catch (e: RemoteException) {
                log("runInference RemoteException: ${e.message}")
                _uiState.update { it.copy(isInferring = false) }
            } catch (e: SecurityException) {
                log("runInference SecurityException: ${e.message}")
                _uiState.update { it.copy(isInferring = false) }
            } catch (e: IllegalArgumentException) {
                log("runInference IllegalArgumentException: ${e.message}")
                _uiState.update { it.copy(isInferring = false) }
            } catch (e: Exception) {
                log("runInference Exception [${e.javaClass.simpleName}]: ${e.message}")
                _uiState.update { it.copy(isInferring = false) }
            }
        }
    }

    // ── Zone 6 ─────────────────────────────────────────────────────────────

    fun setContextMode(apiKey: String, mode: String) = ioOp("setContextMode($mode)") { svc ->
        svc.setContextMode(apiKey, mode)
        log("setContextMode → $mode")
    }

    fun getContextMode(apiKey: String) = ioOp("getContextMode") { svc ->
        val mode = svc.getContextMode(apiKey)
        log("getContextMode → $mode")
    }

    fun resetChatContext(apiKey: String, modelId: String) = ioOp("resetChatContext") { svc ->
        svc.resetChatContext(apiKey, modelId)
        log("resetChatContext(${modelId.ifBlank { "<default>" }}) called")
    }

    // ── Zone 7 ─────────────────────────────────────────────────────────────

    fun setCustomChatTemplate(apiKey: String, modelId: String, templateJson: String) =
        ioOp("setCustomChatTemplate") { svc ->
            svc.setCustomChatTemplate(apiKey, modelId, templateJson)
            val action = if (templateJson.isBlank()) "cleared" else "applied"
            log("setCustomChatTemplate(${modelId.ifBlank { "<default>" }}) $action")
        }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCleared() {
        service?.unregisterCallback(callback)
        runCatching { getApplication<Application>().unbindService(connection) }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun ioOp(opName: String, block: (ICoreAiInterface) -> Unit) {
        val svc = service
        if (svc == null) { log("$opName: not connected — bind first"); return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block(svc)
            } catch (e: RemoteException) {
                log("$opName RemoteException: ${e.message}")
            } catch (e: SecurityException) {
                log("$opName SecurityException: ${e.message}")
            } catch (e: IllegalArgumentException) {
                log("$opName IllegalArgumentException: ${e.message}")
            } catch (e: Exception) {
                log("$opName Exception [${e.javaClass.simpleName}]: ${e.message}")
            }
        }
    }

    private fun log(message: String) {
        val t = LocalTime.now()
        val time = "%02d:%02d:%02d".format(t.hour, t.minute, t.second)
        _uiState.update { state ->
            state.copy(consoleLog = (state.consoleLog + "[$time] $message").takeLast(MAX_LOG_ENTRIES))
        }
    }

    private data class ParsedResult(
        val completion: String?,
        val latencyMs: Long,
        val success: Boolean,
        val error: String?,
    )

    private data class DownloadedModel(
        val modelId: String,
        val path: String,
        val sizeBytes: Long,
    )

    private fun parseDownloadedModels(json: String): Pair<List<DownloadedModel>, String?> =
        runCatching {
            val obj = JSONObject(json)
            val error = obj.optString("error").takeIf { it.isNotEmpty() }
            if (error != null) return Pair(emptyList(), error)
            val arr = obj.optJSONArray("models") ?: return Pair(emptyList(), null)
            val models = (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                DownloadedModel(
                    modelId = m.optString("modelId"),
                    path = m.optString("path"),
                    sizeBytes = m.optLong("sizeBytes"),
                )
            }
            Pair(models, null)
        }.getOrElse { Pair(emptyList(), "Parse error: ${it.message}") }

    private fun parseResult(json: String?): ParsedResult {
        if (json == null) return ParsedResult(null, 0L, false, "Null response")
        return runCatching {
            val obj = JSONObject(json)
            ParsedResult(
                completion = obj.optString("completion").takeIf { it.isNotEmpty() },
                latencyMs = obj.optLong("latency_ms", 0L),
                success = obj.optBoolean("success", false),
                error = obj.optString("error").takeIf { it.isNotEmpty() },
            )
        }.getOrElse { ParsedResult(null, 0L, false, "Parse error: ${it.message}") }
    }
}
