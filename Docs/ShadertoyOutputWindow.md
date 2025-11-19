# Shadertoy IntelliJ 插件 - WebGL渲染集成实现文档

> 本文档描述了在IntelliJ IDEA插件中实现WebGL渲染预览窗口的完整技术方案
>
> 创建日期: 2025-11-17
> 
> 参考项目: [shader-toy VSCode插件](https://marketplace.visualstudio.com/items?itemName=stevensona.shader-toy)

---

## 目录

- [一、需求分析](#一需求分析)
- [二、JetBrains平台可用技术](#二jetbrains平台可用技术)
- [三、架构设计方案](#三架构设计方案)
- [四、详细实现方案](#四详细实现方案)
- [五、实施步骤](#五实施步骤)
- [六、潜在挑战和解决方案](#六潜在挑战和解决方案)
- [七、参考资料](#七参考资料)

---

## 一、需求分析

### 1.1 核心功能需求

参考shader-toy VSCode插件，需要实现：

#### 基础渲染功能

- ✅ 在`ShadertoyOutput`工具窗口中显示WebGL渲染的shader效果
- ✅ 支持GLSL shader实时编译和渲染
- ✅ 支持Three.js渲染管线
- ✅ 支持多pass渲染（buffer链）

#### 交互功能

- ✅ 鼠标交互（位置、点击状态）
- ✅ 键盘输入
- ✅ 暂停/播放
- ✅ 时间控制
- ✅ 截图功能
- ✅ 视频录制（可选）

#### 实时更新

- ✅ 文件保存时自动刷新
- ✅ 实时编辑预览（可配置延迟）
- ✅ 错误提示和编译反馈

#### 纹理和资源

- ✅ 加载外部纹理（图片、cubemap）
- ✅ 音频输入支持
- ✅ 自定义uniform参数

### 1.2 技术需求

- 嵌入浏览器组件用于WebGL渲染
- Java与JavaScript双向通信机制
- 资源文件管理（HTML、JS库、shader代码）
- 配置管理系统

---

## 二、渲染技术栈

### 2.1 多渲染后端架构 ⭐当前实现

插件采用**渲染后端抽象层**设计,支持多种渲染技术,用户可在设置中切换:

```kotlin
interface RenderBackend : Disposable {
    fun getRootComponent(): JComponent
    fun loadShader(fragmentShaderSource: String)
    fun updateRefCanvasResolution(width: Int, height: Int)
    fun updateOuterResolution(width: Int, height: Int)
}
```

### 2.2 JOGL Backend (推荐) ⭐

#### 特点
- **原生OpenGL渲染** - 使用JOGL (Java OpenGL) 
- **高性能** - 支持120fps+无限制帧率
- **无线程限制** - GLCanvas完美集成AWT/Swing
- **跨平台** - macOS/Windows/Linux全支持
- **稳定性最佳** - 无需GLFW主线程限制

#### 技术架构
- `GLCanvas` - AWT原生OpenGL画布
- `GLEventListener` - OpenGL事件回调
- `FPSAnimator` - 高性能渲染循环驱动器
- `GL3` - OpenGL 3.3+ Core Profile

#### 依赖配置
```kotlin
// build.gradle.kts
implementation("org.jogamp.gluegen:gluegen-rt:2.4.0")
implementation("org.jogamp.jogl:jogl-all:2.4.0")
```

### 2.3 LWJGL Backend (实验性)

#### 特点
- **原生OpenGL渲染** - 使用LWJGL3
- **高性能** - 支持120fps+
- **Offscreen渲染** - 使用FBO,输出到BufferedImage
- **线程限制** - macOS要求GLFW在主线程初始化

#### 技术架构
- `GLContext` - 管理OpenGL上下文和FBO
- `GLFW` - 窗口管理(创建隐藏窗口)
- `RenderLoop` - 独立线程渲染循环
- `ShaderCompiler` - Shader编译器
- `ShadertoyUniforms` - Uniform管理

#### macOS问题
- GLFW必须在主线程(EDT)初始化
- 限制了架构灵活性
- 推荐使用JOGL代替

### 2.4 JCEF Backend (兼容方案)

#### 特点
- **WebGL渲染** - 基于Chromium浏览器
- **稳定性高** - 成熟的Web技术栈
- **帧率限制** - 约30fps (浏览器VSync限制)
- **兼容性好** - IntelliJ 2020.1+

#### 技术架构
- `JBCefBrowser` - Chromium浏览器实例
- `WebGL 2.0` - JavaScript端渲染
- `executeJavaScript` - Java→JS通信

#### 依赖配置
```xml
<!-- plugin.xml -->
<depends>com.intellij.modules.platform</depends>
```

### 2.5 其他相关API

#### 虚拟文件系统 (VFS)

- 监听文件变化：`VirtualFileListener`
- 读取文件内容：`VirtualFile.contentsToByteArray()`

#### 编辑器API

- 获取当前编辑器：`FileEditorManager`
- 监听文档变化：`DocumentListener`

#### 消息系统

- 通知用户：`Notifications`
- 错误提示：`Messages`

#### 资源管理

- 读取插件资源：`javaClass.getResource()`
- 临时文件：`FileUtil.createTempFile()`

---

## 三、架构设计方案

### 3.1 整体架构（当前实现）

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         IntelliJ Platform                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  ┌──────────────────────┐         ┌───────────────────────────────┐    │
│  │  ShadertoyWindow     │         │  ShadertoyOutputWindow         │    │
│  │  (主窗口)            │         │  (渲染窗口工厂)                │    │
│  │  - Shuffle按钮       │         │                                │    │
│  │  - Compile按钮  ━━━━━━━━━━━━━━▶│  根据配置选择Backend:         │    │
│  └──────────┬───────────┘         │  - JOGL (推荐)                 │    │
│             │                      │  - LWJGL (实验)                │    │
│             │                      │  - JCEF (兼容)                 │    │
│             ▼                      └────────────┬──────────────────┘    │
│  ┌──────────────────────┐                      │                        │
│  │ ShaderCompileService │                      ▼                        │
│  │ - readImageGlslFile()│         ┌────────────────────────────────┐   │
│  │ - wrapShaderCode()   │         │    RenderBackend (接口)        │   │
│  └──────────┬───────────┘         │    ┌──────────────────────┐   │   │
│             │                      │    │ loadShader()         │   │   │
│             ▼                      │    │ updateResolution()   │   │   │
│  ┌──────────────────────────────┐ │    │ getRootComponent()   │   │   │
│  │    VirtualFileSystem          │ │    └──────────────────────┘   │   │
│  │    shaderTemplate/Image.glsl  │ │                                │   │
│  └──────────────────────────────┘ └────────────┬───────────────────┘   │
│             │                                   │                        │
│             └───────────────────────────────────┘                        │
│                    传递完整的Fragment Shader                             │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘
                                    │
                ┌───────────────────┴───────────────────┐
                │                   │                   │
                ▼                   ▼                   ▼
    ┌────────────────────┐ ┌──────────────────┐ ┌─────────────────┐
    │  JoglBackend       │ │  LwjglBackend    │ │  JCefBackend    │
    ├────────────────────┤ ├──────────────────┤ ├─────────────────┤
    │ • GLCanvas         │ │ • GLContext      │ │ • JBCefBrowser  │
    │ • GLEventListener  │ │ • GLFW + FBO     │ │ • WebGL 2.0     │
    │ • FPSAnimator      │ │ • RenderLoop     │ │ • JS通信        │
    │ • OpenGL 3.3+      │ │ • BufferedImage  │ │ • HTML渲染器    │
    │                    │ │ • 线程渲染       │ │                 │
    │ ✅ 120fps+         │ │ ⚠️ macOS限制    │ │ ⚠️ 30fps限制   │
    │ ✅ 无线程限制       │ │ ✅ 120fps+       │ │ ✅ 高兼容性     │
    │ ✅ 跨平台          │ │ ⚠️ 需主线程init  │ │ ✅ 稳定         │
    └────────────────────┘ └──────────────────┘ └─────────────────┘
```

### 3.2 渲染后端对比

| 特性 | JOGL | LWJGL | JCEF |
|------|------|-------|------|
| **渲染技术** | Native OpenGL | Native OpenGL | WebGL 2.0 |
| **性能(FPS)** | 120+ | 120+ | ~30 |
| **线程模型** | 无限制 | macOS主线程限制 | 浏览器线程 |
| **跨平台性** | ✅ 优秀 | ⚠️ macOS受限 | ✅ 优秀 |
| **集成复杂度** | 低 | 中 | 低 |
| **推荐度** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **适用场景** | 主力推荐 | 实验/Windows | 兼容方案 |

### 3.3 核心模块

#### 模块1: ShadertoyOutputWindowFactory (渲染窗口工厂) ✅

**职责**:
- 创建ToolWindow内容
- 根据用户配置选择渲染后端(JOGL/LWJGL/JCEF)
- 管理渲染后端生命周期
- 监听分辨率变更事件

**核心逻辑**:
```kotlin
init {
    // 根据配置创建渲染后端
    renderBackend = when (config.backendType) {
        "LWJGL" -> LwjglBackend(...)
        "JOGL" -> JoglBackend(...)
        else -> JCefBackend(...)
    }
    
    // 订阅分辨率变更
    // 监听ToolWindow尺寸变化
    // 初始化分辨率
}
```

#### 模块2: RenderBackend (渲染后端接口) ✅

**核心接口**:
```kotlin
interface RenderBackend : Disposable {
    fun getRootComponent(): JComponent          // 获取UI组件
    fun loadShader(fragmentShaderSource: String) // 加载shader
    fun updateRefCanvasResolution(width: Int, height: Int)  // 更新分辨率
    fun updateOuterResolution(width: Int, height: Int)      // 窗口尺寸变化
}
```

**三种实现**:
- **JoglBackend** - 使用GLCanvas + GLEventListener + FPSAnimator
- **LwjglBackend** - 使用GLFW + FBO + 独立渲染线程
- **JCefBackend** - 使用Chromium + WebGL 2.0

#### 模块3: ShaderCompileService (Shader编译服务) ✅

**职责**:
- 读取shader模板文件(使用VirtualFileSystem实时读取)
- 包装用户代码为完整Fragment Shader
- 添加Shadertoy标准uniforms

**核心方法**:
```kotlin
@Service(Service.Level.PROJECT)
class ShaderCompileService {
    fun compileShaderFromTemplate(): String
    private fun readImageGlslFile(): String    // VFS读取
    private fun wrapShaderCode(userCode: String): String  // 添加uniforms + main()
}
```

#### 模块4: 三种渲染后端实现

**JoglBackend (推荐)** ✅
- 实现`GLEventListener`接口
- `init()` - 创建VAO/VBO
- `display()` - 每帧渲染
- `loadShader()` - 在OpenGL上下文中编译shader
- 无线程限制,原生集成Swing

**LwjglBackend (实验)** ⚠️
- 使用`GLContext`管理OpenGL上下文和FBO
- 使用`RenderLoop`独立线程渲染
- Offscreen渲染输出到BufferedImage
- macOS有GLFW主线程限制

**JCefBackend (兼容)** ✅
- 使用`JBCefBrowser`嵌入Chromium
- JavaScript调用`window.loadShader(code)`
- WebGL 2.0渲染
- 帧率约30fps

#### 模块5: ShadertoyWindowFactory (主窗口) ✅

**职责**:
- 提供Compile按钮UI
- 调用ShaderCompileService编译shader
- 将编译结果发送到RenderBackend

---

## 四、文件结构与关键实现

### 4.1 文件结构（核心部分）

```
src/main/kotlin/com/github/edenlia/shadertoyeditor/
├── toolWindow/
│   ├── ShadertoyWindowFactory.kt          # 主窗口(Compile按钮)
│   └── ShadertoyOutputWindowFactory.kt    # 渲染窗口工厂
│
├── renderBackend/
│   ├── RenderBackend.kt                   # 渲染后端接口 ⭐
│   ├── RenderBackendType.kt               # 后端类型枚举
│   └── impl/
│       ├── jogl/JoglBackend.kt            # JOGL实现 (推荐)
│       ├── lwjgl/LwjglBackend.kt          # LWJGL实现 (实验)
│       ├── lwjgl/GLContext.kt             # OpenGL上下文管理
│       ├── lwjgl/RenderLoop.kt            # 渲染循环
│       └── jcef/JCefBackend.kt            # JCEF实现 (兼容)
│
├── services/
│   └── ShaderCompileService.kt            # Shader编译服务
│
├── settings/
│   ├── ShadertoyConfigurable.kt           # 设置页面
│   ├── ShadertoySettings.kt               # 设置持久化
│   └── ShadertoySettingsUI.kt             # 设置UI
│
├── model/
│   └── ShadertoyConfig.kt                 # 配置数据模型
│
└── listeners/
    └── RefCanvasResolutionChangedListener.kt  # 分辨率变更监听器

src/main/resources/
├── webview/
│   └── shadertoy-renderer.html            # WebGL渲染器(JCEF用)
├── shaderTemplate/
│   └── Image.glsl                         # Shader模板
└── META-INF/
    └── plugin.xml                         # 插件配置
```

### 4.2 关键技术点

#### 4.2.1 渲染后端选择机制

根据用户配置动态选择渲染后端:
```kotlin
val config = ShadertoySettings.getInstance().getConfig()
renderBackend = when (config.backendType.uppercase()) {
    "LWJGL" -> LwjglBackend(project, toolWindow.component)
    "JOGL" -> JoglBackend(project, toolWindow.component)
    else -> JCefBackend(project, toolWindow.component)
}
```

#### 4.2.2 分辨率管理

**两个分辨率概念**:
- **参考分辨率(refCanvas)**: 用户在Settings中设置的目标分辨率
- **真实分辨率(realCanvas)**: 根据ToolWindow大小和宽高比计算的实际渲染分辨率

**计算逻辑**:
```kotlin
// 保持宽高比,适配ToolWindow大小
val refAspect = refWidth / refHeight
val windowAspect = windowWidth / windowHeight

if (windowAspect > refAspect) {
    // 窗口更宽,高度受限
    realHeight = windowHeight
    realWidth = (windowHeight * refAspect).toInt()
} else {
    // 窗口更高,宽度受限
    realWidth = windowWidth
    realHeight = (windowWidth / refAspect).toInt()
}
```

#### 4.2.3 VirtualFileSystem文件读取

**为什么使用VFS**:
- ✅ 实时读取最新文件内容(无需重新编译插件)
- ✅ IntelliJ平台推荐方式
- ✅ 跨平台路径处理

```kotlin
val virtualFile = VirtualFileManager.getInstance()
    .findFileByUrl("file://$filePath")
return String(virtualFile.contentsToByteArray())
```

#### 4.2.4 Shader代码包装

将用户的`mainImage()`函数包装为完整的Fragment Shader:
- 添加`#version 300 es`
- 添加Shadertoy标准uniforms(iTime, iResolution, iFrame等)
- 添加`main()`函数调用用户的`mainImage()`

#### 4.2.5 JOGL Backend核心实现

**GLEventListener生命周期**:
- `init()` - 创建VAO/VBO,打印OpenGL信息
- `display()` - 每帧渲染,更新uniforms
- `reshape()` - 窗口尺寸变化
- `dispose()` - 清理OpenGL资源

**Shader编译**:
```kotlin
// 在GLCanvas的OpenGL上下文中编译
glCanvas?.invoke(false) { drawable ->
    val gl = drawable.gl.gL3
    shaderProgram = compileShaderProgram(gl, fragmentShaderSource)
    getUniformLocations(gl)
    true
}
```

#### 4.2.6 DumbService处理

IntelliJ索引期间必须等待:
```kotlin
if (DumbService.isDumb(project)) {
    DumbService.getInstance(project).runWhenSmart {
        compileShader()
    }
    return
}
```

#### 4.2.7 JCEF Backend通信

Java→JavaScript shader注入:
```kotlin
// 带自动重试的loadShader调用
val jsCode = """
    function tryLoadShader() {
        if (typeof window.loadShader === 'function') {
            window.loadShader(`$escapedCode`);
        } else {
            setTimeout(tryLoadShader, 100);  // 重试
        }
    }
    tryLoadShader();
"""
executeJavaScript(jsCode)
```

---

## 五、实施进度

### 阶段1: 基础渲染框架 ✅ **已完成**

- ✅ JCEF Backend实现(WebGL渲染)
- ✅ 工具窗口集成
- ✅ Shader编译服务
- ✅ VirtualFileSystem文件读取
- ✅ DumbService处理

### 阶段2: 多渲染后端架构 ✅ **已完成**

- ✅ RenderBackend接口抽象
- ✅ JOGL Backend实现(原生OpenGL,推荐)
- ✅ LWJGL Backend实现(实验性)
- ✅ 配置系统(用户可切换backend)
- ✅ 分辨率管理系统

### 阶段3: Settings集成 ✅ **已完成**

- ✅ 设置UI界面
- ✅ 参考分辨率配置
- ✅ Backend类型选择
- ✅ 分辨率变更监听器
- ✅ MessageBus事件通信

### 阶段4: 多Mapping支持 ⏳ **计划中**

- ⏳ 设计Shader Mapping配置系统
- ⏳ Mapping管理UI
- ⏳ 支持同一项目多个shader

### 阶段5: 多文件/多Pass支持 📅 **未来**

- 📅 Buffer A/B/C/D多pass渲染
- 📅 Common.glsl共享代码
- 📅 Shader依赖分析

### 阶段6: 高级功能 📅 **未来**

- 📅 鼠标交互(iMouse uniform)
- 📅 纹理加载(图片、cubemap)
- 📅 音频输入
- 📅 截图/录制功能
- 📅 自动文件监听(保存时自动编译)

---

## 六、主要技术挑战

### 6.1 已解决的挑战

#### 1. 性能问题
- **问题**: JCEF WebGL帧率限制在~30fps
- **解决**: 实现JOGL原生OpenGL backend,达到120fps+

#### 2. macOS线程限制
- **问题**: LWJGL的GLFW必须在主线程初始化
- **解决**: JOGL使用GLCanvas,无线程限制

#### 3. 分辨率管理
- **问题**: ToolWindow大小变化时如何保持宽高比
- **解决**: 实现参考分辨率+真实分辨率双系统

#### 4. 实时文件更新
- **问题**: 修改shader后需要重新编译插件
- **解决**: 使用VirtualFileSystem实时读取源文件

#### 5. IDE索引期间服务不可用
- **问题**: DumbMode期间无法访问服务
- **解决**: 使用DumbService.runWhenSmart延迟执行

### 6.2 未来挑战

#### 1. Shader编译错误定位
- 需要将编译后行号映射回源文件

#### 2. 多Pass渲染
- Buffer依赖分析
- 渲染顺序管理

#### 3. 外部资源加载
- 纹理文件路径处理
- 资源缓存机制

---

## 七、参考资料

### 7.1 官方文档

- [IntelliJ Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [JCEF Documentation](https://plugins.jetbrains.com/docs/intellij/jcef.html)
- [Tool Windows Guide](https://plugins.jetbrains.com/docs/intellij/tool-windows.html)
- [JOGL Documentation](https://jogamp.org/jogl/www/)
- [LWJGL Documentation](https://www.lwjgl.org/)

### 7.2 技术资源

- [Shadertoy官网](https://www.shadertoy.com/)
- [The Book of Shaders](https://thebookofshaders.com/)
- [GLSL Reference](https://www.khronos.org/opengl/wiki/OpenGL_Shading_Language)
- [shader-toy VSCode插件](https://github.com/stevensona/shader-toy) - 功能参考

---

## 附录

### A. 术语表

- **RenderBackend**: 渲染后端接口,抽象不同渲染技术
- **JOGL**: Java OpenGL,原生OpenGL绑定
- **LWJGL**: Lightweight Java Game Library
- **JCEF**: Java Chromium Embedded Framework
- **GLCanvas**: AWT/Swing的OpenGL画布组件
- **FBO**: Framebuffer Object,离屏渲染缓冲区
- **GLSL**: OpenGL Shading Language
- **VFS**: Virtual File System,IntelliJ虚拟文件系统
- **Uniform**: GLSL全局变量,用于传递参数

### B. 开发命令

```bash
# 构建项目
./gradlew build

# 运行调试
./gradlew runIde

# 打包插件
./gradlew buildPlugin
```

---

## 八、使用指南

### 8.1 基本使用

1. **打开工具窗口**
   - `View` → `Tool Windows` → `Shadertoy` (主窗口)
   - `View` → `Tool Windows` → `ShadertoyConsole` (渲染窗口)

2. **编辑Shader**
   - 打开 `src/main/resources/shaderTemplate/Image.glsl`
   - 编写 `mainImage()` 函数

3. **编译运行**
   - 点击主窗口的 **Compile** 按钮
   - 在渲染窗口查看效果

### 8.2 配置Backend

`Settings` → `Tools` → `Shadertoy Editor`:
- **Render Backend**: JOGL(推荐) / LWJGL / JCEF
- **Target Resolution**: 设置参考分辨率(如1280x720)
- 修改Backend需要重启IDE生效

### 8.3 Shader模板

```glsl
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    vec3 col = 0.5 + 0.5 * cos(iTime + uv.xyx + vec3(0, 2, 4));
    fragColor = vec4(col, 1.0);
}
```

**可用Uniforms**:
- `vec3 iResolution` - 视口分辨率
- `float iTime` - 时间(秒)
- `float iTimeDelta` - 帧间隔
- `int iFrame` - 帧计数
- `vec4 iDate` - 日期时间

### 8.4 常见问题

- **修改后没效果**: 保存文件后点击Compile
- **启动时灰屏**: 等待IDE索引完成
- **编译错误**: 错误会显示在渲染窗口或弹窗
- **性能低**: 切换到JOGL backend

---

**文档维护者**: 项目团队  
**文档版本**: 3.0.0  
**最后更新**: 2025-11-19  
**插件版本**: 0.0.1

### 更新历史

- **3.0.0** (2025-11-19): 精简版本 - 反映多Backend架构
  - ✅ 更新为多渲染后端架构(JOGL/LWJGL/JCEF)
  - ✅ 精简代码细节,保留核心框架
  - ✅ 重构技术栈章节
  - ✅ 更新架构图和模块说明
  - ✅ 添加Backend对比表格
  - ✅ 精简挑战和解决方案
  - ✅ 更新使用指南
  
- **2.0.0** (2025-11-18): 重大更新，反映实际实现架构
  
- **1.0.0** (2025-11-17): 初始设计文档

