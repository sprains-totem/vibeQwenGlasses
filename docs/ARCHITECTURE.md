# vibeQwenGlasses — 千问 G1 眼镜录音系统架构设计规范

> 版本: v1.0（实机全体验证版）  
> 平台: 纯 Android 原生（Kotlin + Jetpack Compose + 协程 Flow）  
> 目标: 绕开官方 App (`com.alibaba.wow`)，完全自主实现双通道控制、GMA 鉴权、无损 16kHz PCM 录音与专业播放

---

## 1. 系统总体架构与分层

系统采用严格的分层单向依赖架构，状态由底层通过不可变数据流（StateFlow）向 UI 响应式驱动：

```
┌────────────────────────────────────────────────────────────────────────┐
│                        UI 表现层 (Jetpack Compose)                      │
│   ConnectScreen (蓝牙扫描与状态卡片) · RecordScreen (波形/计时/分贝)   │
│   RecordingsScreen (人性化命名列表/单曲状态同步) · MiniPlayerBar (常驻底栏) │
│   PlayerSheet (交互式波形拖动 Seek · 循环指示 · 下拉变速)                │
├────────────────────────────────────────────────────────────────────────┤
│                     播放核心单例 (GlobalAudioPlayer)                    │
│   - 单一信源 (Single Source of Truth) 统一管理播放状态机                │
│   - 全局 MediaPlayer 调度、120 等步长波形采样缓存、毫秒级进度流          │
│   - 状态广播 (isPlaying, positionMs, durationMs, speed, loop, peaks)    │
├────────────────────────────────────────────────────────────────────────┤
│                       服务管理层 (前台保活服务)                          │
│   GlassesConnectionService (前台服务 + WakeLock + 双向总线)             │
│   ├─ 双通道生命周期管理 (BLE L2CAP CoC 控制 + 经典蓝牙 RFCOMM 16 音频)  │
│   ├─ 录音状态控制器与看门狗 (4秒用户停止冷却锁，防抖防自动复燃)           │
│   └─ GlassesBus (全局不可变 UI 状态流与实时波形广播)                    │
├────────────────────────────────────────────────────────────────────────┤
│                      协议与帧处理层 (纯 Kotlin)                         │
│   ├─ GcspFrameReassembler: 权威 PDU 长度定界分发，自动 8B ACK / 会话响应│
│   ├─ QwenHandshakeProtocol: 8 步握手认证状态机 (驱动进入 READY)        │
│   ├─ QwenCommands & QwenFramer: 官方 5 步录音激活序列 (J1~J5) 构造     │
│   └─ QwenFrameParser: 398B 裸帧校验 (8B魔数 + 1B序号 + 384B PCM)       │
├────────────────────────────────────────────────────────────────────────┤
│                        底层通信与硬件传输层                             │
│   ClassicBtTransport:                                                  │
│   ├─ 控制链路: BluetoothDevice.createL2capChannel(130)                  │
│   ├─ 音频链路: BluetoothDevice.createRfcommSocketToServiceRecord(...)  │
│   ├─ 互斥写入: synchronized(writeLock) 杜绝多线程写死锁                │
│   └─ 异步双读线程: vqg-control-reader + vqg-audio-reader                │
├────────────────────────────────────────────────────────────────────────┤
│                        音频存储与编码引擎                               │
│   AudioPipeline:                                                       │
│   ├─ 16kHz 16-bit Mono WAV 文件头构建与原子性写入                       │
│   ├─ 无损 PCM 落盘持久化与分块时间戳切片                                │
│   └─ 实时 RMS 分贝能量计算与波形抽样                                   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 核心模块与技术实现

### 2.1 播放器单例架构 (`GlobalAudioPlayer`)
针对以往局部 ViewModel 导致的“回放页面关闭后无处恢复”、“列表内外播放状态脱节”等问题，将播放器全面重构为应用级全局单例：
- **全局状态驱动**：暴露统一只读 `StateFlow<GlobalAudioPlayer.State>`，包含当前曲目、播放中标识、播放进度、时长、倍速、循环状态及离线预计算的 120 点波形峰值；
- **常驻 MiniPlayerBar**：悬浮于主导航栏上方。无论用户处于「连接」「录音」「录音库」还是「日志」任意页面，均可随时查看进度、启停，并点击卡片瞬间唤回全屏 `PlayerSheet`；
- **全动态波形交互**：彻底移除带有系统兼容性问题的原生灰色 Slider，重构为双色动态波形播放条（已播放青色高亮，未播放暗灰），支持点击任意位置或水平拖拽平滑 Seek；
- **Android 系统级变速防发声保护**：针对 Android `MediaPlayer.setPlaybackParams()` 强行唤醒暂停音频的系统级缺陷，前置检测 `wasPlaying` 状态并在调速后严格锁定维持暂停。

### 2.2 传输与通信并发保障 (`ClassicBtTransport`)
- **双通道独立生命周期**：
  - 控制通道（L2CAP PSM 130）：主会话生命周期载体。只有该通道断开才触发 `notifyDisconnected()`；
  - 音频通道（RFCOMM 16）：从属数据通道。录音结束或单次音频流关闭仅释放音频 Reader，绝不影响主控制会话。
- **并发写互斥锁**：
  - 底层使用 `synchronized(writeLock)` 串行化所有指令写入，彻底消除了协程任务与后台 ACK 读取线程并发写入引发的 native 句柄阻塞。

### 2.3 解帧器权威长度定界 (`GcspFrameReassembler`)
- **定界规范**：
  以帧前置 2 字节 `pduLen` 为唯一准则（`totalLen = 3 + pduLen`），帧完整即切出，帧不足即等待。
- **免括号依赖**：
  彻底废除基于 `{` 字符和括号深度的启发式匹配，彻底解决遥测数据分段包含 `0x7B` 导致解帧器死锁的隐患。
- **自动握手应答**：
  - 遇到 `flag == 0x24` 毫秒级回发 8 字节 ACK；
  - 遇到 `ns == 0x10` 或 `ns == 0x16` 毫秒级回送 `Flag 0x14` 会话响应。

### 2.4 录音状态机与防误触机制 (`GlassesConnectionService`)
- **5 步连发激活**：严格执行官方实测录音激活帧（J1 业务、J2 场景、J3 跳转、J4 硬件推流使能、J5 参数确认），使用 7 位整数 SessionId；
- **防反向二次触发**：用户主动停止录音后，启动 4 秒防抖冷却锁 `userStoppedCooldownUntil`，并在非录音状态丢弃残留音频帧，杜绝自动死循环复燃。

---

## 3. 目录与文件布局

```
vibeQwenGlasses/app/src/main/java/com/vibeqwen/glasses/
├── MainActivity.kt                 # 单 Activity 入口：底部 4 Tab + 全局 MiniPlayer 与弹层
├── audio/
│   ├── AudioPipeline.kt            # 音频存储：PCM 落盘、WAV 格式封装、RMS 能量计算
│   ├── GlobalAudioPlayer.kt        # 全局单例播放中心（单一信源）
│   └── RecordingFileManager.kt     # 录音文件管理、人性化命名转换、分享与删除
├── bluetooth/
│   ├── BleGlassesScanner.kt        # BLE 0xFEB3 广播扫描
│   └── ClassicBtTransport.kt       # 经典蓝牙+L2CAP 双通道传输层（互斥写入与状态隔离）
├── protocol/
│   ├── GcspFrameReassembler.kt     # GCSP/GMA 权威长度定界解帧器与自动 ACK 引擎
│   ├── GmaProtocolHandler.kt       # GMA 快速鉴权协议处理器
│   ├── QwenCommands.kt             # 业务信令与 5 步录音序列构造器
│   ├── QwenFramer.kt               # GCSP 二进制与 JSON 帧封装器
│   └── QwenHandshakeProtocol.kt    # 8 步握手认证状态机
├── service/
│   ├── GlassesConnectionService.kt # 核心前台服务：链路调度、录音防抖控制
│   └── GlassesBus.kt               # 状态总线
└── ui/
    ├── components/                 # 公共组件：动态双色 WaveformBar、大录音按键
    ├── connect/                    # 连接页面：设备卡片、扫描、握手状态指示
    ├── player/                     # 播放组件：MiniPlayerBar、PlayerSheet 交互卡片
    ├── record/                     # 录音页面：计时器、实时波形、分贝仪表
    └── recordings/                 # 录音库页面：列表项与播放器双向毫秒级同步
```
