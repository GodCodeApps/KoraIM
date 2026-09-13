package com.kora.onsim.asr

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.getOfflineModelConfig
import com.k2fsa.sherpa.onnx.getVadModelConfig
import java.io.File
import java.io.FileOutputStream

/** Model/resource adapter migrated from the sherpa-onnx demo. */
internal object SherpaAsrModel {
    private const val TAG = "kora-onnx-sim-asr"

    fun createRecognizer(context: Context, asrModelType: Int): OfflineRecognizer {
        val config = OfflineRecognizerConfig(
            modelConfig = requireNotNull(getOfflineModelConfig(type = asrModelType)) {
                "Unsupported sherpa-onnx ASR model type: $asrModelType"
            },
        )
        // 当前先固定为中文识别。SenseVoice 的 language 是单值配置，
        // 使用 zh 后不会再以 auto 模式检测日文、韩文、粤语等语言。
        config.modelConfig.senseVoice.language = "zh"
        if (config.modelConfig.numThreads == 1) config.modelConfig.numThreads = 2

        var assetManager: AssetManager? = context.assets
        if (config.modelConfig.provider == "qnn") {
            OfflineRecognizer.prependAdspLibraryPath(context.applicationInfo.nativeLibraryDir)
            require(
                config.modelConfig.senseVoice.qnnConfig.backendLib.isNotEmpty() ||
                    config.modelConfig.zipformerCtc.qnnConfig.backendLib.isNotEmpty() ||
                    config.modelConfig.paraformer.qnnConfig.backendLib.isNotEmpty() ||
                    config.modelConfig.transducer.qnnConfig.backendLib.isNotEmpty(),
            ) { "QNN model requires a backend library" }

            config.modelConfig.tokens = copyAssetToInternalStorage(config.modelConfig.tokens, context)
            copyQnnAssets(config, context)
            assetManager = null
        }

        return OfflineRecognizer(assetManager = assetManager, config = config)
    }

    fun createVad(assetManager: AssetManager): Vad {
        val config = requireNotNull(getVadModelConfig(type = 0)) {
            "The default sherpa-onnx VAD model is unavailable"
        }
        return Vad(assetManager = assetManager, config = config)
    }

    private fun copyQnnAssets(config: OfflineRecognizerConfig, context: Context) {
        with(config.modelConfig) {
            if (senseVoice.model.isNotEmpty()) {
                senseVoice.model = copyAssetToInternalStorage(senseVoice.model, context)
            }
            senseVoice.qnnConfig.contextBinary = copyAssetToInternalStorage(
                senseVoice.qnnConfig.contextBinary,
                context,
            )
            if (zipformerCtc.model.isNotEmpty()) {
                zipformerCtc.model = copyAssetToInternalStorage(zipformerCtc.model, context)
            }
            zipformerCtc.qnnConfig.contextBinary = copyAssetToInternalStorage(
                zipformerCtc.qnnConfig.contextBinary,
                context,
            )
            if (paraformer.model.isNotEmpty()) {
                paraformer.model = copyAssetListToInternalStorage(paraformer.model, context)
            }
            paraformer.qnnConfig.contextBinary = copyAssetListToInternalStorage(
                paraformer.qnnConfig.contextBinary,
                context,
            )
            if (transducer.encoder.isNotEmpty()) {
                transducer.encoder = copyAssetListToInternalStorage(transducer.encoder, context)
                transducer.decoder = copyAssetListToInternalStorage(transducer.decoder, context)
                transducer.joiner = copyAssetListToInternalStorage(transducer.joiner, context)
            }
            transducer.qnnConfig.contextBinary = copyAssetListToInternalStorage(
                transducer.qnnConfig.contextBinary,
                context,
            )
        }
    }

    private fun assetExists(assetManager: AssetManager, path: String): Boolean {
        val directory = path.substringBeforeLast('/', "")
        val fileName = path.substringAfterLast('/')
        return assetManager.list(directory)?.contains(fileName) == true
    }

    private fun copyAssetToInternalStorage(path: String, context: Context): String {
        val outFile = File(context.filesDir, path)
        if (!assetExists(context.assets, path)) {
            outFile.parentFile?.mkdirs()
            return outFile.absolutePath
        }

        val assetSize = context.assets.open(path).use { it.available().toLong() }
        if (outFile.exists() && outFile.length() == assetSize) return outFile.absolutePath

        outFile.parentFile?.mkdirs()
        context.assets.open(path).use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        Log.i(TAG, "Copied ASR asset $path to ${outFile.absolutePath}")
        return outFile.absolutePath
    }

    private fun copyAssetListToInternalStorage(paths: String, context: Context): String =
        paths.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(",") { copyAssetToInternalStorage(it, context) }
}
