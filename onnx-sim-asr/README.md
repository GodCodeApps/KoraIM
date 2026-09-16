# onnx-sim-asr

`onnx-sim-asr` 是一个不包含 UI 的 Android ASR 库，内部封装了：

- sherpa-onnx 离线识别；
- SenseVoice 中文模型（当前固定使用 `zh` 语言模式）；
- Silero VAD 语音活动检测；
- `AudioRecord` 麦克风采集；
- 临时识别结果、最终识别结果和实时音量回调。
- 已存在录音文件转文字：支持库内录制的 AAC/M4A 文件，不包含 UI。

## 生命周期

使用顺序是：

```text
initialize -> startListening -> stopListening -> release
```

`initialize` 会在后台线程加载模型，必须在初始化成功后才能开始录音。
调用方仍然需要在运行时申请 `android.permission.RECORD_AUDIO` 权限。

当前识别语言固定为中文。后续如果需要开放英文或中英混合识别，再调整
`SherpaAsrModel` 的语言配置和模型选择。

## 工作方式

这个库采用“模拟流式”方式：使用 `AudioRecord` 连续采集麦克风数据，Silero
VAD 按语音活动切分，再使用 SenseVoice 离线识别器对当前语音缓冲区重复解码，
因此可以持续回调临时结果，但底层不是 OnlineRecognizer 流式模型。

默认参数：

| 参数 | 当前值 | 说明 |
|---|---:|---|
| 采样率 | `16000 Hz` | 单声道 PCM |
| ASR 模型类型 | `15` | SenseVoice |
| 识别语言 | `zh` | 当前固定中文 |
| 临时结果间隔 | 约 `200 ms` | 实际间隔受设备性能影响 |
| VAD 窗口 | `512 samples` | 用于检测语音活动 |

VAD 可能因为短暂停顿产生多个最终片段。库层回调原始识别结果，聊天输入框
会将这些片段连续拼接；拼接时不额外插入空格，片段之间的句号会在确认后续
还有内容时转换为逗号，片段内部的标点不会被删除。

## Kotlin 调用

```kotlin
import com.kora.onsim.asr.OnnxSimAsr
import com.kora.onsim.asr.OnnxSimAsrInitializationListener
import com.kora.onsim.asr.OnnxSimAsrListener

// 建议在页面进入时调用一次，避免点击录音按钮后才加载模型。
OnnxSimAsr.initialize(this, object : OnnxSimAsrInitializationListener {
    override fun onInitialized() {
        // 引擎已准备好，可以启用录音按钮。
    }

    override fun onError(error: Throwable) {
        // 模型加载失败。
    }
})

// 按住录音按钮时调用，调用前必须已经初始化成功。
OnnxSimAsr.startListening(this, object : OnnxSimAsrListener {
    override fun onListeningStarted() {
        // 开始采集麦克风。
    }

    override fun onAudioLevel(level: Float) {
        // 实时音量，范围 0..1，可用于声波、音量条或动画。
    }

    override fun onPartialResult(text: String) {
        // 更新输入框中的临时文本。
    }

    override fun onFinalResult(text: String) {
        // 提交当前语句到输入框。
    }

    override fun onError(error: Throwable) {
        // 录音或识别失败。
    }

    override fun onStopped() {
        // 已停止并处理完剩余音频。
    }
})

// 松开录音按钮或取消录音。
OnnxSimAsr.stopListening()

// 页面销毁，或应用不再使用 ASR 时调用。
OnnxSimAsr.release()
```

## Java 调用

所有主要方法都标记为静态 Java 方法，不需要写 Kotlin 的 `INSTANCE`：

```java
import com.kora.onsim.asr.OnnxSimAsr;
import com.kora.onsim.asr.OnnxSimAsrInitializationListener;
import com.kora.onsim.asr.OnnxSimAsrListener;

OnnxSimAsr.initialize(this, new OnnxSimAsrInitializationListener() {
    @Override
    public void onInitialized() {
        // 引擎初始化完成。
    }

    @Override
    public void onError(Throwable error) {
        // 模型加载失败。
    }
});

OnnxSimAsr.startListening(this, new OnnxSimAsrListener() {
    @Override
    public void onReady() {}

    @Override
    public void onListeningStarted() {}

    @Override
    public void onAudioLevel(float level) {
        // 实时音量，范围 0..1。
    }

    @Override
    public void onPartialResult(String text) {
        // 更新输入框临时文本。
    }

    @Override
    public void onFinalResult(String text) {
        // 写入输入框最终文本。
    }

    @Override
    public void onError(Throwable error) {}

    @Override
    public void onStopped() {}
});

// 结束录音
OnnxSimAsr.stopListening();

// 释放资源
OnnxSimAsr.release();
```

`initialize`、识别和模型加载都不会阻塞主线程，所有回调均在主线程执行。

## 已存在录音转文字

`transcribeAudio` 用于把已经录好的本地 AAC/M4A 文件转换为中文文本，适合消息列表中
对历史语音消息执行“转文字”。调用前仍然需要先完成一次 `initialize`，识别结果和状态
回调均在主线程执行；该接口不需要重新打开麦克风。

```kotlin
OnnxSimAsr.transcribeAudio(
    context = this,
    audioPath = voiceFile.absolutePath,
    listener = object : OnnxSimAsrFileListener {
        override fun onStarted() {}

        override fun onResult(text: String) {
            // 更新语音消息下方的文字
        }

        override fun onError(error: Throwable) {}

        override fun onFinished() {}
    },
)
```

Java 可以直接调用同一个静态方法：

```java
OnnxSimAsr.transcribeAudio(this, voicePath, new OnnxSimAsrFileListener() {
    @Override
    public void onResult(String text) {
        // 显示或保存识别结果
    }

    @Override
    public void onError(Throwable error) {
        // 处理失败
    }
});
```

`transcribeAudio` 内部使用 Android `MediaExtractor`/`MediaCodec` 将 AAC/M4A 解码为
16 kHz 单声道 PCM，再交给 SenseVoice 识别。库只接收本地路径；如果语音来自网络，
应先下载到应用缓存目录后再调用。

## API 说明

### `OnnxSimAsr`

| 方法/属性 | 返回值 | 说明 |
|---|---|---|
| `initialize(context)` | `Boolean` | 使用默认中文 SenseVoice 模型异步初始化 |
| `initialize(context, callback)` | `Boolean` | 初始化并接收成功/失败回调 |
| `initialize(context, modelType, callback)` | `Boolean` | 使用 sherpa-onnx 模型类型初始化 |
| `isReady` | `Boolean` | 模型和 VAD 是否已经准备完成 |
| `startListening(context, listener)` | `Boolean` | 开始麦克风监听；未初始化或无权限时失败 |
| `transcribeAudio(context, audioPath, listener)` | `Boolean` | 将本地 AAC/M4A 录音转为中文文本 |
| `stopListening()` | `Unit` | 停止采集并处理剩余音频 |
| `release()` | `Unit` | 停止监听并释放识别器、VAD 和 native 资源 |

### `OnnxSimAsrListener`

| 回调 | 说明 |
|---|---|
| `onReady()` | 本次监听任务已进入准备完成状态 |
| `onListeningStarted()` | `AudioRecord` 已开始采集 |
| `onAudioLevel(level)` | 实时音量，范围 `0..1`，所有回调均在主线程 |
| `onPartialResult(text)` | 当前语音片段的临时识别结果，可能重复返回完整片段 |
| `onFinalResult(text)` | VAD 判定一段语音结束后的最终结果 |
| `onError(error)` | 录音、模型或识别异常 |
| `onStopped()` | 监听停止且剩余音频处理完成 |

### `OnnxSimAsrFileListener`

| 回调 | 说明 |
|---|---|
| `onStarted()` | 文件识别任务开始 |
| `onResult(text)` | 返回完整中文识别结果 |
| `onError(error)` | 文件读取、解码或识别失败 |
| `onFinished()` | 任务结束；成功和失败都会回调 |

## 接入聊天输入框

`imui` 已经完成默认接入，宿主只需要完成 ASR 初始化和录音权限申请：

```kotlin
// 建议在 Application 启动或首个 Activity 创建时调用一次
OnnxSimAsr.initialize(applicationContext)
```

聊天输入框中的两个语音入口是独立的：

```text
左侧 iv_voice             → 原有按住说话，发送语音消息
输入框右侧 iv_speech_to_text → 实时语音转文字，写入文本输入框
```

右侧按钮状态由 `SpeechToTextMicView` 管理：未选中时显示灰色细线麦克风，
选中时显示绿色圆形麦克风；`onAudioLevel` 驱动声波和圆形扩散动画。发送按钮
会调用停止转文字流程，避免后台识别结果在发送后再次写入输入框。

语音消息长按菜单会额外显示“转文字”。`imui` 会优先使用消息附件中的本地录音，
本地文件不存在时下载 `remoteUrl` 到应用缓存，再调用 `transcribeAudio`。识别结果显示
在语音气泡下方的独立文字气泡中，并按消息 ID 保存在本地；不会修改或重新发送原始
消息。已有结果再次长按时会显示“取消转文字”，点击后隐藏文字并清除本地结果。

## Java 接入注意事项

主要生命周期方法使用了 `@JvmStatic`，Java 可以直接调用：

```java
OnnxSimAsr.initialize(this, new OnnxSimAsrInitializationListener() {
    @Override
    public void onInitialized() {
        // 可以开始调用 startListening
    }

    @Override
    public void onError(Throwable error) {
        // 初始化失败
    }
});
```

`OnnxSimAsrListener` 的新增回调均提供 Kotlin 默认实现，因此已有 Java/Kotlin
监听器不实现 `onAudioLevel` 也可以正常编译；需要动画或音量条时再覆盖该方法。

## 常见问题

### 点击按钮提示引擎仍在初始化

`initialize` 是异步的。等待 `onInitialized()` 回调后再开始监听，或者在 UI 中
禁用按钮直到 `OnnxSimAsr.isReady` 为 `true`。

### 没有识别结果

检查运行时录音权限、设备是否有麦克风，以及是否过早调用了 `stopListening()`。
短于 VAD 最小有效语音或只有环境噪声时可能不会产生最终文本。

### 页面销毁时应该调用什么

聊天页面销毁时调用 `OnnxSimAsr.stopListening()`；应用彻底不再使用 ASR 时才调用
`OnnxSimAsr.release()`。模型释放后可以再次调用 `initialize` 重新加载。

### 包体过大

当前模型随 AAR/APK 一起打包，SenseVoice 模型约 228 MB。若对 APK 体积敏感，
后续可以把模型改为下载到 `filesDir` 后再初始化，但需要额外实现版本校验、下载
失败重试和本地文件安全校验。
