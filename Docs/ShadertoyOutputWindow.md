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

### 3.1 整体架构（当前实现）

```
┌─────────────────────────────────────────────────────────────┐
│                   IntelliJ Platform                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────┐      ┌──────────────────────┐    │
│  │  ShadertoyWindow     │      │ ShadertoyConsole     │    │
│  │  (主窗口)            │      │ (渲染窗口)           │    │
│  │  - Shuffle按钮       │      │                      │    │
│  │  - Compile按钮  ━━━━━━━━━━━▶│  JCefBrowser        │    │
│  └──────────┬───────────┘      │  (WebGL渲染)         │    │
│             │                   └──────────┬───────────┘    │
│             │                              │                 │
│             ▼                              │                 │
│  ┌──────────────────────┐                 │                 │
│  │ ShaderCompileService │                 │                 │
│  │ - readImageGlslFile()│                 │                 │
│  │ - wrapShaderCode()   │                 │                 │
│  └──────────┬───────────┘                 │                 │
│             │                              │                 │
│             ▼                              │                 │
│  ┌──────────────────────────────────────┐ │                 │
│  │    VirtualFileSystem                  │ │                 │
│  │    src/main/resources/               │ │                 │
│  │    shaderTemplate/Image.glsl         │ │                 │
│  └──────────────────────────────────────┘ │                 │
│                                            │                 │
│             │                              │                 │
│             └──────────────────────────────┘                 │
│                      JavaScript执行                          │
│                 window.loadShader(code)                      │
│                                                               │
└───────────────────────────────────────────────────────────────┘
                          │
                          ▼
        ┌─────────────────────────────────────────┐
        │   Browser (JCEF/Chromium)               │
        ├─────────────────────────────────────────┤
        │  shadertoy-renderer.html                │
        │  ├── WebGL 2.0 Renderer                 │
        │  ├── Shader Compiler                    │
        │  ├── window.loadShader() API            │
        │  ├── Performance Stats (FPS/Frame)      │
        │  └── Error Display                      │
        └─────────────────────────────────────────┘
```

### 3.2 核心模块（实际实现）

#### 模块1: ShadertoyOutputWindowFactory (渲染窗口工厂) ✅

```kotlin
class ShadertoyOutputWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val shadertoyOutputWindow = ShadertoyOutputWindow(project)
        val content = ContentFactory.getInstance()
            .createContent(shadertoyOutputWindow.getContent(), null, false)
        
        // 将实例保存到project的userData中，供其他组件访问
        project.putUserData(SHADERTOY_OUTPUT_WINDOW_KEY, shadertoyOutputWindow)
        
        Disposer.register(content) {
            project.putUserData(SHADERTOY_OUTPUT_WINDOW_KEY, null)
            shadertoyOutputWindow.dispose()
        }
        
        toolWindow.contentManager.addContent(content)
    }
    
    class ShadertoyOutputWindow(private val project: Project) {
        private val browserComponent: JCefBrowserComponent
        
        init {
            browserComponent = JCefBrowserComponent(project)
        }
        
        fun getContent(): JComponent = browserComponent.getComponent()
        fun getBrowserComponent(): JCefBrowserComponent = browserComponent
        fun dispose() = browserComponent.dispose()
    }
    
    companion object {
        // 获取项目的ShadertoyOutputWindow实例
        fun getInstance(project: Project): ShadertoyOutputWindow?
    }
}
```

#### 模块2: JCefBrowserComponent (浏览器组件) ✅

```kotlin
class JCefBrowserComponent(
    private val project: Project,
    private val htmlFile: String = "shadertoy-renderer.html"
) : Disposable {
    private val browser: JBCefBrowser
    
    init {
        // 创建浏览器并启用开发者工具
        browser = JBCefBrowser()
        browser.jbCefClient.setProperty("remote_debugging_port", "9222")
        loadInitialContent()
    }
    
    // 执行JavaScript代码
    fun executeJavaScript(jsCode: String)
    
    // 加载shader代码到WebGL渲染器（带自动重试机制）
    fun loadShaderCode(fragmentShaderSource: String)
    
    fun getComponent(): JComponent = browser.component
}
```

#### 模块3: ShaderCompileService (Shader编译服务) ✅

```kotlin
@Service(Service.Level.PROJECT)
class ShaderCompileService(private val project: Project) {
    
    // 编译shader模板文件
    fun compileShaderFromTemplate(): String {
        val glslContent = readImageGlslFile()
        return wrapShaderCode(glslContent)
    }
    
    // 使用VirtualFileSystem读取Image.glsl（实时更新）
    private fun readImageGlslFile(): String {
        val filePath = "$projectBasePath/src/main/resources/shaderTemplate/Image.glsl"
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
        return String(virtualFile.contentsToByteArray())
    }
    
    // 包装用户代码为完整的Fragment Shader
    private fun wrapShaderCode(userGlslCode: String): String {
        // 添加 #version 300 es、uniforms、out、main()函数
    }
}
```

#### 模块4: ShadertoyWindowFactory (主窗口) ✅

```kotlin
class ShadertoyWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val shadertoyWindow = ShadertoyWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(shadertoyWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
        
        // 等待索引完成后自动触发首次编译
        DumbService.getInstance(project).runWhenSmart {
            SwingUtilities.invokeLater {
                shadertoyWindow.compileShader()
            }
        }
    }
    
    class ShadertoyWindow(private val toolWindow: ToolWindow) {
        private val shaderCompileService = project.service<ShaderCompileService>()
        
        // UI包含Shuffle按钮和Compile按钮
        fun getContent() = JBPanel<JBPanel<*>>().apply {
            add(JButton("Shuffle") { ... })
            add(JButton("Compile") { compileShader() })
        }
        
        // 编译并加载shader（处理Dumb Mode）
        fun compileShader() {
            if (DumbService.isDumb(project)) {
                DumbService.getInstance(project).runWhenSmart { compileShader() }
                return
            }
            
            val shaderCode = shaderCompileService.compileShaderFromTemplate()
            outputWindow.getBrowserComponent().loadShaderCode(shaderCode)
        }
    }
}
```

---

## 四、详细实现方案

### 4.1 文件结构（当前实现）

```
src/main/
├── kotlin/com/github/edenlia/shadertoyeditor/
│   ├── toolWindow/
│   │   ├── ShadertoyWindowFactory.kt          # 主窗口 ✅
│   │   ├── ShadertoyOutputWindowFactory.kt    # 渲染窗口 ✅
│   │   └── ShadertoyWindowFactory.kt          # (另一个窗口)
│   ├── browser/
│   │   └── JCefBrowserComponent.kt            # JCEF浏览器组件 ✅
│   ├── services/
│   │   ├── ShaderCompileService.kt            # Shader编译服务 ✅
│   │   ├── MyProjectService.kt                # 项目服务
│   │   └── ConfigUsageExample.kt              # 配置示例
│   ├── settings/
│   │   ├── ShadertoyConfigurable.kt           # 设置页面
│   │   ├── ShadertoySettings.kt               # 设置存储
│   │   └── ShadertoySettingsUI.kt             # 设置UI
│   ├── startup/
│   │   ├── HelloWorldAction.kt                # 示例Action
│   │   └── MyProjectActivity.kt               # 启动Activity
│   ├── model/
│   │   └── ShadertoyConfig.kt                 # 配置模型
│   └── MyBundle.kt                            # 国际化
│
└── resources/
    ├── webview/
    │   ├── shadertoy-renderer.html            # WebGL渲染器 ✅
    │   ├── cube-preview.html                  # 立方体预览
    │   ├── test-red.html                      # 测试页面
    │   └── test-simple-shader.html            # 简单shader测试
    ├── shaderTemplate/
    │   └── Image.glsl                         # Shader模板 ✅
    ├── messages/
    │   └── MyBundle.properties                # 国际化文本
    └── META-INF/
        └── plugin.xml                         # 插件配置 ✅
```

**说明**：
- ✅ 标记的是当前已实现且正在使用的核心文件
- 其他文件是框架生成的或用于未来扩展

### 4.2 关键实现细节（当前实现）

#### 4.2.1 JCEF浏览器初始化 ✅

```kotlin
class JCefBrowserComponent(
    private val project: Project,
    private val htmlFile: String = "shadertoy-renderer.html"
) : Disposable {
    private val browser: JBCefBrowser
    
    init {
        // 检查JCEF是否被支持
        if (!JBCefApp.isSupported()) {
            throw UnsupportedOperationException(
                "JCEF is not supported in this IDE. " +
                "Please upgrade to IntelliJ IDEA 2020.1 or later."
            )
        }
        
        // 创建浏览器实例
        browser = JBCefBrowser()
        
        // 启用开发者工具（用于调试）
        // 右键点击网页 -> "Open DevTools" 可以查看控制台日志
        browser.jbCefClient.setProperty("remote_debugging_port", "9222")
        
        // 设置生命周期管理
        Disposer.register(project, this)
        
        // 加载初始HTML内容
        loadInitialContent()
    }
    
    private fun loadInitialContent() {
        val htmlContent = javaClass.getResource("/webview/$htmlFile")?.readText()
            ?: throw IllegalStateException("$htmlFile not found in resources/webview/")
        
        browser.loadHTML(htmlContent)
    }
    
    fun getComponent(): JComponent = browser.component
    override fun dispose() = browser.dispose()
}
```

#### 4.2.2 Java → JavaScript 通信（Shader注入）✅

```kotlin
/**
 * 执行JavaScript代码
 */
fun executeJavaScript(jsCode: String) {
    browser.cefBrowser.executeJavaScript(jsCode, browser.cefBrowser.url, 0)
}

/**
 * 加载shader代码到WebGL渲染器
 * 包含自动重试机制，确保window.loadShader可用后再执行
 */
fun loadShaderCode(fragmentShaderSource: String) {
    // 转义特殊字符，使用模板字符串
    val escapedCode = fragmentShaderSource
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("$", "\\$")
    
    // 调用网页中的 window.loadShader 函数
    // 使用 setTimeout 确保在浏览器完全加载后执行
    val jsCode = """
        (function() {
            console.log('[Shadertoy] Attempting to load shader...');
            
            function tryLoadShader() {
                if (typeof window.loadShader === 'function') {
                    console.log('[Shadertoy] window.loadShader found, loading shader...');
                    try {
                        window.loadShader(`$escapedCode`);
                        console.log('[Shadertoy] Shader loaded and compiled successfully!');
                    } catch (e) {
                        console.error('[Shadertoy] Failed to load shader:', e);
                    }
                } else {
                    console.warn('[Shadertoy] window.loadShader not ready, retrying in 100ms...');
                    setTimeout(tryLoadShader, 100);
                }
            }
            
            tryLoadShader();
        })();
    """.trimIndent()
    
    executeJavaScript(jsCode)
}
```

**特点**：
- ✅ 自动重试机制：如果 `window.loadShader` 未就绪，每100ms重试一次
- ✅ 完整的日志输出：便于调试
- ✅ 异常处理：捕获shader编译错误

#### 4.2.3 VirtualFileSystem 文件读取 ✅

```kotlin
/**
 * 使用VirtualFileSystem读取Image.glsl文件
 * 优势：实时读取最新文件内容，无需重新编译插件
 */
private fun readImageGlslFile(): String {
    val projectBasePath = project.basePath 
        ?: throw IllegalStateException("Project base path is null")
    
    // 构建文件路径
    val filePath = "$projectBasePath/src/main/resources/shaderTemplate/Image.glsl"
    
    // 使用 VirtualFileManager 查找文件
    val virtualFile = VirtualFileManager.getInstance()
        .findFileByUrl("file://$filePath")
        ?: throw IllegalStateException("Image.glsl not found at: $filePath")
    
    // 读取文件内容（实时获取）
    return String(virtualFile.contentsToByteArray())
}
```

**为什么使用 VirtualFileSystem？**
1. ✅ **实时更新**：读取文件系统中的最新内容，不是编译后的静态资源
2. ✅ **IntelliJ 标准**：这是 JetBrains 平台推荐的文件访问方式
3. ✅ **易于扩展**：未来支持多 mapping 时只需参数化路径
4. ✅ **跨平台**：自动处理不同操作系统的路径差异

**对比 `javaClass.getResource()`**：
- ❌ `getResource()` 读取的是编译时打包的静态文件
- ❌ 修改源文件后必须重新构建才能看到变化
- ✅ VirtualFileSystem 直接读取源文件，修改后点击 Compile 立即生效

#### 4.2.4 Shader代码包装 ✅

```kotlin
/**
 * 将用户的mainImage函数包装成完整的Fragment Shader
 * 用户只需在Image.glsl中写mainImage函数，其他部分自动添加
 */
private fun wrapShaderCode(userGlslCode: String): String {
    return """
#version 300 es
precision highp float;

uniform vec3 iResolution;
uniform float iTime;
uniform float iTimeDelta;
uniform int iFrame;
uniform vec4 iMouse;
uniform vec4 iDate;

out vec4 fragColor;

$userGlslCode

void main() {
    mainImage(fragColor, gl_FragCoord.xy);
}
    """.trimIndent()
}
```

**包装内容**：
- ✅ `#version 300 es` - WebGL 2.0 版本声明
- ✅ `precision highp float` - 高精度浮点数
- ✅ Shadertoy标准uniforms（iTime、iResolution等）
- ✅ `out vec4 fragColor` - 输出颜色
- ✅ `main()` 函数 - 调用用户的 `mainImage()`

**用户只需写**：
```glsl
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    vec3 col = 0.5 + 0.5 * cos(iTime + uv.xyx + vec3(0.0, 2.0, 4.0));
    fragColor = vec4(col, 1.0);
}
```

### 4.3 DumbService处理（索引问题）✅

在 IntelliJ 启动或构建索引时，很多服务不可用（Dumb Mode）。必须等待索引完成才能执行编译。

```kotlin
/**
 * 创建工具窗口时自动触发首次编译
 * 使用DumbService确保在索引完成后执行
 */
override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val shadertoyWindow = ShadertoyWindow(toolWindow)
    // ...
    
    // 等待索引构建完成后再自动触发第一次编译
    DumbService.getInstance(project).runWhenSmart {
        SwingUtilities.invokeLater {
            shadertoyWindow.compileShader()
        }
    }
}

/**
 * 用户手动点击Compile时也要检查
 */
fun compileShader() {
    // 检查是否处于索引构建模式
    if (DumbService.isDumb(project)) {
        thisLogger().info("Cannot compile shader during indexing, will retry when indexing is complete")
        // 等待索引完成后再执行
        DumbService.getInstance(project).runWhenSmart {
            compileShader()
        }
        return
    }
    
    // 正常编译流程...
}
```

**关键API**：
- `DumbService.isDumb(project)` - 检查是否在索引中
- `runWhenSmart { }` - 等待索引完成后执行回调
- `SwingUtilities.invokeLater { }` - UI线程安全

### 4.4 WebGL渲染器（HTML端）✅

#### shadertoy-renderer.html 核心功能

1. **WebGL 2.0 初始化**：创建context、canvas管理
2. **Shader编译系统**：编译vertex/fragment shader，链接program
3. **Uniform管理**：iTime、iResolution、iFrame等标准uniform
4. **渲染循环**：requestAnimationFrame驱动的持续渲染
5. **性能监控**：FPS、Frame Time、Compile Time统计
6. **错误显示**：Shader编译错误的可视化显示

#### window.loadShader API

```javascript
// 暴露给Java端的API
window.loadShader = function(fragmentShaderSource) {
    try {
        console.log('[WebGL] Starting shader compilation...');
        const vertexSource = document.getElementById('vertexShader').textContent.trim();
        
        // 删除旧的program（如果存在）
        if (program) {
            gl.deleteProgram(program);
        }
        
        // 编译新的shader程序
        program = createProgram(vertexSource, fragmentShaderSource);
        uniforms = setupUniforms(program);
        
        // 重置时间和帧计数器，让动画效果更明显
        startTime = performance.now();
        frameCounter = 0;
        
        hideError();
        console.log('[WebGL] Shader loaded and compiled successfully!');
    } catch (e) {
        console.error('[WebGL] Failed to load shader:', e);
        showError(e.message || String(e));
    }
};
```

---

## 五、实施步骤（当前进度）

### 阶段1: 基础框架 ✅ **已完成**

**任务**:
1. ✅ 创建`ShadertoyOutputWindowFactory`基础结构
2. ✅ 集成JCEF浏览器组件（JCefBrowserComponent）
3. ✅ 实现HTML加载（shadertoy-renderer.html）
4. ✅ 测试WebGL 2.0渲染

**交付物**:
- ✅ 工具窗口显示WebGL内容
- ✅ 默认shader自动渲染（彩色渐变动画）
- ✅ 性能监控（FPS、Frame Time）

### 阶段2: 单文件Compile功能 ✅ **已完成**

**任务**:
1. ✅ 实现Java→JavaScript通信（executeJavaScript）
2. ✅ 创建ShaderCompileService服务
3. ✅ 使用VirtualFileSystem读取GLSL文件
4. ✅ 实现Shader代码包装（添加uniforms和main函数）
5. ✅ 添加Compile按钮到主窗口
6. ✅ 处理DumbService（索引问题）
7. ✅ 自动重试机制（确保window.loadShader可用）

**交付物**:
- ✅ 用户可编辑`src/main/resources/shaderTemplate/Image.glsl`
- ✅ 点击Compile按钮实时看到效果
- ✅ 编译错误在网页上显示
- ✅ 启动时自动加载shader（等待索引完成）

### 阶段3: 多Mapping支持 ⏳ **计划中**

**任务**:
1. ⏳ 设计Shader Mapping配置系统
2. ⏳ 实现Mapping管理UI（添加/删除/选择）
3. ⏳ 持久化存储Mapping配置
4. ⏳ 参数化文件路径读取
5. ⏳ 支持同一项目多个shader项目

**交付物**:
- ⏳ 可配置的Mapping目录
- ⏳ 下拉框选择不同的Mapping
- ⏳ 配置保存到项目设置

### 阶段4: 多文件/多Pass支持 📅 **未来**

**任务**:
1. 📅 支持Buffer A/B/C/D多pass渲染
2. 📅 实现Common.glsl共享代码
3. 📅 Shader依赖分析
4. 📅 渲染顺序管理

**交付物**:
- 📅 支持复杂的多pass shader
- 📅 像真实Shadertoy一样的完整功能

### 阶段5: 高级功能 📅 **未来**

**任务**:
1. 📅 鼠标交互（iMouse uniform）
2. 📅 键盘输入
3. 📅 纹理加载（图片、cubemap）
4. 📅 音频输入
5. 📅 截图/录制功能
6. 📅 自动文件监听（保存时自动编译）

**交付物**:
- 📅 完整的交互支持
- 📅 外部资源加载
- 📅 更流畅的开发体验

---

### 当前状态总结

**✅ 已实现**：
- 基础WebGL渲染管线
- 单文件Shader编译
- 手动Compile触发
- 实时文件读取（VirtualFileSystem）
- 错误显示
- 性能监控
- DumbService处理

**🚧 正在进行**：
- 文档更新和完善

**📋 下一步计划**：
- 多Mapping支持（让用户可以配置不同的shader项目目录）

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

## 八、使用指南（Quick Start）

### 8.1 开发环境运行

1. **打开项目**
   ```bash
   cd ShadertoyEditor-IntelliJ
   ./gradlew build
   ./gradlew runIde
   ```

2. **打开工具窗口**
   - `View` → `Tool Windows` → `Shadertoy`（主窗口，包含Compile按钮）
   - `View` → `Tool Windows` → `ShadertoyConsole`（渲染窗口）

3. **编辑Shader**
   - 打开 `src/main/resources/shaderTemplate/Image.glsl`
   - 修改 `mainImage` 函数中的代码

4. **编译查看效果**
   - 点击主窗口中的 **Compile** 按钮
   - 在 ShadertoyConsole 窗口中查看渲染结果

5. **调试（可选）**
   - 右键点击 ShadertoyConsole 窗口
   - 选择 "Open DevTools"（如果可用）
   - 查看浏览器控制台日志

### 8.2 Shader编写规范

只需在 `Image.glsl` 中编写 `mainImage` 函数：

```glsl
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    // 归一化坐标（0到1）
    vec2 uv = fragCoord / iResolution.xy;
    
    // 你的shader代码
    vec3 col = vec3(uv.x, uv.y, 0.5);
    
    // 输出颜色
    fragColor = vec4(col, 1.0);
}
```

**可用的Uniforms**：
- `vec3 iResolution` - 视口分辨率（宽，高，像素比）
- `float iTime` - 播放时间（秒）
- `float iTimeDelta` - 帧间隔时间（秒）
- `int iFrame` - 帧计数器
- `vec4 iMouse` - 鼠标位置（未实现）
- `vec4 iDate` - 当前日期时间

### 8.3 常见问题

**Q: 修改文件后看不到效果？**  
A: 确保保存了文件（Ctrl+S / Cmd+S），然后点击 Compile 按钮。

**Q: 启动时显示"视图不可用"？**  
A: 等待IDE索引构建完成，会自动触发首次编译。

**Q: Shader编译错误在哪看？**  
A: 错误会直接显示在 ShadertoyConsole 窗口中（红色边框）。

**Q: 如何查看调试日志？**  
A: 
1. IDEA日志：`Help` → `Show Log in Finder/Explorer`，搜索"Shadertoy"
2. 浏览器日志：右键点击渲染窗口 → "Open DevTools" → Console标签

---

**文档维护者**: 项目团队  
**文档版本**: 2.0.0  
**最后更新**: 2025-11-18  
**插件版本**: 0.0.1

### 更新历史

- **2.0.0** (2025-11-18): 重大更新，反映实际实现架构
  - 更新架构图为当前实现
  - 更新核心模块代码为实际代码
  - 添加VirtualFileSystem文件读取说明
  - 添加DumbService处理说明
  - 更新实施步骤进度
  - 添加使用指南和Quick Start
  
- **1.0.0** (2025-11-17): 初始设计文档

