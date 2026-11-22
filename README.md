# 小鲸鱼 AR

独立 Android 测试应用，包名 `dev.littlewhale.ar`。0.1.0 为 Redmi K80 Ultra 的桌面定位测试版，支持 arm64、Android 10 及以上。首次启动需要相机权限。

## 真机试法

1. 找一张光线均匀的桌子，放一本封面有图案的书。让桌面占据大部分画面。
2. 手持手机缓慢左右**平移**，直到出现「跟踪就绪」。点击「放置方块」。引擎从可见特征点拟合平面，在该平面上放置方块。
3. 记住方块底边相对于书角的位置。向侧面、背面绕行，再回到起点。记录底边是否滑走、是否看到不同颜色的面，以及底栏的定位 FPS。FPS 来自已完成的定位结果回调。
4. 短暂遮住镜头，再对准原来的桌面，观察方块能否回到原处。点击「重新扫描」可换一张桌子。从后台回来后重新扫描放置。

单目地图没有绝对厘米刻度。初次放置时根据相机距离确定方块大小，之后保持该地图尺度。纯色、反光或暗光桌面容易缺少定位特征。

## 从源码构建

使用 JDK 17 和 Android SDK。执行：

```sh
bash scripts/prepare.sh
bash upstream/gradlew :app:assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。准备脚本按提交号取依赖和 OpenCV 子模块。相机采集、定位服务和桌面拟合复用 [MenthaAR](https://github.com/Olsc/Android_ORB-SLAM2s/tree/913d2c13ec300e9aaa148a45de2b14a5de805f63)。测试应用从 SDK 读取同帧的模型、视图、投影矩阵，并按相机预览的旋转和尺寸绘制方块。首轮无需安装 ARCore 或 Unity。

项目记录与角色交互的后续入口见 [PROJECT_LEDGER.md](PROJECT_LEDGER.md)。引擎、相机和 SDK 原始许可见[上游许可索引](https://github.com/Olsc/Android_ORB-SLAM2s/blob/913d2c13ec300e9aaa148a45de2b14a5de805f63/LICENSE)。
