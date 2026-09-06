# 千问 G1 眼镜录音组合测试报告

- 日志文件: `bt_combo_test.log`
- 日志时间范围: 2026-08-30 14:08:04.800 CST ~ 2026-08-30 14:14:16.609 CST（vivo btsnoop 时间戳为本地 CST 墙上时间，基准≈公元元年，经 log_timestamp 校准）
- HCI 记录总数: 20355
- 包类型: H4-ACL=18165, raw-ACL=84, 其他H4=2106, 无法解析=0, L2CAP头截断=37, 孤立续包=32

## 1. 协议摘要

- 音频通道 CID `0x0047`（本日志实测；参考日志为 0x0048，CID 因手机/固件而异，按魔数头全局匹配），帧 398B：8B 魔数 `87 EF 12 03 07 01 86 08` + 1B 序号 + 4B 填充 + 384B PCM(16bit LE, 16000Hz, 单声道) + 1B 填充
- 控制通道：CID `0x004A`（手机→眼镜 JSON 指令）、CID `0x0041`（眼镜→手机 JSON 事件）
- 提取规则：跳过前 13 字节取 384B PCM，丢弃最后一字节

## 2. 音频通道(0x0047)流量随时间分布（按秒）

| 相对秒 | 时间(CST) | 字节 | 包数 | 帧数 | 标记 |
|---|---:|---:|---:|---:|---|
| +15s | 2026-08-30 14:08:19.800 CST | 37412 | 94 | 94 | 音频突发 |
| +16s | 2026-08-30 14:08:20.800 CST | 34228 | 86 | 86 | 音频突发 |
| +17s | 2026-08-30 14:08:21.800 CST | 32238 | 81 | 81 | 音频突发 |
| +18s | 2026-08-30 14:08:22.800 CST | 34228 | 86 | 86 | 音频突发 |
| +19s | 2026-08-30 14:08:23.800 CST | 31442 | 79 | 79 | 音频突发 |
| +20s | 2026-08-30 14:08:24.800 CST | 34626 | 87 | 87 | 音频突发 |
| +21s | 2026-08-30 14:08:25.800 CST | 31442 | 79 | 79 | 音频突发 |
| +26s | 2026-08-30 14:08:30.800 CST | 2388 | 6 | 6 | 有帧 |
| +27s | 2026-08-30 14:08:31.800 CST | 34228 | 86 | 86 | 音频突发 |
| +28s | 2026-08-30 14:08:32.800 CST | 33432 | 84 | 84 | 音频突发 |
| +29s | 2026-08-30 14:08:33.800 CST | 32636 | 82 | 82 | 音频突发 |
| +30s | 2026-08-30 14:08:34.800 CST | 34228 | 86 | 86 | 音频突发 |
| +31s | 2026-08-30 14:08:35.800 CST | 32636 | 82 | 82 | 音频突发 |
| +32s | 2026-08-30 14:08:36.800 CST | 33034 | 83 | 83 | 音频突发 |
| +33s | 2026-08-30 14:08:37.800 CST | 33034 | 83 | 83 | 音频突发 |
| +34s | 2026-08-30 14:08:38.800 CST | 4776 | 12 | 12 | 有帧 |
| +39s | 2026-08-30 14:08:43.800 CST | 32238 | 81 | 81 | 音频突发 |
| +40s | 2026-08-30 14:08:44.800 CST | 33034 | 83 | 83 | 音频突发 |
| +41s | 2026-08-30 14:08:45.800 CST | 32636 | 82 | 82 | 音频突发 |
| +42s | 2026-08-30 14:08:46.800 CST | 33432 | 84 | 84 | 音频突发 |
| +43s | 2026-08-30 14:08:47.800 CST | 32238 | 81 | 81 | 音频突发 |
| +44s | 2026-08-30 14:08:48.800 CST | 34228 | 86 | 86 | 音频突发 |
| +45s | 2026-08-30 14:08:49.800 CST | 33432 | 84 | 84 | 音频突发 |
| +46s | 2026-08-30 14:08:50.800 CST | 3980 | 10 | 10 | 有帧 |
| +49s | 2026-08-30 14:08:53.800 CST | 7562 | 19 | 19 | 有帧 |
| +50s | 2026-08-30 14:08:54.800 CST | 34228 | 86 | 86 | 音频突发 |
| +51s | 2026-08-30 14:08:55.800 CST | 32238 | 81 | 81 | 音频突发 |
| +52s | 2026-08-30 14:08:56.800 CST | 33432 | 84 | 84 | 音频突发 |
| +53s | 2026-08-30 14:08:57.800 CST | 33432 | 84 | 84 | 音频突发 |
| +54s | 2026-08-30 14:08:58.800 CST | 33034 | 83 | 83 | 音频突发 |
| +55s | 2026-08-30 14:08:59.800 CST | 33432 | 84 | 84 | 音频突发 |
| +56s | 2026-08-30 14:09:00.800 CST | 3980 | 10 | 10 | 有帧 |

## 3. 四次录音提取结果

| 组合 | 触发方式 | 起始时间(CST) | 结束时间(CST) | 帧数 | 时长(帧推算) | 时长(时间戳) | 序号跳变 | WAV 文件 |
|---|---|---|---:|---:|---:|---:|---:|---|
| 1 | 眼镜发起 + 眼镜结束 | 2026-08-30 14:08:20.131 CST | 2026-08-30 14:08:26.788 CST | 592 | 7.10s | 6.66s | 0 | `combo_1_glasses_start_glasses_stop.wav` |
| 2 | 手机发起 + 手机结束 | 2026-08-30 14:08:31.712 CST | 2026-08-30 14:08:39.006 CST | 604 | 7.25s | 7.29s | 0 | `combo_2_phone_start_phone_stop.wav` |
| 3 | 眼镜发起 + 手机结束 | 2026-08-30 14:08:43.897 CST | 2026-08-30 14:08:50.966 CST | 591 | 7.09s | 7.07s | 0 | `combo_3_glasses_start_phone_stop.wav` |
| 4 | 手机发起 + 眼镜结束 | 2026-08-30 14:08:54.568 CST | 2026-08-30 14:09:00.985 CST | 531 | 6.37s | 6.42s | 0 | `combo_4_phone_start_glasses_stop.wav` |

## 4. WAV 文件详情

| 文件 | 时长 | 大小(字节) | 帧数 | 峰值 | RMS | 非零样本比 |
|---|---:|---:|---:|---:|---:|---:|
| `combo_1_glasses_start_glasses_stop.wav` | 7.10s | 227372 | 592 | 8815 | 1102.1 | 99.7% |
| `combo_2_phone_start_phone_stop.wav` | 7.25s | 231980 | 604 | 8647 | 1175.6 | 99.8% |
| `combo_3_glasses_start_phone_stop.wav` | 7.09s | 226988 | 591 | 8690 | 1189.3 | 99.8% |
| `combo_4_phone_start_glasses_stop.wav` | 6.37s | 203948 | 531 | 10930 | 1399.1 | 99.7% |

## 5. 录音控制指令/事件

### 5.1 组合 1（眼镜发起 + 眼镜结束）

录音窗口: 2026-08-30 14:08:20.131 CST ~ 2026-08-30 14:08:26.788 CST（6.66s，592 帧）
触发方式判定: 发起=眼镜，结束=眼镜

开始指令/事件:
- `2026-08-30 14:08:19.147 CST` CID 0x004a H->C: `{"code":"AudioRecording","extensions":{"taskLinkId":"AudioRecording178807009884994E17F7751E9432894D02B103B4311AD"},"sessionId":8391553,"traceId":"0b5d39f417880700981117526d0fa3"}`
- `2026-08-30 14:08:19.147 CST` CID 0x004a H->C: `{"scene":"AudioRecording","sessionId":8391553,"taskLinkId":"AudioRecording178807009884994E17F7751E9432894D02B103B4311AD","traceId":"0b5d39f417880700981117526d0fa3","wakeupType":"longRecord"}`
- `2026-08-30 14:08:19.148 CST` CID 0x004a H->C: `{"data":{"dialogId":"44354137344330345f313538343930313134353939363435383135335f7ffffe5faeb7883d"},"pageType":"SCHEME_AIRECORD_START","sessionId":8391553,"traceId":"0b5d39f417880700981117526d0fa3","uri":"airecord://start"}`
- `2026-08-30 14:08:19.458 CST` CID 0x0041 C->H: `{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178807009884994E17F7751E9432894D02B103B4311AD","scene":"AudioRecording","sessionId":8391553},"reason":"CLOUD"},"background":[]}`
结束指令/事件:
- `2026-08-30 14:08:26.780 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"0b5d39f417880700981117526d0fa3","status":"TryExit","reason":"CLOUD","reasonStop":"KEY","hint":"","context":"{}"}`
- `2026-08-30 14:08:27.438 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"0b5d39f417880700981117526d0fa3","status":"Exiting","reason":"CLOUD","reasonStop":"KEY","hint":"","context":"{}"}`
- `2026-08-30 14:08:27.478 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"0b5d39f417880700981117526d0fa3","status":"Exited","reason":"CLOUD","reasonStop":"KEY","hint":"","context":"{}"}`
眼镜侧遥测事件（内部时间 CST，递达时间见括号）:
- 2026-08-30 14:08:19.374 CST（递达 2026-08-30 14:08:27.441 CST）power-state/record_start: `{"battery":43,"currentTask":"AudioRecording","startTime":1788098899374}`
- 2026-08-30 14:08:26.786 CST（递达 2026-08-30 14:08:27.828 CST）power-state/record_end: `{"battery":43,"currentTask":"AudioRecording","endTime":1788098906786,"duration":7412}`
- 2026-08-30 14:08:19.678 CST（递达 2026-08-30 14:08:27.860 CST）AudioRecording/onHandler: `{"msg":1,"talkSessionIdBefore":1121155,"recordDataSent":592,"recordDataStartTime":1788098899678,"recordDataSentDura":7125}`

窗口内关键消息:
- `2026-08-30 14:08:17.089 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"event","contextInfo":{"type":76,"typeName":"INPUT_EVENT_MEDIA_PRESS_DOWN","value":0,"timestampMs":472799,"isTriggerByUser":true},"deviceType":"bes2800","extendInfo":{`
- `2026-08-30 14:08:17.150 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"handle","contextInfo":{"type":76,"typeName":"INPUT_EVENT_MEDIA_PRESS_DOWN","value":0,"timestampMs":472799,"isTriggerByUser":true,"handler":"default"},"deviceType":"be`
- `2026-08-30 14:08:17.337 CST` CID 0x0041 C->H: `(分段JSON) {\"document\":0.75,\"enabled\":true}}}"},{"identifier":"asrOnGW","value":"{\"value\":true}"},{"identifier":"asrRecordTime","value":"{\"value\":5}"},{"identifier":"bluetoothDualConfig","value":"{\"dual`
- `2026-08-30 14:08:17.342 CST` CID 0x0041 C->H: `(分段JSON) {"identifier":"cameraHybridRaw","value":"{\"enable\":true}"},{"identifier":"characterId","value":"{\"systemInit\":true,\"characterVoiceTemplateId\":83,\"value\":488}"},{"identifier":"cityEscort","valu`
- `2026-08-30 14:08:17.363 CST` CID 0x0041 C->H: `(分段JSON) {"identifier":"pstnCallAbility","value":"{}"},{"identifier":"recordConfig","value":"{\"enhanceVocals\":false,\"pickupDirection\":\"omnidirectional\"}"},{"identifier":"screenDisplay","value":"{\"enable`
- `2026-08-30 14:08:17.365 CST` CID 0x0041 C->H: `(分段JSON) {\"enable\":true}"},{"identifier":"shortcut_wakeup","value":"{\"enable\":true}"},{"identifier":"soundEffect","value":"{\"value\":\"standard\"}"},{"identifier":"spatialEffect","value":"{\"value\":false`
- `2026-08-30 14:08:17.366 CST` CID 0x0041 C->H: `(分段JSON) {\"value\":120}"},{"identifier":"teleprompter","value":"{\"enable\":false}"},{"identifier":"touchQuery","value":"{\"enable\":true,\"text\":\"打开会议录音\"}"},{"identifier":"touchQueryOptional","value":"{\"`
- `2026-08-30 14:08:17.396 CST` CID 0x0041 C->H: `(分段JSON) {}"},{"identifier":"wakeupConfig","value":"{\"enable\":true,\"wakeWord\":\"你好千问\",\"replyFeedback\":\"voice\"}"},{"identifier":"watermark","value":"{\"logo\":\"Qianwen\"}"},{"identifier":"wearDetectio`
- `2026-08-30 14:08:17.398 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"gma_pair","eventName":"gma_pair_wrapper","contextInfo":{"version":2,"facResetCnt":0,"record":[{"event":"bt_evt","sub_event":"B4:6E:XX:XX:XX:XX","curr_state":"GMA_PAIRING_GMA_LOCAL_AUTH_D`
- `2026-08-30 14:08:17.750 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"event","contextInfo":{"type":78,"typeName":"INPUT_EVENT_MEDIA_MULTI_FINGER_LONG","value":0,"timestampMs":473605,"isTriggerByUser":true},"deviceType":"bes2800","extend`
- `2026-08-30 14:08:17.751 CST` CID 0x0041 C->H: `{"eventNs":"AliGenie.Text","eventName":"Recognize","payLoad":{"inputText":"打开会议录音","wakeupType":"press","pressContext":{"type":"threeFingerLongPress"}},"externFlag":false}`
- `2026-08-30 14:08:17.780 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"handle","contextInfo":{"type":78,"typeName":"INPUT_EVENT_MEDIA_MULTI_FINGER_LONG","value":0,"timestampMs":473605,"isTriggerByUser":true,"handler":"TouchQuery"},"devic`
- `2026-08-30 14:08:18.292 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"event","contextInfo":{"type":77,"typeName":"INPUT_EVENT_MEDIA_PRESS_UP","value":0,"timestampMs":474182,"isTriggerByUser":true},"deviceType":"bes2800","extendInfo":{"s`
- `2026-08-30 14:08:18.295 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"handle","contextInfo":{"type":77,"typeName":"INPUT_EVENT_MEDIA_PRESS_UP","value":0,"timestampMs":474182,"isTriggerByUser":true,"handler":"noCatcher"},"deviceType":"be`
- `2026-08-30 14:08:19.147 CST` CID 0x004a H->C: `{"code":"AudioRecording","extensions":{"taskLinkId":"AudioRecording178807009884994E17F7751E9432894D02B103B4311AD"},"sessionId":8391553,"traceId":"0b5d39f417880700981117526d0fa3"}`
- `2026-08-30 14:08:19.147 CST` CID 0x004a H->C: `{"scene":"AudioRecording","sessionId":8391553,"taskLinkId":"AudioRecording178807009884994E17F7751E9432894D02B103B4311AD","traceId":"0b5d39f417880700981117526d0fa3","wakeupType":"longRecord"}`
- `2026-08-30 14:08:19.148 CST` CID 0x004a H->C: `{"data":{"dialogId":"44354137344330345f313538343930313134353939363435383135335f7ffffe5faeb7883d"},"pageType":"SCHEME_AIRECORD_START","sessionId":8391553,"traceId":"0b5d39f417880700981117526d0fa3","uri":"airecord://start"}`
- `2026-08-30 14:08:19.399 CST` CID 0x0041 C->H: `(分段JSON) {"code":"AudioRecording","traceId":"0b5d39f417880700981117526d0fa3","status":"Running","reason":"CLOUD","reasonStop":null,"hint":"","context":"{\"taskLinkId\":\"AudioRecording178807009884994E17F7751E9`
- `2026-08-30 14:08:19.429 CST` CID 0x0041 C->H: `(分段JSON) {"eventContext":{"taskLayer":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178807009884994E17F7751E9432894D02B103B4311AD","scene":"AudioRecording","sessionId":8391553},"re`
- `2026-08-30 14:08:19.458 CST` CID 0x0041 C->H: `{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178807009884994E17F7751E9432894D02B103B4311AD","scene":"AudioRecording","sessionId":8391553},"reason":"CLOUD"},"background":[]}`
- `2026-08-30 14:08:19.489 CST` CID 0x0041 C->H: `(分段JSON) {"eventContext":{"taskLayer":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178807009884994E17F7751E9432894D02B103B4311AD","scene":"AudioRecording","sessionId":8391553},"re`
- `2026-08-30 14:08:19.490 CST` CID 0x0041 C->H: `(分段JSON) {"eventNs":"AliGenie.System","eventName":"SynchronizeStatus","payLoad":{"identifier":"deviceContext","value":{"taskLayer":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178`
- `2026-08-30 14:08:19.493 CST` CID 0x0041 C->H: `(分段JSON) {"format":".ogg","sceneContexts":{"taskLinkId":"AudioRecording178807009884994E17F7751E9432894D02B103B4311AD","scene":"AudioRecording"},"eventContext":{"taskLayer":{"current":{"code":"AudioRecording","`
- `2026-08-30 14:08:26.780 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"0b5d39f417880700981117526d0fa3","status":"TryExit","reason":"CLOUD","reasonStop":"KEY","hint":"","context":"{}"}`
- `2026-08-30 14:08:27.415 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"EnterAppTask","contextInfo":{"name":"AudioRecording","reason":"CLOUD","extensions":"{\"taskLinkId\":\"AudioRecording178807009884994E17F7751E9432894D02B103B4311AD\"}`
- `2026-08-30 14:08:27.416 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskInsideStage","contextInfo":{"name":"AudioRecording","stage":"Creating"},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":"2`
- `2026-08-30 14:08:27.419 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AiTalkService","eventName":"exitTalk","contextInfo":{"reason":"AudioRecording","exitWhenActive":false},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_tim`
- `2026-08-30 14:08:27.428 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskInsideStage","contextInfo":{"name":"AudioRecording","stage":"Running"},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":"20`
- `2026-08-30 14:08:27.432 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"PendingGwCmdDone","contextInfo":{"sessionId":8391553,"traceId":"0b5d39f417880700981117526d0fa3","namespaceId":13,"transactionId":3,"taskName":"AudioRecording","task`
- `2026-08-30 14:08:27.438 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"0b5d39f417880700981117526d0fa3","status":"Exiting","reason":"CLOUD","reasonStop":"KEY","hint":"","context":"{}"}`
- `2026-08-30 14:08:27.441 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"power-state","eventName":"record_start ","contextInfo":{"battery":43,"currentTask":"AudioRecording","startTime":1788098899374},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS`
- `2026-08-30 14:08:27.443 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"SingleTaskChange","contextInfo":{"code":"AudioRecording","traceId":"0b5d39f417880700981117526d0fa3","status":"Running","reason":"CLOUD","reasonStop":null,"hint":"",`
- `2026-08-30 14:08:27.451 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskChange","contextInfo":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178807009884994E17F7751E9432894D02B103B4311AD","scene":"AudioRe`
- `2026-08-30 14:08:27.454 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"PendingGwCmdDone","contextInfo":{"sessionId":8391553,"traceId":"0b5d39f417880700981117526d0fa3","namespaceId":13,"transactionId":1,"taskName":"AudioRecording","task`
- `2026-08-30 14:08:27.456 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AudioRecording","eventName":"onHandler","contextInfo":{"msg":0,"talkSessionIdBefore":-1,"taskLinkId":"AudioRecording178807009884994E17F7751E9432894D02B103B4311AD","talkSessionIdAfter":11`
- `2026-08-30 14:08:27.458 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"EnterAppTaskResp","contextInfo":{"name":"AudioRecording","token":142,"sessionId":8391553,"traceId":"0b5d39f417880700981117526d0fa3","reason":"CLOUD","success":true,`
- `2026-08-30 14:08:27.470 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"event","contextInfo":{"type":510,"typeName":"INPUT_EVENT_SENSOR_CHARGE_PORT_CLEAN","value":0,"timestampMs":1788098901218,"isTriggerByUser":false},"deviceType":"bes280`
- `2026-08-30 14:08:27.472 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"handle","contextInfo":{"type":510,"typeName":"INPUT_EVENT_SENSOR_CHARGE_PORT_CLEAN","value":0,"timestampMs":1788098901218,"isTriggerByUser":false,"handler":"noCatcher`
- `2026-08-30 14:08:27.478 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"0b5d39f417880700981117526d0fa3","status":"Exited","reason":"CLOUD","reasonStop":"KEY","hint":"","context":"{}"}`
- `2026-08-30 14:08:27.559 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"event","contextInfo":{"type":512,"typeName":"INPUT_EVENT_SENSOR_HEADATT_MONITOR","value":0,"timestampMs":1788098905709,"isTriggerByUser":false},"deviceType":"bes2800"`

### 5.2 组合 2（手机发起 + 手机结束）

录音窗口: 2026-08-30 14:08:31.712 CST ~ 2026-08-30 14:08:39.006 CST（7.29s，604 帧）
触发方式判定: 发起=手机，结束=手机

开始指令/事件:
- `2026-08-30 14:08:30.827 CST` CID 0x004a H->C: `{"code":"AudioRecording","data":{"reason":"touch"},"extensions":{"taskLinkId":"AudioRecording178807011090649A5292E5517459EB1CDC65C0BC0B013","bizType":"live"},"sessionId":"1788070110"}`
- `2026-08-30 14:08:30.828 CST` CID 0x004a H->C: `{"data":{"reason":"touch"},"scene":"AudioRecording","sessionId":"1788070110","taskLinkId":"AudioRecording178807011090649A5292E5517459EB1CDC65C0BC0B013","wakeupType":"longRecord"}`
- `2026-08-30 14:08:30.829 CST` CID 0x004a H->C: `{"data":{"reason":"touch"},"pageType":"SCHEME_AIRECORD_START","sessionId":"1788070110","uri":"airecord://start"}`
- `2026-08-30 14:08:31.159 CST` CID 0x0041 C->H: `{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178807011090649A5292E5517459EB1CDC65C0BC0B013","scene":"AudioRecording","sessionId":1788070110},"reason":"APP"},"background":[]}`
结束指令/事件:
- `2026-08-30 14:08:38.872 CST` CID 0x004a H->C: `{"type":"PART","codeList":["AudioRecording"]}`
- `2026-08-30 14:08:38.873 CST` CID 0x004a H->C: `{"code":"AudioRecording"}`
- `2026-08-30 14:08:38.987 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"76b62a47ae4bb122f3691788098910","status":"TryExit","reason":"APP","reasonStop":"APP","hint":"","context":"{}"}`
- `2026-08-30 14:08:39.619 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"76b62a47ae4bb122f3691788098910","status":"Exiting","reason":"APP","reasonStop":"APP","hint":"","context":"{}"}`
- `2026-08-30 14:08:39.678 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"76b62a47ae4bb122f3691788098910","status":"Exited","reason":"APP","reasonStop":"APP","hint":"","context":"{}"}`
眼镜侧遥测事件（内部时间 CST，递达时间见括号）:
- 2026-08-30 14:08:31.097 CST（递达 2026-08-30 14:08:41.360 CST）power-state/record_start: `{"battery":43,"currentTask":"AudioRecording","startTime":1788098911097}`
- 2026-08-30 14:08:38.983 CST（递达 2026-08-30 14:08:41.542 CST）power-state/record_end: `{"battery":43,"currentTask":"AudioRecording","endTime":1788098918983,"duration":7886}`
- 2026-08-30 14:08:31.720 CST（递达 2026-08-30 14:08:41.571 CST）AudioRecording/onHandler: `{"msg":1,"talkSessionIdBefore":1121161,"recordDataSent":604,"recordDataStartTime":1788098911720,"recordDataSentDura":7283}`

窗口内关键消息:
- `2026-08-30 14:08:27.714 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"event","contextInfo":{"type":76,"typeName":"INPUT_EVENT_MEDIA_PRESS_DOWN","value":0,"timestampMs":482281,"isTriggerByUser":true},"deviceType":"bes2800","extendInfo":{`
- `2026-08-30 14:08:27.714 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"handle","contextInfo":{"type":76,"typeName":"INPUT_EVENT_MEDIA_PRESS_DOWN","value":0,"timestampMs":482281,"isTriggerByUser":true,"handler":"default"},"deviceType":"be`
- `2026-08-30 14:08:27.738 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"event","contextInfo":{"type":77,"typeName":"INPUT_EVENT_MEDIA_PRESS_UP","value":0,"timestampMs":482336,"isTriggerByUser":true},"deviceType":"bes2800","extendInfo":{"s`
- `2026-08-30 14:08:27.768 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"handle","contextInfo":{"type":77,"typeName":"INPUT_EVENT_MEDIA_PRESS_UP","value":0,"timestampMs":482336,"isTriggerByUser":true,"handler":"noCatcher"},"deviceType":"be`
- `2026-08-30 14:08:27.771 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"event","contextInfo":{"type":73,"typeName":"INPUT_EVENT_MEDIA_CLICK_DOUBLE","value":0,"timestampMs":482651,"isTriggerByUser":true},"deviceType":"bes2800","extendInfo"`
- `2026-08-30 14:08:27.800 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"handle","contextInfo":{"type":73,"typeName":"INPUT_EVENT_MEDIA_CLICK_DOUBLE","value":0,"timestampMs":482651,"isTriggerByUser":true,"handler":"AppTaskSchedulePerceptio`
- `2026-08-30 14:08:27.828 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"power-state","eventName":"record_end ","contextInfo":{"battery":43,"currentTask":"AudioRecording","endTime":1788098906786,"duration":7412},"deviceType":"bes2800","extendInfo":{"systemVer`
- `2026-08-30 14:08:27.830 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"SingleTaskChange","contextInfo":{"code":"AudioRecording","traceId":"0b5d39f417880700981117526d0fa3","status":"TryExit","reason":"CLOUD","reasonStop":"KEY","hint":""`
- `2026-08-30 14:08:27.860 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AudioRecording","eventName":"onHandler","contextInfo":{"msg":1,"talkSessionIdBefore":1121155,"recordDataSent":592,"recordDataStartTime":1788098899678,"recordDataSentDura":7125},"deviceTy`
- `2026-08-30 14:08:27.888 CST` CID 0x0041 C->H: `{"eventType":"AudioRecording","eventName":"onHandler","contextInfo":{"msg":1000},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":"2026-08-30 14:08:26.920","log_timestamp":"1788098906920"}}`
- `2026-08-30 14:08:27.889 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskInsideStage","contextInfo":{"name":"AudioRecording","stage":"Exiting"},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":"20`
- `2026-08-30 14:08:27.918 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskInsideStage","contextInfo":{"name":"AudioRecording","stage":"Destroying"},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":`
- `2026-08-30 14:08:27.921 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"SingleTaskChange","contextInfo":{"code":"AudioRecording","traceId":"0b5d39f417880700981117526d0fa3","status":"Exiting","reason":"CLOUD","reasonStop":"KEY","hint":""`
- `2026-08-30 14:08:27.948 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"SingleTaskChange","contextInfo":{"code":"AudioRecording","traceId":"0b5d39f417880700981117526d0fa3","status":"Exited","reason":"CLOUD","reasonStop":"KEY","hint":"",`
- `2026-08-30 14:08:29.419 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"event","contextInfo":{"type":514,"typeName":"INPUT_EVENT_SENSOR_SEDENTARY_DETECT","value":1,"timestampMs":1788098908047,"isTriggerByUser":false},"deviceType":"bes2800`
- `2026-08-30 14:08:29.449 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"handle","contextInfo":{"type":514,"typeName":"INPUT_EVENT_SENSOR_SEDENTARY_DETECT","value":1,"timestampMs":1788098908047,"isTriggerByUser":false,"handler":"noCatcher"`
- `2026-08-30 14:08:30.827 CST` CID 0x004a H->C: `{"code":"AudioRecording","data":{"reason":"touch"},"extensions":{"taskLinkId":"AudioRecording178807011090649A5292E5517459EB1CDC65C0BC0B013","bizType":"live"},"sessionId":"1788070110"}`
- `2026-08-30 14:08:30.828 CST` CID 0x004a H->C: `{"data":{"reason":"touch"},"scene":"AudioRecording","sessionId":"1788070110","taskLinkId":"AudioRecording178807011090649A5292E5517459EB1CDC65C0BC0B013","wakeupType":"longRecord"}`
- `2026-08-30 14:08:30.829 CST` CID 0x004a H->C: `{"data":{"reason":"touch"},"pageType":"SCHEME_AIRECORD_START","sessionId":"1788070110","uri":"airecord://start"}`
- `2026-08-30 14:08:31.067 CST` CID 0x0041 C->H: `{"eventContext":{"taskLayer":{"current":{"code":"AudioRecording"},"background":[]}}}`
- `2026-08-30 14:08:31.098 CST` CID 0x0041 C->H: `{"format":".ogg","sceneContexts":{"taskLinkId":"AudioRecording178807011090649A5292E5517459EB1CDC65C0BC0B013","scene":"AudioRecording"},"eventContext":{"taskLayer":{"current":{},"background":[]}}}`
- `2026-08-30 14:08:31.100 CST` CID 0x0041 C->H: `(分段JSON) {"code":"AudioRecording","traceId":"76b62a47ae4bb122f3691788098910","status":"Running","reason":"APP","reasonStop":null,"hint":"","context":"{\"taskLinkId\":\"AudioRecording178807011090649A5292E551745`
- `2026-08-30 14:08:31.129 CST` CID 0x0041 C->H: `(分段JSON) {"eventContext":{"taskLayer":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178807011090649A5292E5517459EB1CDC65C0BC0B013","scene":"AudioRecording","sessionId":1788070110},`
- `2026-08-30 14:08:31.159 CST` CID 0x0041 C->H: `{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178807011090649A5292E5517459EB1CDC65C0BC0B013","scene":"AudioRecording","sessionId":1788070110},"reason":"APP"},"background":[]}`
- `2026-08-30 14:08:31.190 CST` CID 0x0041 C->H: `(分段JSON) {"eventNs":"AliGenie.System","eventName":"SynchronizeStatus","payLoad":{"identifier":"deviceContext","value":{"taskLayer":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178`
- `2026-08-30 14:08:38.872 CST` CID 0x004a H->C: `{"type":"PART","codeList":["AudioRecording"]}`
- `2026-08-30 14:08:38.873 CST` CID 0x004a H->C: `{"code":"AudioRecording"}`
- `2026-08-30 14:08:38.987 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"76b62a47ae4bb122f3691788098910","status":"TryExit","reason":"APP","reasonStop":"APP","hint":"","context":"{}"}`
- `2026-08-30 14:08:39.619 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"76b62a47ae4bb122f3691788098910","status":"Exiting","reason":"APP","reasonStop":"APP","hint":"","context":"{}"}`
- `2026-08-30 14:08:39.678 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"76b62a47ae4bb122f3691788098910","status":"Exited","reason":"APP","reasonStop":"APP","hint":"","context":"{}"}`

### 5.3 组合 3（眼镜发起 + 手机结束）

录音窗口: 2026-08-30 14:08:43.897 CST ~ 2026-08-30 14:08:50.966 CST（7.07s，591 帧）
触发方式判定: 发起=眼镜，结束=手机

开始指令/事件:
- `2026-08-30 14:08:43.349 CST` CID 0x004a H->C: `{"code":"AudioRecording","extensions":{"taskLinkId":"AudioRecording17880701230330D2C496C79124819AC918A794A9CF9A8"},"sessionId":8391565,"traceId":"0b5d39f417880701223766172d0fa3"}`
- `2026-08-30 14:08:43.351 CST` CID 0x004a H->C: `{"scene":"AudioRecording","sessionId":8391565,"taskLinkId":"AudioRecording17880701230330D2C496C79124819AC918A794A9CF9A8","traceId":"0b5d39f417880701223766172d0fa3","wakeupType":"longRecord"}`
- `2026-08-30 14:08:43.353 CST` CID 0x004a H->C: `{"data":{"dialogId":"44354137344330345f313538343930313134353939363435383135335f7ffffe5faeb729c9"},"pageType":"SCHEME_AIRECORD_START","sessionId":8391565,"traceId":"0b5d39f417880701223766172d0fa3","uri":"airecord://start"}`
- `2026-08-30 14:08:43.668 CST` CID 0x0041 C->H: `{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording17880701230330D2C496C79124819AC918A794A9CF9A8","scene":"AudioRecording","sessionId":8391565},"reason":"CLOUD"},"background":[]}`
结束指令/事件:
- `2026-08-30 14:08:50.843 CST` CID 0x004a H->C: `{"type":"PART","codeList":["AudioRecording"]}`
- `2026-08-30 14:08:50.844 CST` CID 0x004a H->C: `{"code":"AudioRecording"}`
- `2026-08-30 14:08:50.958 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"0b5d39f417880701223766172d0fa3","status":"TryExit","reason":"CLOUD","reasonStop":"APP","hint":"","context":"{}"}`
- `2026-08-30 14:08:51.618 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"0b5d39f417880701223766172d0fa3","status":"Exiting","reason":"CLOUD","reasonStop":"APP","hint":"","context":"{}"}`
- `2026-08-30 14:08:51.649 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"0b5d39f417880701223766172d0fa3","status":"Exited","reason":"CLOUD","reasonStop":"APP","hint":"","context":"{}"}`
眼镜侧遥测事件（内部时间 CST，递达时间见括号）:
- 2026-08-30 14:08:43.590 CST（递达 2026-08-30 14:08:54.500 CST）power-state/record_start: `{"battery":43,"currentTask":"AudioRecording","startTime":1788098923590}`
- 2026-08-30 14:08:50.949 CST（递达 2026-08-30 14:09:01.760 CST）power-state/record_end: `{"battery":43,"currentTask":"AudioRecording","endTime":1788098930949,"duration":7359}`
- 2026-08-30 14:08:43.869 CST（递达 2026-08-30 14:09:01.766 CST）AudioRecording/onHandler: `{"msg":1,"talkSessionIdBefore":1121167,"recordDataSent":591,"recordDataStartTime":1788098923869,"recordDataSentDura":7102}`

窗口内关键消息:
- `2026-08-30 14:08:41.209 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"EnterAppTask","contextInfo":{"name":"AudioRecording","reason":"APP","extensions":"{\"taskLinkId\":\"AudioRecording178807011090649A5292E5517459EB1CDC65C0BC0B013\",\"`
- `2026-08-30 14:08:41.212 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskInsideStage","contextInfo":{"name":"AudioRecording","stage":"Creating"},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":"2`
- `2026-08-30 14:08:41.239 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AiTalkService","eventName":"exitTalk","contextInfo":{"reason":"AudioRecording","exitWhenActive":false},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_tim`
- `2026-08-30 14:08:41.328 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskInsideStage","contextInfo":{"name":"AudioRecording","stage":"Running"},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":"20`
- `2026-08-30 14:08:41.330 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"PendingGwCmdDone","contextInfo":{"sessionId":1788070110,"traceId":"","namespaceId":13,"transactionId":3,"taskName":"AudioRecording","taskToken":144,"taskStage":1,"t`
- `2026-08-30 14:08:41.358 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"PendingGwCmdDone","contextInfo":{"sessionId":1788070110,"traceId":"","namespaceId":13,"transactionId":1,"taskName":"AudioRecording","taskToken":144,"taskStage":1,"t`
- `2026-08-30 14:08:41.360 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"power-state","eventName":"record_start ","contextInfo":{"battery":43,"currentTask":"AudioRecording","startTime":1788098911097},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS`
- `2026-08-30 14:08:41.389 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"SingleTaskChange","contextInfo":{"code":"AudioRecording","traceId":"76b62a47ae4bb122f3691788098910","status":"Running","reason":"APP","reasonStop":null,"hint":"","c`
- `2026-08-30 14:08:41.393 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskChange","contextInfo":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178807011090649A5292E5517459EB1CDC65C0BC0B013","scene":"AudioRe`
- `2026-08-30 14:08:41.418 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"EnterAppTaskResp","contextInfo":{"name":"AudioRecording","token":144,"sessionId":1788070110,"traceId":"76b62a47ae4bb122f3691788098910","reason":"APP","success":true`
- `2026-08-30 14:08:41.456 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AudioRecording","eventName":"onHandler","contextInfo":{"msg":0,"talkSessionIdBefore":-1,"taskLinkId":"AudioRecording178807011090649A5292E5517459EB1CDC65C0BC0B013","talkSessionIdAfter":11`
- `2026-08-30 14:08:41.512 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"ExitAppTask","contextInfo":{"name":"AudioRecording","traceId":"5e14be0d02f851e471ba1788098918","reason":"APP","token":146},"deviceType":"bes2800","extendInfo":{"sys`
- `2026-08-30 14:08:41.539 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"ExitAppTask","contextInfo":{"name":"AudioRecording","traceId":"78480a44cb939420942e1788098918","reason":"APP","token":147},"deviceType":"bes2800","extendInfo":{"sys`
- `2026-08-30 14:08:41.542 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"power-state","eventName":"record_end ","contextInfo":{"battery":43,"currentTask":"AudioRecording","endTime":1788098918983,"duration":7886},"deviceType":"bes2800","extendInfo":{"systemVer`
- `2026-08-30 14:08:41.542 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"SingleTaskChange","contextInfo":{"code":"AudioRecording","traceId":"76b62a47ae4bb122f3691788098910","status":"TryExit","reason":"APP","reasonStop":"APP","hint":"","`
- `2026-08-30 14:08:41.571 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AudioRecording","eventName":"onHandler","contextInfo":{"msg":1,"talkSessionIdBefore":1121161,"recordDataSent":604,"recordDataStartTime":1788098911720,"recordDataSentDura":7283},"deviceTy`
- `2026-08-30 14:08:41.631 CST` CID 0x0041 C->H: `{"eventType":"AudioRecording","eventName":"onHandler","contextInfo":{"msg":1000},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":"2026-08-30 14:08:39.140","log_timestamp":"1788098919140"}}`
- `2026-08-30 14:08:41.658 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskInsideStage","contextInfo":{"name":"AudioRecording","stage":"Exiting"},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":"20`
- `2026-08-30 14:08:41.661 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskInsideStage","contextInfo":{"name":"AudioRecording","stage":"Destroying"},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":`
- `2026-08-30 14:08:41.719 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"SingleTaskChange","contextInfo":{"code":"AudioRecording","traceId":"76b62a47ae4bb122f3691788098910","status":"Exiting","reason":"APP","reasonStop":"APP","hint":"","`
- `2026-08-30 14:08:41.721 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"SingleTaskChange","contextInfo":{"code":"AudioRecording","traceId":"76b62a47ae4bb122f3691788098910","status":"Exited","reason":"APP","reasonStop":"APP","hint":"","c`
- `2026-08-30 14:08:41.729 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"ExitAppTaskResp","contextInfo":{"name":"AudioRecording","token":146,"sessionId":0,"traceId":"5e14be0d02f851e471ba1788098918","reason":"APP","success":true,"hint":""`
- `2026-08-30 14:08:41.731 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"ExitAppTaskResp","contextInfo":{"name":"AudioRecording","token":147,"sessionId":0,"traceId":"78480a44cb939420942e1788098918","reason":"APP","success":true,"hint":""`
- `2026-08-30 14:08:41.755 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"event","contextInfo":{"type":76,"typeName":"INPUT_EVENT_MEDIA_PRESS_DOWN","value":0,"timestampMs":497048,"isTriggerByUser":true},"deviceType":"bes2800","extendInfo":{`
- `2026-08-30 14:08:41.779 CST` CID 0x0041 C->H: `(分段JSON) {{"eventType":"input","eventName":"handle","contextInfo":{"type":76,"typeName":"INPUT_EVENT_MEDIA_PRESS_DOWN","value":0,"timestampMs":497048,"isTriggerByUser":true,"handler":"default"},"deviceType":`
- `2026-08-30 14:08:42.018 CST` CID 0x0041 C->H: `{"eventNs":"AliGenie.Text","eventName":"Recognize","payLoad":{"inputText":"打开会议录音","wakeupType":"press","pressContext":{"type":"threeFingerLongPress"}},"externFlag":false}`
- `2026-08-30 14:08:42.020 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"event","contextInfo":{"type":78,"typeName":"INPUT_EVENT_MEDIA_MULTI_FINGER_LONG","value":0,"timestampMs":497862,"isTriggerByUser":true},"deviceType":"bes2800","extend`
- `2026-08-30 14:08:42.109 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"handle","contextInfo":{"type":78,"typeName":"INPUT_EVENT_MEDIA_MULTI_FINGER_LONG","value":0,"timestampMs":497862,"isTriggerByUser":true,"handler":"TouchQuery"},"devic`
- `2026-08-30 14:08:42.589 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"event","contextInfo":{"type":77,"typeName":"INPUT_EVENT_MEDIA_PRESS_UP","value":0,"timestampMs":498500,"isTriggerByUser":true},"deviceType":"bes2800","extendInfo":{"s`
- `2026-08-30 14:08:42.591 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"input","eventName":"handle","contextInfo":{"type":77,"typeName":"INPUT_EVENT_MEDIA_PRESS_UP","value":0,"timestampMs":498500,"isTriggerByUser":true,"handler":"noCatcher"},"deviceType":"be`
- `2026-08-30 14:08:43.349 CST` CID 0x004a H->C: `{"code":"AudioRecording","extensions":{"taskLinkId":"AudioRecording17880701230330D2C496C79124819AC918A794A9CF9A8"},"sessionId":8391565,"traceId":"0b5d39f417880701223766172d0fa3"}`
- `2026-08-30 14:08:43.351 CST` CID 0x004a H->C: `{"scene":"AudioRecording","sessionId":8391565,"taskLinkId":"AudioRecording17880701230330D2C496C79124819AC918A794A9CF9A8","traceId":"0b5d39f417880701223766172d0fa3","wakeupType":"longRecord"}`
- `2026-08-30 14:08:43.353 CST` CID 0x004a H->C: `{"data":{"dialogId":"44354137344330345f313538343930313134353939363435383135335f7ffffe5faeb729c9"},"pageType":"SCHEME_AIRECORD_START","sessionId":8391565,"traceId":"0b5d39f417880701223766172d0fa3","uri":"airecord://start"}`
- `2026-08-30 14:08:43.609 CST` CID 0x0041 C->H: `(分段JSON) {"code":"AudioRecording","traceId":"0b5d39f417880701223766172d0fa3","status":"Running","reason":"CLOUD","reasonStop":null,"hint":"","context":"{\"taskLinkId\":\"AudioRecording17880701230330D2C496C7912`
- `2026-08-30 14:08:43.639 CST` CID 0x0041 C->H: `(分段JSON) {"eventContext":{"taskLayer":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording17880701230330D2C496C79124819AC918A794A9CF9A8","scene":"AudioRecording","sessionId":8391565},"re`
- `2026-08-30 14:08:43.668 CST` CID 0x0041 C->H: `{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording17880701230330D2C496C79124819AC918A794A9CF9A8","scene":"AudioRecording","sessionId":8391565},"reason":"CLOUD"},"background":[]}`
- `2026-08-30 14:08:43.699 CST` CID 0x0041 C->H: `(分段JSON) {"eventContext":{"taskLayer":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording17880701230330D2C496C79124819AC918A794A9CF9A8","scene":"AudioRecording","sessionId":8391565},"re`
- `2026-08-30 14:08:43.700 CST` CID 0x0041 C->H: `(分段JSON) {"eventNs":"AliGenie.System","eventName":"SynchronizeStatus","payLoad":{"identifier":"deviceContext","value":{"taskLayer":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178`
- `2026-08-30 14:08:43.702 CST` CID 0x0041 C->H: `(分段JSON) {"format":".ogg","sceneContexts":{"taskLinkId":"AudioRecording17880701230330D2C496C79124819AC918A794A9CF9A8","scene":"AudioRecording"},"eventContext":{"taskLayer":{"current":{"code":"AudioRecording","`
- `2026-08-30 14:08:50.843 CST` CID 0x004a H->C: `{"type":"PART","codeList":["AudioRecording"]}`

### 5.4 组合 4（手机发起 + 眼镜结束）

录音窗口: 2026-08-30 14:08:54.568 CST ~ 2026-08-30 14:09:00.985 CST（6.42s，531 帧）
触发方式判定: 发起=手机，结束=眼镜

开始指令/事件:
- `2026-08-30 14:08:53.564 CST` CID 0x004a H->C: `{"code":"AudioRecording","data":{"reason":"touch"},"extensions":{"taskLinkId":"AudioRecording178807013364982EEBAB35066465E8DB1D53DE8981695","bizType":"live"},"sessionId":"1788070133"}`
- `2026-08-30 14:08:53.566 CST` CID 0x004a H->C: `{"data":{"reason":"touch"},"scene":"AudioRecording","sessionId":"1788070133","taskLinkId":"AudioRecording178807013364982EEBAB35066465E8DB1D53DE8981695","wakeupType":"longRecord"}`
- `2026-08-30 14:08:53.566 CST` CID 0x004a H->C: `{"data":{"reason":"touch"},"pageType":"SCHEME_AIRECORD_START","sessionId":"1788070133","uri":"airecord://start"}`
- `2026-08-30 14:08:53.929 CST` CID 0x0041 C->H: `{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178807013364982EEBAB35066465E8DB1D53DE8981695","scene":"AudioRecording","sessionId":1788070133},"reason":"APP"},"background":[]}`
结束指令/事件:
- `2026-08-30 14:09:00.948 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"f3031525386b3d143da81788098933","status":"TryExit","reason":"APP","reasonStop":"KEY","hint":"","context":"{}"}`
- `2026-08-30 14:09:01.609 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"f3031525386b3d143da81788098933","status":"Exiting","reason":"APP","reasonStop":"KEY","hint":"","context":"{}"}`
- `2026-08-30 14:09:01.638 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"f3031525386b3d143da81788098933","status":"Exited","reason":"APP","reasonStop":"KEY","hint":"","context":"{}"}`
眼镜侧遥测事件（内部时间 CST，递达时间见括号）:
- 2026-08-30 14:08:53.827 CST（递达 2026-08-30 14:09:01.878 CST）power-state/record_start: `{"battery":43,"currentTask":"AudioRecording","startTime":1788098933827}`
- 2026-08-30 14:09:00.938 CST（递达 2026-08-30 14:09:02.181 CST）power-state/record_end: `{"battery":43,"currentTask":"AudioRecording","endTime":1788098940938,"duration":7111}`
- 2026-08-30 14:08:54.586 CST（递达 2026-08-30 14:09:02.241 CST）AudioRecording/onHandler: `{"msg":1,"talkSessionIdBefore":1121171,"recordDataSent":531,"recordDataStartTime":1788098934586,"recordDataSentDura":6381}`

窗口内关键消息:
- `2026-08-30 14:08:50.843 CST` CID 0x004a H->C: `{"type":"PART","codeList":["AudioRecording"]}`
- `2026-08-30 14:08:50.844 CST` CID 0x004a H->C: `{"code":"AudioRecording"}`
- `2026-08-30 14:08:50.958 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"0b5d39f417880701223766172d0fa3","status":"TryExit","reason":"CLOUD","reasonStop":"APP","hint":"","context":"{}"}`
- `2026-08-30 14:08:51.618 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"0b5d39f417880701223766172d0fa3","status":"Exiting","reason":"CLOUD","reasonStop":"APP","hint":"","context":"{}"}`
- `2026-08-30 14:08:51.649 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"0b5d39f417880701223766172d0fa3","status":"Exited","reason":"CLOUD","reasonStop":"APP","hint":"","context":"{}"}`
- `2026-08-30 14:08:53.564 CST` CID 0x004a H->C: `{"code":"AudioRecording","data":{"reason":"touch"},"extensions":{"taskLinkId":"AudioRecording178807013364982EEBAB35066465E8DB1D53DE8981695","bizType":"live"},"sessionId":"1788070133"}`
- `2026-08-30 14:08:53.566 CST` CID 0x004a H->C: `{"data":{"reason":"touch"},"scene":"AudioRecording","sessionId":"1788070133","taskLinkId":"AudioRecording178807013364982EEBAB35066465E8DB1D53DE8981695","wakeupType":"longRecord"}`
- `2026-08-30 14:08:53.566 CST` CID 0x004a H->C: `{"data":{"reason":"touch"},"pageType":"SCHEME_AIRECORD_START","sessionId":"1788070133","uri":"airecord://start"}`
- `2026-08-30 14:08:53.840 CST` CID 0x0041 C->H: `(分段JSON) {"code":"AudioRecording","traceId":"f3031525386b3d143da81788098933","status":"Running","reason":"APP","reasonStop":null,"hint":"","context":"{\"taskLinkId\":\"AudioRecording178807013364982EEBAB3506646`
- `2026-08-30 14:08:53.899 CST` CID 0x0041 C->H: `(分段JSON) {"eventContext":{"taskLayer":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178807013364982EEBAB35066465E8DB1D53DE8981695","scene":"AudioRecording","sessionId":1788070133},`
- `2026-08-30 14:08:53.929 CST` CID 0x0041 C->H: `{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178807013364982EEBAB35066465E8DB1D53DE8981695","scene":"AudioRecording","sessionId":1788070133},"reason":"APP"},"background":[]}`
- `2026-08-30 14:08:53.959 CST` CID 0x0041 C->H: `(分段JSON) {"eventContext":{"taskLayer":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178807013364982EEBAB35066465E8DB1D53DE8981695","scene":"AudioRecording","sessionId":1788070133},`
- `2026-08-30 14:08:53.961 CST` CID 0x0041 C->H: `(分段JSON) {"eventNs":"AliGenie.System","eventName":"SynchronizeStatus","payLoad":{"identifier":"deviceContext","value":{"taskLayer":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording178`
- `2026-08-30 14:08:53.963 CST` CID 0x0041 C->H: `(分段JSON) {"format":".ogg","sceneContexts":{"taskLinkId":"AudioRecording178807013364982EEBAB35066465E8DB1D53DE8981695","scene":"AudioRecording"},"eventContext":{"taskLayer":{"current":{"code":"AudioRecording","`
- `2026-08-30 14:08:54.110 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"EnterAppTask","contextInfo":{"name":"AudioRecording","reason":"CLOUD","extensions":"{\"taskLinkId\":\"AudioRecording17880701230330D2C496C79124819AC918A794A9CF9A8\"}`
- `2026-08-30 14:08:54.139 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskInsideStage","contextInfo":{"name":"AudioRecording","stage":"Creating"},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":"2`
- `2026-08-30 14:08:54.199 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AiTalkService","eventName":"exitTalk","contextInfo":{"reason":"AudioRecording","exitWhenActive":false},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_tim`
- `2026-08-30 14:08:54.380 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskInsideStage","contextInfo":{"name":"AudioRecording","stage":"Running"},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":"20`
- `2026-08-30 14:08:54.409 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"PendingGwCmdDone","contextInfo":{"sessionId":8391565,"traceId":"0b5d39f417880701223766172d0fa3","namespaceId":13,"transactionId":3,"taskName":"AudioRecording","task`
- `2026-08-30 14:08:54.439 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"PendingGwCmdDone","contextInfo":{"sessionId":8391565,"traceId":"0b5d39f417880701223766172d0fa3","namespaceId":13,"transactionId":1,"taskName":"AudioRecording","task`
- `2026-08-30 14:08:54.500 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"power-state","eventName":"record_start ","contextInfo":{"battery":43,"currentTask":"AudioRecording","startTime":1788098923590},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS`
- `2026-08-30 14:08:54.529 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"SingleTaskChange","contextInfo":{"code":"AudioRecording","traceId":"0b5d39f417880701223766172d0fa3","status":"Running","reason":"CLOUD","reasonStop":null,"hint":"",`
- `2026-08-30 14:08:54.559 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskChange","contextInfo":{"current":{"code":"AudioRecording","context":{"taskLinkId":"AudioRecording17880701230330D2C496C79124819AC918A794A9CF9A8","scene":"AudioRe`
- `2026-08-30 14:08:54.618 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"EnterAppTaskResp","contextInfo":{"name":"AudioRecording","token":148,"sessionId":8391565,"traceId":"0b5d39f417880701223766172d0fa3","reason":"CLOUD","success":true,`
- `2026-08-30 14:08:54.649 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AudioRecording","eventName":"onHandler","contextInfo":{"msg":0,"talkSessionIdBefore":-1,"taskLinkId":"AudioRecording17880701230330D2C496C79124819AC918A794A9CF9A8","talkSessionIdAfter":11`
- `2026-08-30 14:09:00.948 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"f3031525386b3d143da81788098933","status":"TryExit","reason":"APP","reasonStop":"KEY","hint":"","context":"{}"}`
- `2026-08-30 14:09:01.609 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"f3031525386b3d143da81788098933","status":"Exiting","reason":"APP","reasonStop":"KEY","hint":"","context":"{}"}`
- `2026-08-30 14:09:01.638 CST` CID 0x0041 C->H: `{"code":"AudioRecording","traceId":"f3031525386b3d143da81788098933","status":"Exited","reason":"APP","reasonStop":"KEY","hint":"","context":"{}"}`
- `2026-08-30 14:09:01.751 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"ExitAppTask","contextInfo":{"name":"AudioRecording","traceId":"2e3717b1382696959eff1788098930","reason":"APP","token":150},"deviceType":"bes2800","extendInfo":{"sys`
- `2026-08-30 14:09:01.758 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"ExitAppTask","contextInfo":{"name":"AudioRecording","traceId":"4a32bc3524b7780099ea1788098930","reason":"APP","token":151},"deviceType":"bes2800","extendInfo":{"sys`
- `2026-08-30 14:09:01.760 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"power-state","eventName":"record_end ","contextInfo":{"battery":43,"currentTask":"AudioRecording","endTime":1788098930949,"duration":7359},"deviceType":"bes2800","extendInfo":{"systemVer`
- `2026-08-30 14:09:01.762 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"SingleTaskChange","contextInfo":{"code":"AudioRecording","traceId":"0b5d39f417880701223766172d0fa3","status":"TryExit","reason":"CLOUD","reasonStop":"APP","hint":""`
- `2026-08-30 14:09:01.766 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AudioRecording","eventName":"onHandler","contextInfo":{"msg":1,"talkSessionIdBefore":1121167,"recordDataSent":591,"recordDataStartTime":1788098923869,"recordDataSentDura":7102},"deviceTy`
- `2026-08-30 14:09:01.768 CST` CID 0x0041 C->H: `{"eventType":"AudioRecording","eventName":"onHandler","contextInfo":{"msg":1000},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":"2026-08-30 14:08:51.110","log_timestamp":"1788098931110"}}`
- `2026-08-30 14:09:01.770 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskInsideStage","contextInfo":{"name":"AudioRecording","stage":"Exiting"},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":"20`
- `2026-08-30 14:09:01.772 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"TaskInsideStage","contextInfo":{"name":"AudioRecording","stage":"Destroying"},"deviceType":"bes2800","extendInfo":{"systemVer":"1.10.0-RS-20260826.0248","log_time":`
- `2026-08-30 14:09:01.776 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"SingleTaskChange","contextInfo":{"code":"AudioRecording","traceId":"0b5d39f417880701223766172d0fa3","status":"Exiting","reason":"CLOUD","reasonStop":"APP","hint":""`
- `2026-08-30 14:09:01.777 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"SingleTaskChange","contextInfo":{"code":"AudioRecording","traceId":"0b5d39f417880701223766172d0fa3","status":"Exited","reason":"CLOUD","reasonStop":"APP","hint":"",`
- `2026-08-30 14:09:01.790 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"ExitAppTaskResp","contextInfo":{"name":"AudioRecording","token":150,"sessionId":0,"traceId":"2e3717b1382696959eff1788098930","reason":"APP","success":true,"hint":""`
- `2026-08-30 14:09:01.793 CST` CID 0x0041 C->H: `(分段JSON) {"eventType":"AppTask","eventName":"ExitAppTaskResp","contextInfo":{"name":"AudioRecording","token":151,"sessionId":0,"traceId":"4a32bc3524b7780099ea1788098930","reason":"APP","success":true,"hint":""`

## 6. 异常与备注

- CID 0x0048 无魔数 payload 数: 1（本日志音频不在 0x0048；该 CID 上仅少量杂项包）
- L2CAP 头截断的 ACL 起始包 37 个（不影响音频帧，音频帧均为完整 398B PDU）
- 无对应起始包的孤立续包 32 个

> 生成脚本: `extract_combo_audio.js`（Node.js 内置模块，无外部依赖）
> 指令明细: `combo_test_instructions.json`