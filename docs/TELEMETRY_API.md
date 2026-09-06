# 千问 G1 眼镜全量遥测与传感器数据 API 规格指南 (Telemetry & Sensor Reference)

> **版本**：v1.0 (教程级详细规范)  
> **适用硬件**：千问 G1 智能眼镜（恒玄 BES2800 协处理器 + 高通骁龙 AR1 主芯片）  
> **基准固件**：`1.10.0-RS-20260826.0248` / ODM 标识 `AILABS_SG02_QW`  
> **传输信道**：BLE L2CAP CoC (PSM `130` / `0x0082`)，主通道 `CID = 0x0001`

---

## 目录
1. [协议定界与通用帧头结构](#1-协议定界与通用帧头结构)
2. [佩戴与光敏检测 API (`wear` & `als_sensor`)](#2-佩戴与光敏检测-api-wear--als_sensor)
3. [电池与充电盒物理遥测 API (`battery` & `power`)](#3-电池与充电盒物理遥测-api-battery--power)
4. [头部体态与颈椎健康监控 API (`Posture & Sedentary`)](#4-头部体态与颈椎健康监控-api-posture--sedentary)
5. [镜腿触摸与手势交互 API (`input` & `touch`)](#5-镜腿触摸与手势交互-api-input--touch)
6. [蓝牙底层链路质量与射频诊断 API (`bwt_dbg` & `rssi`)](#6-蓝牙底层链路质量与射频诊断-api-bwt_dbg--rssi)
7. [摄像头、端侧视觉 AI 与多媒体同步 API (`camera` & `vision`)](#7-摄像头端侧视觉-ai-与多媒体同步-api-camera--vision)
8. [双芯片能耗与系统运行状态 API (`SoC & System`)](#8-双芯片能耗与系统运行状态-api-soc--system)
9. [实战接入代码教程 (Kotlin / TypeScript)](#9-实战接入代码教程-kotlin--typescript)

---

## 1. 协议定界与通用帧头结构

所有遥测数据包均通过 BLE L2CAP CoC 链路以 GCSP 格式传输。底层传输采用权威的 **PDU 长度定界规范**，绝不可依赖字符匹配。

### 1.1 8 字节二进制定界头
接收到的每包裸字节流布局如下：

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  0x01 (CID)   |  PDU Length (High)  |   PDU Length (Low)    |  Flag (0x24)  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Segment (00) |  MsgID (递增匹配)    |  NameSpace (命名空间)  |  CmdID (指令) |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
|             UTF-8 JSON 业务载荷 (长度 = PDU Length - 5)         |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### 1.2 关键参数计算公式
- **整帧长度**：`totalFrameLen = 3 + (((frame[1] & 0x0F) << 8) | (frame[2] & 0xFF))`；
- **遥测数据特征**：遥测帧的 `NameSpace` 通常为 `0x17`（事件通知）、`0x10`（AliGenie 镜像）或 `0x08`（设备属性）；`Flag` 多数为 `0x24` 或 `0x04`；
- **自动应答规则**：若 `(flag & 0x20) != 0` 或 `flag == 0x24`，客户端收到遥测数据后**必须毫秒级回发 8 字节 ACK**，否则眼镜将认为信令超时并断连：
  ```text
  01 00 05 10 00 [MsgID] [NameSpace] [CmdID]
  ```

---

## 2. 佩戴与光敏检测 API (`wear` & `als_sensor`)

千问 G1 眼镜在左右鼻托与内侧镜框集成了红外接近传感器（Proximity Sensor）与物理温度探头，用于精确识别人脸贴合度、离脸动作与传感器芯片工况。

### 2.1 实时光敏接近与温度矩阵 (`eventType: "wear", eventName: "data"`)

- **触发时机**：佩戴状态发生波动、头部剧烈运动或周期性巡检（约每 3~5 秒一次）。
- **NameSpace**：`0x17`，**CmdID**：`0x01`，**Flag**：`0x24`。

#### 载荷示例
```json
{
  "eventType": "wear",
  "eventName": "data",
  "contextInfo": {
    "left_diff": [33861, 52773, 53362, 51744, 53003, 51992],
    "right_valid": [62406, 62348, 62017, 62529, 63118, 61500],
    "right_threshold": [23794, 527284, 527492, 523530, 524848],
    "right_raw_data": [140220, 140214, 139960, 140464, 140962],
    "left_temp": 38388,
    "right_temp": 33200,
    "ps_temp": 0
  },
  "deviceType": "bes2800",
  "extendInfo": {
    "systemVer": "1.10.0-RS-20260826.0248",
    "log_time": "2026-09-05 13:02:46.556",
    "log_timestamp": "1788613366556"
  }
}
```

#### 字段详解
| 字段路径 | 数据类型 | 单位/量程 | 含义与解析说明 |
|---|---|---|---|
| `contextInfo.left_diff` | Array<Int> | 原始 ADC 数值 | 左侧光敏通道反射基线差值数组。贴合人脸时数值骤升 |
| `contextInfo.right_valid` | Array<Int> | 原始 ADC 数值 | 右侧有效近接检测门限判定采样点 |
| `contextInfo.right_threshold` | Array<Int> | 阈值标准 | 动态自适应佩戴判定门限 |
| `contextInfo.right_raw_data` | Array<Int> | 裸 ADC 计数值 | 右侧红外接收管未经滤波的真实物理反射采样 |
| `contextInfo.left_temp` | Int | 0.001 ℃ | **左镜腿传感器物理温度**。`38388` 即 `38.388 ℃` |
| `contextInfo.right_temp` | Int | 0.001 ℃ | **右镜腿传感器物理温度**。`33200` 即 `33.200 ℃` |
| `contextInfo.ps_temp` | Int | 0.001 ℃ | 鼻托光学近接芯片内部结温 |

### 2.2 离脸 / 佩戴动作事件 (`reason: "WEAR" | "UNWEAR"`)

- **触发时机**：用户摘下眼镜或将眼镜戴上鼻梁瞬间。

#### 载荷示例
```json
{
  "traceId": "2ea2956490598510c85c4f9131cdc60e",
  "action": "start",
  "reason": "UNWEAR"
}
```
- **业务用途**：实现摘镜自动暂停录音/播放，戴上自动恢复会话。

### 2.3 环境光传感器采样 (`als_sensor_sensor_start` / `als_sensor_sensor_end`)
- **说明**：Ambient Light Sensor 上报，包含前置光敏传感器的积分测光周期与当前物理环境勒克斯（Lux）采样。

---

## 3. 电池与充电盒物理遥测 API (`battery` & `power`)

千问 G1 眼镜具备微米级电化学电池监控系统，并通过镜腿尾部物理金属 Pogopin 触点与便携充电盒建立专用数据链路。

### 3.1 电池与充电盒状态深度上报 (`eventType: "battery", eventName: "battery_status"`)

- **触发时机**：电量变化、充放电状态切换、眼镜入盒/出盒瞬间，或每 30 秒心跳上报。
- **NameSpace**：`0x17`，**CmdID**：`0x01`。

#### 载荷示例
```json
{
  "eventType": "battery",
  "eventName": "battery_status",
  "contextInfo": {
    "batteryCapacityLevel": 77,
    "chargerBoxBatteryChargeState": 0,
    "chargerBoxBatteryCapacityLevel": 53,
    "batteryStateOfHealth": 100,
    "voltage": 4099,
    "x_ocv": 3954,
    "x_current": 50,
    "x_cycle": 31,
    "cycle": 26,
    "inbox": 0,
    "health": "good",
    "plugged": 1,
    "authorize": 0
  },
  "deviceType": "bes2800"
}
```

#### 字段详解
| 字段名 | 类型 | 取值范围 / 单位 | 含义与业务逻辑 |
|---|---|---|---|
| `batteryCapacityLevel` | Int | `0` ~ `100` (%) | **眼镜本体当前剩余百分比** |
| `chargerBoxBatteryCapacityLevel`| Int | `0` ~ `100` (%) | **便携充电盒自身剩余电量百分比** |
| `chargerBoxBatteryChargeState` | Int | `0` (未充) / `1` (充电中) | 充电盒外接 Type-C 线的供电状态 |
| `inbox` | Int | `0` (盒外) / `1` (盒内) | **眼镜当前物理位置**（出盒戴在头上还是收纳在盒中） |
| `batteryStateOfHealth` (SOH) | Int | `0` ~ `100` (%) | 电池健康度，出厂为 100 |
| `voltage` | Int | mV (毫伏) | 电池组端电压（如 `4099 mV` = `4.099 V`） |
| `x_ocv` | Int | mV (毫伏) | 开路电压（Open Circuit Voltage）真实物理估算值 |
| `x_current` | Int | mA (毫安) | 实时工作电流。正值为充电流入，负值为放电消耗 |
| `cycle` | Int | 次数 | 固件记录的电池全循环充放电次数（如 `26` 次） |

---

## 4. 头部体态与颈椎健康监控 API (`Posture & Sedentary`)

眼镜内置六轴 IMU（低功耗陀螺仪 + 加速度计），在端侧进行姿态融合算法运算，并将健康体态数据打包上报至 AliGenie 采集总线。

### 4.1 颈椎俯仰角与低头健康监控 (`HealthyPostureMonitor`)

- **NameSpace**：`AliGenie.GatherConfig`，**eventName**：`HealthyPostureMonitor`。

#### 载荷示例
```json
{
  "eventNs": "AliGenie.GatherConfig",
  "eventName": "HealthyPostureMonitor",
  "payLoad": {
    "data": "1788581572952,0,60,15,4;"
  },
  "externFlag": false
}
```

#### CSV 数据串解析规则
`data` 字段为分号结尾的逗号分隔字符串：
`"时间戳,低头状态标识,统计时间窗口秒数,平均低头倾角,不良体态次数;"`
- `1788581572952`: 统计周期起始基准毫秒戳；
- `0`: 标志位（`0` 正常头部仰角，`1` 严重低头超标）；
- `60`: 时间窗口（以 60 秒为基准单元）；
- `15`: 俯仰倾角（Pitch 角度偏离人脸垂直轴度数）；
- `4`: 监测到剧烈低头或不良习惯的频次。

### 4.2 久坐不动与步态巡检 (`SedentaryMonitor`)

#### 载荷示例
```json
{
  "eventNs": "AliGenie.GatherConfig",
  "eventName": "SedentaryMonitor",
  "payLoad": {
    "data": "1788581529878,1,60;"
  },
  "externFlag": false
}
```
- `data`: `"时间戳,活动级别,窗口时长"`（`1` 表示静坐不动，持续累计将触发眼镜端蜂鸣或震动久坐提醒）。

---

## 5. 镜腿触摸与手势交互 API (`input` & `touch`)

千问 G1 眼镜外侧镜腿集成了电容触控感应条（Touch Slider）。用户的手指交互在端侧以 `0.01ms` 精度捕获并立即广播。

### 5.1 物理电容接触原始事件 (`eventType: "input", eventName: "event" | "handle"`)

- **NameSpace**：`0x17`，**CmdID**：`0x01`。

#### 载荷示例
```json
{
  "eventType": "input",
  "eventName": "event",
  "contextInfo": {
    "type": 78,
    "typeName": "INPUT_EVENT_MEDIA_MULTI_FINGER_LONG",
    "timestamp": 1788613389107
  },
  "deviceType": "bes2800"
}
```

#### 交互按键/手势字典映射表
| `type` 代码 | 官方内部常量名称 | 用户物理动作 | 官方默认触发功能 |
|---|---|---|---|
| `76` | `INPUT_EVENT_MEDIA_PRESS_DOWN` | 单指按下镜腿触控板 | 交互起始标志 |
| `77` | `INPUT_EVENT_MEDIA_PRESS_UP` | 手指离开镜腿触控板 | 交互终止标志 |
| `72` | `INPUT_EVENT_MEDIA_CLICK_SINGLE` | 单击镜腿 | 播放 / 暂停音乐 |
| `73` | `INPUT_EVENT_MEDIA_CLICK_DOUBLE` | 双击镜腿 | 下一曲 / 接听电话 |
| `74` | `INPUT_EVENT_MEDIA_CLICK_TRIPLE` | 三击镜腿 | 开启相机抓拍 / 录像 |
| `75` | `INPUT_EVENT_MEDIA_LONG_PRESS` | 单指长按镜腿 (>1.5s) | 唤出千问 AI 对话助手 |
| `78` | `INPUT_EVENT_MEDIA_MULTI_FINGER_LONG`| **双指长按镜腿** | **眼镜端主动触发录音 (`AudioRecording`)** |
| `80` | `INPUT_EVENT_SLIDE_FORWARD` | 沿镜腿向前滑动 | 增大音量 |
| `81` | `INPUT_EVENT_SLIDE_BACKWARD` | 沿镜腿向后滑动 | 减小音量 |

### 5.2 端侧意图识别事件 (`AliGenie.Text:Recognize`)
当用户执行手势触发语音或任务时，端侧 NPU 识别层直接送达意图：
```json
{
  "eventNs": "AliGenie.Text",
  "eventName": "Recognize",
  "payLoad": {
    "inputText": "打开会议录音",
    "wakeupType": "press",
    "pressContext": {
      "type": "threeFingerLongPress"
    }
  }
}
```

---

## 6. 蓝牙底层链路质量与射频诊断 API (`bwt_dbg` & `rssi`)

### 6.1 无线传输性能诊断 (`bwt_dbg_report`)
- **说明**：Bluetooth Wireless Transport Debug Report，上报当前 L2CAP 通道的高频重传、丢包率、缓冲区水位：
```json
{
  "eventType": "gma_pair",
  "eventName": "bwt_dbg_report",
  "contextInfo": {
    "curr_rssi": -58,
    "tx_packets": 1420,
    "rx_packets": 2860,
    "retransmit_cnt": 0,
    "buffer_watermark": 12
  }
}
```

### 6.2 信号强度与距离衰减 (`peer_rssi`)
```json
{
  "peer_rssi": -58,
  "time": "2026-9-5 12:10:03"
}
```
- 配合此指标可在 UI 实时展示眼镜与手机连接信号格数（`-50 ~ -60 dBm` 极佳，`<-85 dBm` 濒临断开）。

---

## 7. 摄像头、端侧视觉 AI 与多媒体同步 API (`camera` & `vision`)

高通骁龙 AR1 芯片负责前置 1200 万像素相机的拍摄与边缘轻量级图像识别。

### 7.1 相机 HAL 自动对焦与曝光收敛 (`ar1_cameraHAL:aeConverge`)
```json
{
  "eventType": "ar1_cameraHAL",
  "eventName": "aeConverge",
  "deviceType": "ar1",
  "contextInfo": {
    "fwkId": "9",
    "aeState": "2"
  }
}
```
- `aeState: 2`: 自动曝光（Auto Exposure）算法锁定就绪，画面达到最佳动态范围。

### 7.2 端侧边缘 AI 主体与防抖检测 (`subjectDetection` & `blurDetection`)
拍照前，AR1 芯片实时跑边缘推理网络，判断取景框内容：
```json
{
  "subjectDetection": {
    "clean": 0.8,
    "hand": 0.75,
    "enabled": true
  },
  "blurDetection": {
    "threshold": 0.4,
    "algorithm": "string"
  },
  "documentDetection": {
    "document": 0.75,
    "enabled": true
  },
  "format": "heic",
  "resolution": {
    "width": 1680,
    "height": 1264
  }
}
```
- `clean`: 画面主体无杂物遮挡的纯净度置信度（`0.8`）；
- `hand`: 是否检测到手部（`0.75`）；
- `document`: 是否正在对准文档/PPT/名片（`0.75`）；
- `resolution`: 采用的高画质低体积 HEIC 分辨率（`1680x1264`）。

### 7.3 拍照生成与缩略图推送 (`PhotoTaken` & `fileCount`)
```json
{
  "eventNs": "AliGenie.Camera",
  "eventName": "PhotoTaken",
  "payLoad": {
    "sessionID": "1788586011319",
    "cameraProcessStatus": 2,
    "imageId": "1788586011414",
    "cameraUiCaller": "InternalNormalCamera",
    "playCaptureSound": true
  }
}
```
伴随紧接着的缩略图索引通知：
```json
{
  "fileCount": 1,
  "fileName": "20260904120234708_pic_thumb.jpg",
  "index": 0,
  "reporterId": 2,
  "thumbnailCount": 1
}
```
手机收到后可通过 WebFS / TFTP 快速拉取缩略图呈现在 UI。

---

## 8. 双芯片能耗与系统运行状态 API (`SoC & System`)

### 8.1 AR1 主芯片休眠挂起通知 (`soc_state`)
```json
{
  "soc_state": "SUSPEND"
}
```
- 当用户没有使用相机、没有大图传输时，高通安卓芯片进入 `SUSPEND` 深度待机模式，电流压低至微安级；仅恒玄 BES2800 维持 BLE 监听。

### 8.2 系统内存分级与配额健康度
```json
{
  "critical": 0,
  "high": 0,
  "normal": 400,
  "low": 0,
  "total": 400,
  "capacity": 1200,
  "used_percent": 33
}
```
- 若系统内存不足，会触发 `status: "onLowMemory"`，客户端应主动推迟大图下载请求。

---

## 9. 实战接入代码教程 (Kotlin / TypeScript)

### 9.1 Kotlin 端完整解包与事件分发总线
在 Android 客户端的 `GcspFrameReassembler.kt` 中挂载：

```kotlin
// 解析遥测 JSON 并结构化分发
fun dispatchTelemetry(json: String) {
    val root = JSONObject(json)
    
    // 1. 电池与充电盒状态
    if (root.optString("eventName") == "battery_status") {
        val ctx = root.getJSONObject("contextInfo")
        val glassesBattery = ctx.getInt("batteryCapacityLevel")
        val boxBattery = ctx.getInt("chargerBoxBatteryCapacityLevel")
        val isBoxCharging = ctx.getInt("chargerBoxBatteryChargeState") == 1
        val isInBox = ctx.getInt("inbox") == 1
        Log.i("Telemetry", "眼镜电量: $glassesBattery%, 盒子电量: $boxBattery% (盒内: $isInBox, 盒充中: $isBoxCharging)")
    }
    
    // 2. 佩戴与离脸
    if (root.optString("eventType") == "wear") {
        val ctx = root.getJSONObject("contextInfo")
        val leftTemp = ctx.optInt("left_temp") / 1000.0
        val rightTemp = ctx.optInt("right_temp") / 1000.0
        Log.i("Telemetry", "镜腿物理温度: 左侧 ${leftTemp}℃ / 右侧 ${rightTemp}℃")
    }
    if (root.optString("reason") == "UNWEAR") {
        Log.w("Telemetry", "用户摘下眼镜！")
    }
    
    // 3. 镜腿触控手势
    if (root.optString("eventType") == "input") {
        val ctx = root.getJSONObject("contextInfo")
        when (ctx.optInt("type")) {
            78 -> Log.i("Telemetry", "【双指长按】触发眼镜端本地录音")
            73 -> Log.i("Telemetry", "【双击镜腿】播放/暂停控制")
            80 -> Log.i("Telemetry", "【前滑】音量加")
            81 -> Log.i("Telemetry", "【后滑】音量减")
        }
    }
}
```

### 9.2 TypeScript 抓包与自动化测试过滤脚本
在抓包分析工具中实时解析：

```typescript
export function parseGlassesFrame(rawBuffer: Buffer) {
  if (rawBuffer[0] !== 0x01) return null;
  const pduLen = ((rawBuffer[1] & 0x0f) << 8) | rawBuffer[2];
  if (rawBuffer.length < 3 + pduLen) return null; // 待完整分包

  const flag = rawBuffer[3];
  const msgId = rawBuffer[5];
  const nameSpace = rawBuffer[6];
  const cmdId = rawBuffer[7];

  const payload = rawBuffer.subarray(8, 3 + pduLen).toString('utf-8');
  return {
    flag,
    msgId,
    nameSpace,
    cmdId,
    payload: JSON.parse(payload),
    requiresAck: (flag & 0x20) !== 0 || flag === 0x24
  };
}
```
