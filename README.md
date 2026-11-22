# 小鲸鱼 AR

独立 Android 测试应用，包名 `dev.littlewhale.ar`。0.2.0 为 Redmi K80 Ultra 的桌面角色测试版，支持 arm64、Android 10 及以上。首次启动需要相机权限。

## 小鲸鱼 V21

在「选择角色」里点「作者原址下载」，或选「本机导入」并选择[作者发布的完整 V21 VRM](https://github.com/CF-612/SmallBlueFish-Model/releases/tag/dot-test-20261104-v21-fullmodel)。首次约 333 MiB，SHA-256 校验后保存在应用私有目录，再次打开可离线使用。选择「对照方块」可在同一锚点比较。

对准有纹理的桌面并平移，提示就绪后点「放置小鲸鱼」。绕到背面，点尾鳍的可见部分，看头、颈和身体转向镜头。首次调试请关注脚底是否贴桌、移开镜头后的恢复位置和定位 FPS。渲染和定位分开运行，定位 FPS 不能代表角色实际渲染帧率。

本版使用 WebView WebGL2、Three.js 和模型中立站姿的 glTF 蒙皮，不含 Unity 或完整 UniVRM 表情、布料与毛发物理。角色保留 V21 原始骨骼，移动版按需读文件片段、跳过表情增量和完全透明的表情网格，材质使用原底色和贴图，最长边限制为 1024 像素。

模型来源 CF-612 / SmallBlueFish-Model，形象参考「这个刀子真甜」，原形象 @ZipZipPipe、@上善无形。作者的 V21 文件要求署名，仅个人非商用试验，禁止再分发。APK 内只有渲染程序，模型从作者源首次取得。

## 方块真机试法

1. 找一张光线均匀的桌子，放一本封面有图案的书。让桌面占据大部分画面。
2. 手持手机缓慢左右**平移**，直到出现「跟踪就绪」。点击「放置方块」。引擎从可见特征点拟合平面，在该平面上放置方块。
3. 记住方块底边相对于书角的位置。向侧面、背面绕行，再回到起点。记录底边是否滑走、是否看到不同颜色的面，以及底栏的定位 FPS。FPS 来自已完成的定位结果回调。
4. 短暂遮住镜头，再对准原来的桌面，观察方块能否回到原处。点击「重新扫描」可换一张桌子。从后台回来后重新扫描放置。

单目地图没有绝对厘米刻度。初次放置时根据相机距离确定方块大小，之后保持该地图尺度。纯色、反光或暗光桌面容易缺少定位特征。

## 从源码构建

使用 JDK 17、Node.js 22 和 Android SDK。执行：

```sh
bash scripts/prepare.sh
(cd viewer && npm ci && npm run build)
bash upstream/gradlew :app:assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。准备脚本按提交号取依赖和 OpenCV 子模块。相机采集、定位服务和桌面拟合复用 [MenthaAR](https://github.com/Olsc/Android_ORB-SLAM2s/tree/913d2c13ec300e9aaa148a45de2b14a5de805f63)。测试应用从 SDK 读取同帧的模型、视图、投影矩阵，并按相机预览的旋转和尺寸绘制方块。首轮无需安装 ARCore 或 Unity。

项目记录与角色交互的后续入口见 [PROJECT_LEDGER.md](PROJECT_LEDGER.md)。引擎、相机和 SDK 原始许可见[上游许可索引](https://github.com/Olsc/Android_ORB-SLAM2s/blob/913d2c13ec300e9aaa148a45de2b14a5de805f63/LICENSE)。
