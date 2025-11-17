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

## 二、JetBrains平台可用技术

### 2.1 JCEF (Java Chromium Embedded Framework) ⭐核心技术

#### 能力

- 在Swing应用中嵌入完整的Chromium浏览器
- 支持WebGL 2.0
- 支持HTML5全部特性
- 性能优秀

#### 主要组件

```kotlin
// 1. JBCefBrowser - 浏览器实例
val browser = JBCefBrowser()

// 2. JBCefJSQuery - Java调用JavaScript
val jsQuery = JBCefJSQuery.create(browser)

// 3. executeJavaScript - JavaScript调用Java
browser.cefBrowser.executeJavaScript(jsCode, url, 0)

// 4. CefMessageRouter - 双向通信
```

#### 依赖配置

```xml
<!-- plugin.xml -->
<depends>com.intellij.modules.platform</depends>
```

**注意**: JCEF从IntelliJ IDEA 2020.1版本开始可用。

### 2.2 其他相关API

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

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                   IntelliJ Platform                      │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  ┌─────────────────┐         ┌──────────────────────┐  │
│  │  Shadertoy      │         │ ShadertoyOutput      │  │
│  │  WindowFactory  │         │ WindowFactory        │  │
│  │  (主窗口)       │         │ (渲染窗口)           │  │
│  └────────┬────────┘         └──────────┬───────────┘  │
│           │                              │               │
│           │                              │               │
│  ┌────────▼────────┐         ┌──────────▼───────────┐  │
│  │  Editor Panel   │         │   JBCefBrowser       │  │
│  │  (代码编辑)     │◄───────►│   (WebGL渲染)        │  │
│  └─────────────────┘         └──────────┬───────────┘  │
│                                          │               │
│  ┌─────────────────────────────────────┼──────────┐   │
│  │         ShaderManager                │          │   │
│  │  - 解析shader代码                    │          │   │
│  │  - 管理buffer依赖                    │          │   │
│  │  - 资源加载                          │          │   │
│  │  - 与浏览器通信                      │          │   │
│  └──────────────────────────────────────┘          │   │
│                                                      │   │
└──────────────────────────────────────────────────────┘
                          │
                          ▼
        ┌─────────────────────────────────────┐
        │   Browser (JCEF/Chromium)           │
        ├─────────────────────────────────────┤
        │  webview_base.html                  │
        │  ├── Three.js                       │
        │  ├── WebGL Renderer                 │
        │  ├── Shader Programs                │
        │  ├── Texture Manager                │
        │  └── Message Handler (JS↔Java)      │
        └─────────────────────────────────────┘
```

### 3.2 核心模块

#### 模块1: ShadertoyOutputWindowFactory (渲染窗口工厂)

```kotlin
class ShadertoyOutputWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val outputWindow = ShadertoyOutputWindow(project, toolWindow)
        val content = ContentFactory.getInstance()
            .createContent(outputWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }
    
    class ShadertoyOutputWindow(
        private val project: Project,
        private val toolWindow: ToolWindow
    ) {
        private val browser: JBCefBrowser = JBCefBrowser()
        private val shaderManager: ShaderManager
        private val messageHandler: BrowserMessageHandler
        
        init {
            setupBrowser()
            setupCommunication()
            loadWebGLContent()
        }
        
        fun getContent(): JComponent {
            return browser.component
        }
    }
}
```

#### 模块2: ShaderManager (Shader管理器)

```kotlin
class ShaderManager(private val project: Project) {
    private val parser: ShaderParser
    private val bufferProvider: BufferProvider
    
    // 解析shader代码
    fun parseShader(code: String): ShaderData
    
    // 处理多pass依赖
    fun buildBufferChain(mainShader: String): List<BufferDefinition>
    
    // 监听文件变化
    fun watchFiles()
    
    // 发送shader到浏览器
    fun updateShaderInBrowser(shaderData: ShaderData)
}
```

#### 模块3: BrowserMessageHandler (通信处理器)

```kotlin
class BrowserMessageHandler(private val browser: JBCefBrowser) {
    private val jsToJavaQuery: JBCefJSQuery
    
    // Java -> JavaScript
    fun sendShaderUpdate(shader: String)
    fun sendCommand(command: String, params: Map<String, Any>)
    
    // JavaScript -> Java
    fun onMessage(message: String) {
        when (message.type) {
            "error" -> handleShaderError(message)
            "ready" -> handleBrowserReady(message)
            "pause" -> handlePauseRequest(message)
        }
    }
}
```

#### 模块4: WebviewContentAssembler (HTML内容生成器)

```kotlin
class WebviewContentAssembler {
    // 加载base HTML模板
    fun loadBaseTemplate(): String
    
    // 注入shader代码
    fun injectShaders(buffers: List<BufferDefinition>): String
    
    // 注入配置
    fun injectConfig(config: ShaderConfig): String
    
    // 生成最终HTML
    fun assembleContent(): String
}
```

---

## 四、详细实现方案

### 4.1 文件结构

```
src/main/
├── kotlin/com/github/edenlia/shadertoyeditor/
│   ├── toolWindow/
│   │   ├── ShadertoyWindowFactory.kt          # 主窗口
│   │   └── ShadertoyOutputWindowFactory.kt    # 渲染窗口
│   ├── browser/
│   │   ├── JCefBrowserComponent.kt            # JCEF浏览器组件
│   │   ├── BrowserMessageHandler.kt           # 消息处理
│   │   └── JavaScriptBridge.kt                # JS桥接
│   ├── shader/
│   │   ├── ShaderManager.kt                   # Shader管理
│   │   ├── ShaderParser.kt                    # 代码解析
│   │   ├── BufferProvider.kt                  # Buffer管理
│   │   └── ShaderLexer.kt                     # 词法分析
│   ├── webview/
│   │   ├── WebviewContentAssembler.kt         # HTML组装
│   │   └── ResourceManager.kt                 # 资源管理
│   └── listeners/
│       ├── FileChangeListener.kt              # 文件监听
│       └── EditorChangeListener.kt            # 编辑器监听
│
└── resources/
    ├── webview/
    │   ├── webview_base.html                  # 基础HTML模板
    │   ├── shadertoy.js                       # 主要JS逻辑
    │   └── styles.css                         # 样式
    ├── lib/
    │   ├── three.min.js                       # Three.js
    │   ├── stats.min.js                       # 性能监控
    │   └── dat.gui.min.js                     # GUI控制
    └── shaders/
        └── default.frag                       # 默认shader
```

### 4.2 关键实现细节

#### 4.2.1 JCEF浏览器初始化

```kotlin
class JCefBrowserComponent(private val project: Project) {
    private val browser: JBCefBrowser
    
    init {
        // 检查JCEF是否可用
        if (!JBCefApp.isSupported()) {
            throw UnsupportedOperationException("JCEF is not supported")
        }
        
        // 创建浏览器实例
        browser = JBCefBrowser()
        
        // 启用开发者工具（调试用）
        browser.jbCefClient.setProperty(
            JBCefClient.Properties.JS_QUERY_POOL_SIZE, 
            10
        )
        
        // 设置生命周期处理
        Disposer.register(project, browser)
    }
    
    fun loadContent(htmlContent: String) {
        // 方案1: 直接加载HTML字符串
        browser.loadHTML(htmlContent)
        
        // 方案2: 加载本地文件URL
        // val url = getResourceURL("webview/webview_base.html")
        // browser.loadURL(url)
    }
    
    fun getComponent(): JComponent = browser.component
}
```

#### 4.2.2 Java ↔ JavaScript 通信

```kotlin
// Java -> JavaScript
fun sendShaderToJS(shaderCode: String) {
    val escapedCode = shaderCode
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
    
    val jsCode = """
        window.updateShader('$escapedCode');
    """.trimIndent()
    
    browser.cefBrowser.executeJavaScript(jsCode, browser.cefBrowser.url, 0)
}

// JavaScript -> Java
fun setupJavaScriptBridge() {
    val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
    
    query.addHandler { message ->
        ApplicationManager.getApplication().invokeLater {
            handleMessageFromJS(message)
        }
        return@addHandler null
    }
    
    // 在HTML中注入JS桥接函数
    val injectionScript = """
        window.sendToJava = function(message) {
            ${query.inject("message")}
        };
    """.trimIndent()
    
    browser.cefBrowser.executeJavaScript(
        injectionScript,
        browser.cefBrowser.url,
        0
    )
}
```

#### 4.2.3 文件变化监听

```kotlin
class FileChangeListener(
    private val project: Project,
    private val onFileChanged: (VirtualFile) -> Unit
) : BulkFileListener {
    
    fun register() {
        project.messageBus.connect()
            .subscribe(VirtualFileManager.VFS_CHANGES, this)
    }
    
    override fun after(events: List<VFileEvent>) {
        events.forEach { event ->
            if (event is VFileContentChangeEvent) {
                val file = event.file
                if (file.extension == "glsl" || file.extension == "frag") {
                    onFileChanged(file)
                }
            }
        }
    }
}
```

#### 4.2.4 HTML内容生成

```kotlin
class WebviewContentAssembler(private val project: Project) {
    
    fun generateHTML(shaderData: ShaderData): String {
        // 读取基础模板
        val template = loadTemplate("webview/webview_base.html")
        
        // 替换占位符
        return template
            .replace("<!-- Shaders -->", generateShaderScripts(shaderData))
            .replace("<!-- Three.js -->", getLibraryPath("three.min.js"))
            .replace("<!-- Start Time -->", "0.0")
            .replace("<!-- Start Paused -->", "false")
    }
    
    private fun generateShaderScripts(shaderData: ShaderData): String {
        return shaderData.buffers.joinToString("\n") { buffer ->
            """
            <script type="x-shader/x-fragment" id="${buffer.name}">
            ${buffer.code}
            </script>
            """.trimIndent()
        }
    }
    
    private fun getLibraryPath(libName: String): String {
        val resource = javaClass.getResource("/webview/lib/$libName")
        return resource?.toExternalForm() ?: ""
    }
}
```

### 4.3 通信协议设计

#### JavaScript → Java 消息格式

```json
{
  "type": "error" | "ready" | "pause" | "screenshot" | "state",
  "payload": {
    "line": 42,
    "message": "Compilation error",
    "file": "shader.frag"
  }
}
```

#### Java → JavaScript 命令格式

```javascript
// 更新shader
window.updateShader({
    buffers: [...],
    textures: [...],
    uniforms: {...}
});

// 控制命令
window.executeCommand({
    command: "pause" | "play" | "reset" | "screenshot",
    params: {...}
});
```

### 4.4 VSCode插件关键功能分析

根据shader-toy VSCode插件的实现，以下是关键功能点：

#### WebviewContentProvider 核心逻辑

1. **Shader解析**: 通过`BufferProvider`解析shader代码，识别多pass渲染
2. **资源管理**: 处理纹理、音频、cubemap等外部资源
3. **HTML生成**: 使用`WebviewContentAssembler`动态生成包含所有依赖的HTML
4. **扩展系统**: 通过Extension模式添加各种功能（键盘、音频、uniform等）

#### 需要移植的关键部分

- `BufferProvider`: shader依赖分析
- `ShaderParser`: GLSL代码解析
- `webview_base.html`: WebGL渲染模板
- Extension系统: 功能模块化

---

## 五、实施步骤

### 阶段1: 基础框架 (1-2周)

**任务**:
1. ✅ 创建`ShadertoyOutputWindow`基础结构
2. ✅ 集成JCEF浏览器组件
3. ✅ 实现基本的HTML加载
4. ✅ 测试WebGL基础渲染

**交付物**:
- 能在工具窗口中显示静态HTML页面
- 简单的WebGL三角形渲染示例

### 阶段2: 通信机制 (1周)

**任务**:
1. ✅ 实现JavaScript↔Java双向通信
2. ✅ 设计消息协议
3. ✅ 测试数据传输

**交付物**:
- Java能向JS发送shader代码
- JS能向Java报告错误和状态

### 阶段3: Shader解析和管理 (2-3周)

**任务**:
1. ✅ 移植/重写ShaderParser
2. ✅ 实现BufferProvider
3. ✅ 支持多pass渲染
4. ✅ 纹理和资源加载

**交付物**:
- 能解析复杂的shader依赖关系
- 支持多buffer渲染
- 能加载外部纹理资源

### 阶段4: 实时更新 (1周)

**任务**:
1. ✅ 文件监听器
2. ✅ 自动刷新机制
3. ✅ 错误处理和提示

**交付物**:
- 修改shader文件自动更新预览
- 编译错误实时提示
- 性能优化（防抖动）

### 阶段5: 高级功能 (2周)

**任务**:
1. ✅ 鼠标/键盘交互
2. ✅ 截图功能
3. ✅ 性能优化
4. ✅ 配置管理

**交付物**:
- 完整的用户交互支持
- 截图和录制功能
- 性能统计和优化

---

## 六、潜在挑战和解决方案

### 6.1 挑战清单

#### 1. JCEF兼容性

**问题**: 老版本IDE可能不支持JCEF

**解决方案**:
- 在`plugin.xml`中指定最低IDE版本为2020.1
- 启动时检测JCEF可用性
- 提供降级方案（外部浏览器预览）

```kotlin
if (!JBCefApp.isSupported()) {
    Notifications.Bus.notify(
        Notification(
            "Shadertoy Editor",
            "JCEF Not Supported",
            "Please upgrade to IntelliJ IDEA 2020.1 or later",
            NotificationType.ERROR
        )
    )
    return
}
```

#### 2. 性能问题

**问题**: WebGL渲染可能影响IDE性能

**解决方案**:
- 使用独立线程渲染
- 限制刷新频率（配置项）
- 提供性能模式切换
- 工具窗口隐藏时暂停渲染

```kotlin
// 性能配置
data class PerformanceConfig(
    val maxFPS: Int = 60,
    val pauseWhenHidden: Boolean = true,
    val enableVSync: Boolean = true
)
```

#### 3. 资源路径问题

**问题**: 本地文件访问权限限制

**解决方案**:
- 方案A: 使用`data:` URI嵌入小资源
- 方案B: 创建临时HTTP服务器
- 方案C: 使用JCEF的`loadHTML`直接加载

```kotlin
// 方案A: Data URI
fun embedResource(path: String): String {
    val bytes = javaClass.getResourceAsStream(path).readBytes()
    val base64 = Base64.getEncoder().encodeToString(bytes)
    val mimeType = getMimeType(path)
    return "data:$mimeType;base64,$base64"
}

// 方案B: 临时HTTP服务器
val server = HttpServer.create(InetSocketAddress(0), 0)
server.createContext("/resources") { exchange ->
    // 处理资源请求
}
server.start()
```

#### 4. 异步通信线程安全

**问题**: Java和JS之间的线程安全问题

**解决方案**:
- 使用`invokeLater`处理UI更新
- 消息队列缓冲
- 状态同步机制

```kotlin
// 线程安全的消息发送
fun sendMessageSafely(message: String) {
    ApplicationManager.getApplication().invokeLater {
        browser.cefBrowser.executeJavaScript(message, "", 0)
    }
}
```

#### 5. Shader编译错误定位

**问题**: WebGL错误信息行号需要映射回源文件

**解决方案**:
- 维护行号映射表
- 解析WebGL错误消息
- 在编辑器中高亮错误行

```kotlin
data class LineMapping(
    val sourceFile: String,
    val sourceLine: Int,
    val glslLine: Int
)

fun mapErrorLine(glslLine: Int): Pair<String, Int> {
    return lineMappings.find { it.glslLine == glslLine }
        ?.let { it.sourceFile to it.sourceLine }
        ?: ("unknown" to 0)
}
```

### 6.2 测试策略

#### 单元测试
- ShaderParser逻辑测试
- BufferProvider依赖解析测试
- 消息序列化/反序列化测试

#### 集成测试
- JCEF浏览器加载测试
- Java↔JS通信测试
- 文件监听器测试

#### 性能测试
- 大型shader编译性能
- 多buffer渲染性能
- 内存泄漏检测

---

## 七、参考资料

### 7.1 官方文档

1. **JetBrains平台开发文档**
   - [JCEF Documentation](https://plugins.jetbrains.com/docs/intellij/jcef.html)
   - [Tool Windows Guide](https://plugins.jetbrains.com/docs/intellij/tool-windows.html)
   - [Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)

2. **WebGL和Three.js**
   - [Three.js Documentation](https://threejs.org/docs/)
   - [WebGL Specification](https://www.khronos.org/webgl/)
   - [Shadertoy Documentation](https://www.shadertoy.com/howto)

### 7.2 示例项目

1. **JetBrains官方示例**
   - [intellij-platform-plugin-template](https://github.com/JetBrains/intellij-platform-plugin-template)
   - [Markdown Plugin](https://github.com/JetBrains/intellij-community/tree/master/plugins/markdown) - 使用JCEF实现预览

2. **本项目参考**
   - [shader-toy VSCode插件](https://github.com/stevensona/shader-toy) - 功能参考
   - 项目路径: `C:\Users\ethanzzhang\Workspace\CodeProjects\shader-toy`

### 7.3 技术文章

1. **JCEF相关**
   - [Embedding Chromium in IntelliJ Platform](https://blog.jetbrains.com/platform/2020/07/javafx-and-jcef-in-the-intellij-platform/)
   - [JCEF API Examples](https://github.com/chromiumembedded/java-cef)

2. **Shader开发**
   - [The Book of Shaders](https://thebookofshaders.com/)
   - [GLSL Syntax Reference](https://www.khronos.org/opengl/wiki/OpenGL_Shading_Language)

### 7.4 相关工具

- [ShaderToy官网](https://www.shadertoy.com/)
- [GLSL Sandbox](http://glslsandbox.com/)
- [Shader Editor](https://shaderfrog.com/)

---

## 附录

### A. 术语表

- **JCEF**: Java Chromium Embedded Framework，在Java应用中嵌入Chromium浏览器
- **WebGL**: 基于OpenGL ES的Web图形API
- **GLSL**: OpenGL Shading Language，着色器编程语言
- **Buffer**: Shadertoy中的渲染通道，支持多pass渲染
- **Uniform**: GLSL中的全局变量，用于传递参数
- **VFS**: Virtual File System，IntelliJ平台的虚拟文件系统

### B. 配置示例

#### plugin.xml配置

```xml
<idea-plugin>
    <!-- 最低IDE版本要求 -->
    <idea-version since-build="203.0"/>
    
    <extensions defaultExtensionNs="com.intellij">
        <!-- 渲染窗口 -->
        <toolWindow 
            id="ShadertoyConsole" 
            factoryClass="com.github.edenlia.shadertoyeditor.toolWindow.ShadertoyOutputWindowFactory" 
            anchor="bottom" 
            secondary="true"/>
        
        <!-- 配置页面 -->
        <applicationConfigurable 
            groupId="tools" 
            displayName="Shadertoy Editor" 
            instance="com.github.edenlia.shadertoyeditor.settings.ShadertoyConfigurable"/>
    </extensions>
    
    <actions>
        <!-- 预览命令 -->
        <action 
            id="shadertoy.showPreview" 
            class="com.github.edenlia.shadertoyeditor.actions.ShowPreviewAction"
            text="Show Shader Preview">
            <keyboard-shortcut keymap="$default" first-keystroke="ctrl alt S"/>
        </action>
    </actions>
</idea-plugin>
```

### C. 开发环境设置

```bash
# 克隆项目
git clone https://github.com/edenlia/ShadertoyEditor-IntelliJ.git

# 构建项目
./gradlew build

# 运行插件调试
./gradlew runIde

# 打包插件
./gradlew buildPlugin
```

### D. 更新日志模板

```markdown
## [版本号] - YYYY-MM-DD

### Added
- 新功能描述

### Changed
- 修改内容

### Fixed
- 修复的bug

### Removed
- 移除的功能
```

---

**文档维护者**: 项目团队  
**最后更新**: 2025-11-17  
**版本**: 1.0.0

