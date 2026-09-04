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

## 四、核心卡点攻克记录（2026-09-05 已彻底攻克！）

### ✅ GMA 鉴权与实机直连已完全打通！

在 Root 真机（OnePlus 6 / Android 14）上通过 vibeADB 完成了全链路逆向抓包与密钥提取，此前阻碍项目的鉴权与音频通道两大核心卡点已全部告破：

1. **已提取的核心密钥与凭据**：
   - 眼镜 MAC：`C4:D7:DC:40:19:1C`
   - Product ID：`8665` (`0x21D9`)
   - GMA Device UUID：`D5A74C04894A4E70C2AE0BDC687904FE`
   - GMA BLE Key：`19f2bb2b7bff8e994b7e244f65a989c7`
   - LinkKey：`62fdde4d42cd97eb331561e9117e1977`
   - LTK：`6cc0a23911c2205aedeb0280c6fb1dff`

2. **GMA 快速鉴权协议机制（0x10 ~ 0x13）**：
   - 手机下发 `0x10` 挑战帧 (26B，携带 16 字节随机数 RandomA)
   - 眼镜返回 `0x11` 响应帧 (26B，携带 16 字节设备随机数 RandomB)
   - 手机回复 `0x12` 确认帧 (11B)
   - 眼镜上报 `0x13` 鉴权成功通知 (`09 00 01 00 06 10 00 01 00 13 00`)！

3. **双通道连接实测**：
   - **Insecure L2CAP PSM 130**：建立耗时仅 233ms！
   - **经典蓝牙 RFCOMM Channel 16**：并行建立成功！
   - 下发 `{"type":1103,"data":"D5A74C04894A4E70C2AE0BDC687904FE"}` 与 `{"code":1,"msg":"attach_success"}`，App 真实进入 `READY` 状态！

4. **音频流物理通道与帧格式实证**：
   - 真实抓包提取出 851 帧完整 16kHz PCM 音频，总时长 10.21 秒，已成功无损还原并输出为 `extracted_real_record.wav`。
   - 音频推流直接复用 **BLE L2CAP CoC 通道 (PSM 130)**，帧长 395 字节（魔数 `89 01 07 01 86 08` + 1B 序号 + 4B 填充 + 384B PCM），每帧分两段 ACL 传输（247B + 148B）。

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
