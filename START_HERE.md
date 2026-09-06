# vibeQwenGlasses — 模型开发启动包

> 本仓库为模型开发 vibeQwenGlasses（千问 G1 眼镜 Android 录音 APP）的**完整材料包**。
> 目标：Android 原生（Kotlin + Jetpack Compose），直连眼镜录音，绕开官方 APP。

---

## 📦 材料清单

| 路径 | 内容 | 用途 |
|------|------|------|
| `docs/ARCHITECTURE.md` | Android 原生版架构设计 | 分层结构、模块设计、里程碑 |
| `docs/PROTOCOL.md` | 逆向协议规格（核心！） | 握手、指令、398B 音频帧格式 |
| `docs/combo_test_report.md` | 4 种发起/结束组合测试报告 | 录音控制指令与窗口验证 |
| `tools/analyze_recording.js` | HCI 日志分析脚本 | 逐秒流量/CID/JSON 提取 |
| `tools/extract_audio.js` | 音频帧提取脚本 | 398B 帧 → PCM → WAV |
| `tools/extract_combo_audio.js` | 组合测试提取脚本 | 多段录音切分还原 |

---

## 🔑 关键事实速查（先读这个！）

### 硬件
- 眼镜：恒玄 BES2800 + 高通骁龙 AR1，蓝牙 5.3 双模
- 眼镜设备名通常包含 `Qwen Glasses`、`AILABS` 或 `Quark` 标识

### 协议（详见 PROTOCOL.md）
- 控制通道：L2CAP CID `0x0041`（眼镜→手机 JSON）/ `0x004A`（手机→眼镜 JSON）
- 音频通道：L2CAP CID **动态**（实测 0x0047/0x0048 因连接而异），**须按魔数头 `87 EF 12 03 07 01 86 08` 全局匹配**，398 字节/帧：
  ```
  [0..7]   8B  魔数头 87 EF 12 03 07 01 86 08
  [8]      1B  序列号（递增）
  [9..12]  4B  填充 00 00 00 00
  [13..396] 384B PCM（16bit LE / 16000Hz / 单声道）
  [397]    1B  填充（丢弃）
  ```
- 录音开始：3 条 JSON（`code:"AudioRecording"` + `wakeupType:"longRecord"` + `uri:"airecord://start"`）
- 录音停止：`{"type":"PART","codeList":["AudioRecording"]}` + `{"code":"AudioRecording"}`
- 音频帧提取已**字节级验证**：3402 帧与官方 WAV 一致；另 4 种发起/结束组合（6-7s×4）全部还原成功（见 docs/combo_test_report.md），并已用两个不同模型交叉验证（结果字节级一致）

### 复用 vibeARS
音频管线 `AudioPipeline`（WAV/AAC 编码、5min 切片、幅度计算）可从
vibeARS 仓库（`com.vibears.app.audio` 包）移植，仅替换输入点：
`audioRecord.read()` → 眼镜 398B 帧流解析。

---

## 🚀 开发起点（M1）

2. **Android 工程**：Kotlin + Compose，minSdk 26，compileSdk 34
3. **传输层**：`BluetoothDevice.createRfcommSocketToServiceRecord(uuid)` + 读取循环
4. **协议层**：握手状态机（PROTOCOL.md §3）→ READY
5. **录音**：发 3 条 JSON → 收音频帧 → 解析 PCM → 写 WAV

---

## ⚠️ 约束

- **不在本地编译**：所有构建/测试走 GitHub Actions（`.github/workflows/`）
- 需要蓝牙权限 + 已配对设备
- 日志 `.cfa` 实际是 btsnoop 格式，改后缀 `.log` 即可解析
- 音频 CID 是动态的，实现时必须按魔数头匹配而非固定 CID