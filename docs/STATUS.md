# vibeQwenGlasses 项目状态与推进计划

## 📅 最后更新：2026-09-04

---

## 一、项目目标

在不依赖官方千问 App（com.alibaba.wow）的前提下，独立连接 Qwen G1 智能眼镜，
通过私有高速数据通道获取眼镜麦克风的高清 16kHz PCM 音频流，
且录音期间不降级 A2DP 立体声音乐播放质量（不走 SCO 免提电话通道）。

---

## 二、已确认的底层架构（来自 2 次真机 HCI 抓包逆向）

### 三条并行蓝牙物理链路

```
                    ┌── [1] A2DP 音乐通道 (Handle 0xedc, L2CAP PSM 0x0019)
                    │       高清立体声，不受录音影响
千问 G1 眼镜 ───────┼── [2] 私有控制通道 (Handle 0x000a, BLE L2CAP CoC PSM 130)
 (BES2800)          │       业务 JSON 指令、握手认证、录音控制
                    └── [3] 私有音频推流通道 (Handle 0x0007, Classic RFCOMM Channel)
                            398B 裸 PCM 音频帧 (魔数 87 EF 12 03 07 01 86 08)
```

### 音频帧格式（398 字节）

| 偏移 | 长度 | 内容 |
|------|------|------|
| 0-7  | 8B   | 魔数 `87 EF 12 03 07 01 86 08` |
| 8    | 1B   | 帧序号（0-255 回绕） |
| 9-12 | 4B   | 填充/标志 |
| 13-396 | 384B | 有效 PCM 数据（16kHz / 16bit / 单声道 LE） |
| 397  | 1B   | 尾部填充 |

### 流控机制

手机每收到 2 帧音频后回复 5 字节 Credit ACK：`85 FF 01 02 6B`

---

## 三、已完成的工作

### ✅ 底层抓包与逆向
- [x] 抓取官方 App 录音时的完整 HCI 报文（bt_fresh.cfa 7MB，5038 帧音频）
- [x] 抓取官方 App 冷启动重连的完整 HCI 报文（bt_reconn.cfa 7.7MB）
- [x] 确认三通道并行架构与音频帧格式
- [x] 确认录音触发的 3 条 JSON 指令与停止指令
- [x] 确认流控 Credit ACK 报文格式

### ✅ App 基础设施
- [x] GitHub Actions CI/CD 自动编译与 Release 发布
- [x] 固定签名密钥（支持无缝升级安装）
- [x] Live Debug Bridge（ADB Broadcast 动态报文注入）
- [x] DebugProvider（ADB ContentProvider CLI 调试接口 `/data/local/tmp/qwen`）
- [x] LogCollector 实时日志写盘（`latest.log`）
- [x] vibeADB 远程 MCP 控制链路

### ✅ 连接层
- [x] BLE L2CAP PSM 130 控制通道连接稳定
- [x] 经典蓝牙 RFCOMM Channel 16 连接可达（反射 createInsecureRfcommSocket(16)）
- [x] GCSP 版本协商帧发送与接收正常
- [x] GcspFrameReassembler 流式帧解析器
- [x] QwenFrameParser 398B 音频帧解析器
- [x] AudioPipeline + WavWriter 录音管线

### ✅ 已移除的错误方案
- [x] 删除 SCO 免提电话录音（ScoAudioRecorder）
- [x] 删除 Shizuku 集成

---

## 四、当前真实状态与卡点诊断（严格遵守 DEV_SPEC.md 规范）

### ❌ 核心卡点：GMA 鉴权尚未真正通过，录音数据为 0

**客观实测数据（2026-09-05 实测）**：
* 物理连接：Insecure L2CAP PSM 130 已稳定建立，双向通信正常（收到过眼镜上报的 `0x2009` 硬件数据包）；
* 握手状态：手机发出 `0x10` GMA 鉴权挑战后，**眼镜端并未回复 `0x11 / 0x13` 鉴权成功帧**；
* 状态虚标排查：此前状态机因设置了 2.5 秒超时兜底强推至 `READY`，导致界面显示“就绪”实为**伪就绪**；
* 录音实测结果：下发 `AudioRecording` 指令后，眼镜硬件麦克风物理推流未启动，生成的录音文件均为 **44 字节空文件头**，捕获帧数 **`frames_captured: 0`**。

### 根因分析与攻坚目标

1. **必须收到对端真实回包**：
   严格废除超时强推 `READY` 的逻辑，未收到 `0x13 0x00` 时状态机必须明确停留在 `AUTH_FAILED`，严禁假就绪。
2. **GATT 特征描述符使能缺失（阻塞根因）**：
   从官方抓包发现，在 L2CAP 连接前，官方 App 会对 GATT 服务中的阿里专属 GMA 特征（CCCD 描述符 `0x2902`，句柄 `0x0023`）写入 `0x0002`（启用 Indication）。
   眼镜固件未收到此使能时，内部 GMA 协议引擎不处于激活状态，导致其忽略后续的 `0x10` 快速鉴权挑战。
3. **攻坚目标**：
   在 `ClassicBtTransport` 中补齐 GATT CCCD 使能写入，必须在真机日志中观测到眼镜回发的 `0x11` 及 `0x13 AUTH_SUCCESS`，并在录音时实际采集到有效 PCM 音频帧（`frames_captured > 0` 且 WAV > 44B）才算打通！

---

## 五、推进路线（优先级排序）

### 方案 A：从官方 App 提取已保存的 BLE Key（最快路径）

**原理**：官方 App 已经完成过一次配对鉴权，BLE Key 保存在其 SharedPreferences 中。
如果能读取到这个 Key，我们就可以直接完成本地鉴权。

**实施步骤**：
1. 通过 `adb shell run-as com.alibaba.wow cat shared_prefs/*.xml` 尝试读取
   （需要 debuggable 或 root 权限，普通 shell 无法访问）
2. 或通过 Shizuku 高权限进程读取官方 App 的 `/data/data/com.alibaba.wow/shared_prefs/`
3. 或在官方 App 运行时通过 `dumpsys` 间接提取

**风险**：非 root 设备无法直接读取其他应用的私有存储。
需要 Shizuku + root 或 backup 提取。

### 方案 B：实现云端鉴权（完整独立方案）

**原理**：首次配对时，官方 App 通过阿里云 MTOP API 进行云端鉴权获取 BLE Key。
我们可以逆向该 API 并自行实现。

**实施步骤**：
1. 逆向 APK 中 `deliveryRandom` / `deliveryVerify` 的网络请求
2. 构造合法的 MTOP 签名请求
3. 从云端获取 BLE Key
4. 保存并用于后续本地鉴权

**风险**：MTOP API 有签名校验、设备绑定等反逆向措施，实现难度较高。

### 方案 C：Hook 官方 App 在运行时捕获鉴权帧（中间人）

**原理**：在官方 App 进行鉴权交换时，通过 HCI 抓包或 Frida Hook 截获完整的 0x14/0x15/0x13 交互帧。

**实施步骤**：
1. 先用官方 App 完成一次正常连接（触发鉴权）
2. 从 HCI 抓包中提取 0x14/0x15/0x13 帧
3. 分析 HMAC 计算过程，提取或推导 BLE Key

**风险**：BLE Key 不在 HCI 报文中明文传输；需要结合 APK 逆向才能还原。

### 方案 D：分析已有 HCI 抓包中的鉴权帧（零成本）

**原理**：我们已有 bt_official.log（18MB）和 bt_reconn.cfa（7.7MB）两份抓包，
其中可能包含了上一次官方 App 的完整鉴权交换。

**实施步骤**：
1. 在已有抓包中搜索 Handle 0x000a 上 CID 0x0040/0x0041 的非 JSON 二进制帧
2. 识别 0x14（Auth Request）和 0x15（Auth Response）帧
3. 提取 randomA、randomB、HMACDevice 等参数
4. 结合 APK 逆向的算法还原 BLE Key

---

## 六、文件清单

### 核心源码
- `app/src/main/java/com/vibeqwen/glasses/bluetooth/ClassicBtTransport.kt` - 双通道蓝牙传输层
- `app/src/main/java/com/vibeqwen/glasses/protocol/QwenHandshake.kt` - 握手状态机
- `app/src/main/java/com/vibeqwen/glasses/protocol/QwenCommands.kt` - 指令构造器
- `app/src/main/java/com/vibeqwen/glasses/protocol/QwenFramer.kt` - GCSP v2 帧封装（含 CRC16）
- `app/src/main/java/com/vibeqwen/glasses/protocol/QwenFrameParser.kt` - 398B 音频帧解析器
- `app/src/main/java/com/vibeqwen/glasses/protocol/GcspFrameReassembler.kt` - 流式帧重组器
- `app/src/main/java/com/vibeqwen/glasses/protocol/GmaProtocolHandler.kt` - GMA 二进制应答器
- `app/src/main/java/com/vibeqwen/glasses/protocol/QwenConstants.kt` - 协议常量
- `app/src/main/java/com/vibeqwen/glasses/service/GlassesConnectionService.kt` - 核心前台服务
- `app/src/main/java/com/vibeqwen/glasses/debug/DebugProvider.kt` - ADB CLI 调试接口
- `app/src/main/java/com/vibeqwen/glasses/debug/DebugBridge.kt` - ADB Broadcast 调试桥

### 抓包档案（电脑端）
- `bt_fresh.cfa` (7.0MB) - 官方 App 录音时的完整 HCI 报文
- `bt_reconn.cfa` (7.7MB) - 官方 App 冷启动重连的 HCI 报文
- `bt_official.log` (18.7MB) - 早期官方 HCI 报文

### 工具
- `tools/qwen` - 手机端 ADB CLI 快捷调试脚本
- `tools/debug_send.mjs` - PC 端 vibeADB 动态报文工具
- `tools/vibeadb_shell.mjs` - PC 端 vibeADB shell 执行器

---

## 七、设备信息

| 项目 | 值 |
|------|-----|
| 手机 | vivo V2425A, Android 16 (API 36) |
| 眼镜主 MAC | C4:D7:DC:40:19:1C (HFP/A2DP) |
| 眼镜芯片 | BES2800 + Snapdragon AR1 |
| 眼镜固件 | 1.10.0-RS-20260826.0248 |
| 眼镜设备型号 | AILABS_SG02_QW |
| 眼镜 SN | 5200002612240211A002181 |
| L2CAP PSM | 130 |
| RFCOMM Audio Channel | 16 |
| BLE Service UUID | 0xFEB3 |
| BLE MFR ID | 424 (0x1A8) |
