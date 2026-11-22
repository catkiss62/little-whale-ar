# YuChat 小鲸鱼动作盘点

依据 CF-612/YuChat 的源码快照 `c3e1896c` 和作者单独发布的 Quest 测试包 `v0.5.1-quest-test`。两份材料的日期和文件不同。清单按动作文件计数，同一片段在对话和玩具中复用只计一次。[源码](https://github.com/CF-612/YuChat/tree/c3e1896c11d6d1fa19bc2ec762d7bd0ce490ae5b) · [Quest 原包](https://github.com/CF-612/YuChat/releases/tag/v0.5.1-quest-test) · [逐条文件清单](yuchat-motion-files.tsv)

## 提起用哪段

| 玩家操作 | 原作怎么演 | 手机端接法 |
| --- | --- | --- |
| 普通提起 | `grabbed.vrma`，4.60 秒。保留全身关节原片，持有时循环，轻扶正身体并压低过仰的头。普通片在发布 APK 的 `assets/Motions/local-grab/` | 优先给现有「长按身体」使用。抓点、跟手平移、绿圈与同图落点继续由 AR 控制。悬空期间让原片控制手臂、腿和身姿，避免旧弯腿或看镜头逻辑覆盖原片 |
| 尾鳍提起 | `jitabata.vrma`，11.77 秒。完整踢腿扑腾，抓尾模式额外将躯干调整至头朝下并使用尾部抓点。尾骨运动另由模型驱动 | 若增加长按尾鳍，沿用该片及倒挂约束；保留轻点尾巴回头。尾端要在扑腾中贴近同一抓点 |
| 小鲸鱼捡道具 | `kay_pickup`，1.30 秒。小鲸鱼自己弯腰伸手；抱币另接抱物姿势和两手接触校正 | 供后续拾物玩法使用 |

原作抓住时从当下姿势约 0.25 秒混入，播完整段，循环交界约 0.20 秒，松手约 0.30 秒退出。动画不驱动整个角色在世界中的位置。普通提起与尾抓共用播放层，但有不同的持有片段、身体方向修正和握点。[抓取播放器](https://github.com/CF-612/YuChat/blob/c3e1896c11d6d1fa19bc2ec762d7bd0ce490ae5b/LingChat-XR/Assets/LingChatXR/Runtime/Chat/XrMotionGrabbed.cs) · [抓尾接入记录](https://github.com/CF-612/YuChat/blob/c3e1896c11d6d1fa19bc2ec762d7bd0ce490ae5b/docs/JITABATA-AUDITION-2026-11-04.md)

## 41 个可定位的人形动画文件

| 分组 | 数量 | 逐项内容 |
| --- | ---: | --- |
| 空间抓取 | 2 | 普通提起、抓尾倒挂扑腾 |
| 移动库 | 7 | 移动用待机、向前走、左转、右转、另一套走路循环、蹦跳步、跑步。当前源码的第二版移动目录默认用蹦跳步；额外走路循环随包提供，未列入该目录运行时加载的六项 |
| 待机与表达 | 23 | 中立待机、灵机一动握拳击掌、双手背后、鞠躬、摊手、合十致谢、左指、右指、OK 手势、提裙下蹲致谢、举手提问、疑问歪头、拍胸保证、弯腰伸手邀请、竖拇指、嘘、开心加油、思考倾听、指自己、右手挥手、摊手叹气、抚胸深呼吸、停止手势 |
| 后续挑选的动作 | 9 | 轻松待机、警觉待机、抱臂待机、小跳并落地、大跳并落地、弯腰拾取、蹲伏、踮脚潜行、双手搬重物走 |

逐项表记录 ID、中文名称、实际轨道时长、轨道数、文件路径、SHA-256 和来源标记。31 段从官方 Quest 包提取并与包内目录校验，1 段从源码取出并与 tail.json 校验，其余 9 段按 YuChat 的选片与转换代码从作者来源重新生成。这 9 段记录本次生成的指纹；未取得 VR 作者本机转换后的副本做逐字节对照。[移动播放器](https://github.com/CF-612/YuChat/blob/c3e1896c11d6d1fa19bc2ec762d7bd0ce490ae5b/LingChat-XR/Assets/LingChatXR/Runtime/Chat/XrMotionLocomotion.cs) · [九段选片](https://github.com/CF-612/YuChat/blob/c3e1896c11d6d1fa19bc2ec762d7bd0ce490ae5b/tools/motion-import/prepare-selected.py)

## 复合互动与程序动作

| 原作行为 | 组成和复用方式 |
| --- | --- |
| 摸头、按压、弹额头 | 累积接触时间和滑动距离。抚摸叠加眼睑与头颈摆动，更深按压可挤眼，弹额头有短促后仰再恢复。适合映射到触屏的点击、持续滑动；压力相关效果可在支持时再接 |
| 安静站立、注视 | 呼吸、眨眼、看人、看靠近的手、注视玩具。完整手势或提起占用头部时需避让。可保留 AR 已通过的持续抬头和延迟转身 |
| 持有、摇晃、放下 | 持有可触发疑惑反应；摇晃有独立惊讶反馈。下落与站稳有独立状态，配合手臂、胸部和落地回稳。原作另有开心、疑惑、惊讶、不满短声。音效、表情和骨骼片段分层 |
| 指向走、手掌挡路 | 朝目标转向、开始移动、停下、面向玩家；Quest 可指向有效桌面并停留确认，前方手掌阻挡可令角色停步。AR 可按需映射到点桌面及既有活动范围 |
| 硬币 | 看见、追逐、转向、靠近停稳、俯身伸手、双手抓住币缘、抱到胸前、被拿走后解除。含追到活动区边缘、遮住后重新发现、抓起角色打断。拾取和抱物原片配合双手对齐及物体状态 |
| 对话与面部 | 内置点头、摇头；说话和思考有程序姿态及节奏，完整手势由动作目录选择，持续姿势由专门的循环区间控制。面部包含口型、眨眼、情绪与表情反馈；可用项取决于加载的模型 |
| PC 实验 | T2M 生成演出、视频转身体动作、ARDY/Horizon8 根据目标生成后续步态。它们生成结果或依赖外部运行环境，不另算固定片段 |

[表情与身体](https://github.com/CF-612/YuChat/blob/c3e1896c11d6d1fa19bc2ec762d7bd0ce490ae5b/LingChat-XR/Assets/LingChatXR/Runtime/FeiyuAvatar.cs) · [空间状态](https://github.com/CF-612/YuChat/blob/c3e1896c11d6d1fa19bc2ec762d7bd0ce490ae5b/LingChat-XR/Assets/LingChatXR/Core/PetInteraction.cs) · [指向走与挡路](https://github.com/CF-612/YuChat/blob/c3e1896c11d6d1fa19bc2ec762d7bd0ce490ae5b/LingChat-XR/Assets/LingChatXR/XR/QuestWalkGestures.cs) · [PC 抱币验证](https://github.com/CF-612/YuChat/blob/c3e1896c11d6d1fa19bc2ec762d7bd0ce490ae5b/docs/COIN-HUG-REWORK-RESULT-2026-11-09.md)

## 原片位置和适配证据

- 在原作者发布的 Quest APK 内，按 ZIP 打开 `assets/Motions/` 即可取得普通提起、23 个表现动作、7 个移动动作及三个目录 JSON。原包 SHA-256 为 `dc79c7775471d0e8f8092f2ae37f4dfb312deb95798804c5ef1855939f73fb39`。原作目录将 23 个表现动作标作本地使用待确认，移动目录包含受限和购入素材，普通提起目录没有填写许可。个人使用时可以从原包本地读取，公开仓库保留文件指纹与出处。[原作者发布包](https://github.com/CF-612/YuChat/releases/tag/v0.5.1-quest-test)
- 抓尾原片和来源说明在源码的 local-grab 目录。九个额外候选来自 KayKit Character Animations 1.1 和 Universal Animation Library 2 Standard，已用两个作者下载页取得的原包完成转换。两份下载 ZIP 指纹依次为 `65882f31f905ad2e953819648a59287cdeab8f623908d5ef701971d3758be20f`、`4008ea208a604773a2b2177d965f0f5d3195498b5bf838c3f5785d68e95f2a68`。[抓尾文件及 CC0 说明](https://github.com/CF-612/YuChat/tree/c3e1896c11d6d1fa19bc2ec762d7bd0ce490ae5b/LingChat-XR/Assets/StreamingAssets/Motions/local-grab) · [KayKit](https://kaylousberg.itch.io/kaykit-character-animations) · [Quaternius](https://quaternius.com/packs/universalanimationlibrary2.html)
- 已在原始 VRMA 和 V21 上核过骨骼映射。普通提起含 V21 未声明的 upperChest 动画，倒挂片含 V21 未声明的 jaw 动画。实际接入宜使用标准 Humanoid 重定向，检查肩胸、手指和双脚；应在完整 4.60 秒或 11.77 秒循环中检查抓点，而非只检查触屏事件。当前 Android 0.7.1 仍播放程序弯腿，以上原片未在该 APK 中播放。

离线归集时，把作者的 Quest APK 和本参考包放在本机。解压参考包，在解压目录运行以下命令，可补齐 31 段并复核指纹。解压后的 `motions/` 原有 10 段，补齐后共 41 段。

```sh
python3 scripts/collect-yuchat-motions.py Desktop-SmallBlueFish-Quest.apk motions --inventory yuchat-motion-files.tsv
```
