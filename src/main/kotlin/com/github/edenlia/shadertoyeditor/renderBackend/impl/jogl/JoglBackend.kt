package com.github.edenlia.shadertoyeditor.renderBackend.impl.jogl

import com.github.edenlia.shadertoyeditor.listeners.STE_IDEAppEventListener
import com.github.edenlia.shadertoyeditor.listeners.STE_IDEProjectEventListener
import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.github.edenlia.shadertoyeditor.renderBackend.DefaultBlackTexture
import com.github.edenlia.shadertoyeditor.renderBackend.RenderBackend
import com.github.edenlia.shadertoyeditor.renderBackend.Texture
import com.github.edenlia.shadertoyeditor.services.GlobalEnvService
import com.github.edenlia.shadertoyeditor.services.GlobalEnvService.Platform.LINUX
import com.github.edenlia.shadertoyeditor.services.GlobalEnvService.Platform.MACOS
import com.github.edenlia.shadertoyeditor.services.GlobalEnvService.Platform.UNKNOWN
import com.github.edenlia.shadertoyeditor.services.GlobalEnvService.Platform.WINDOWS
import com.github.edenlia.shadertoyeditor.renderBackend.TexturePathResolver
import com.github.edenlia.shadertoyeditor.services.RenderBackendService
import com.github.edenlia.shadertoyeditor.settings.ShadertoySettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.JBColor
import com.intellij.util.messages.MessageBusConnection
import com.jogamp.opengl.*
import com.jogamp.opengl.awt.GLCanvas
import com.jogamp.opengl.util.AnimatorBase
import com.jogamp.opengl.util.FPSAnimator
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.Calendar
import javax.swing.*
import kotlin.math.max

/**
 * JOGL OpenGL 渲染后端
 *
 * 使用JOGL (Java OpenGL) + GLCanvas进行原生OpenGL渲染
 *
 * 优点：
 * - 无线程限制（不需要GLFW）
 * - 原生AWT/Swing集成
 * - 支持所有平台（macOS/Windows/Linux）
 * - 高性能（无限帧率）
 *
 * @param project 当前项目实例
 */
class JoglBackend(private val project: Project) : RenderBackend, GLEventListener {

    private val rootPanel: JPanel
    private val renderPanel: JPanel
    private val statusLabel: JLabel
    private var glCanvas: GLCanvas? = null
    private var animator: FPSAnimator? = null

    // Shader相关
    private var shaderProgram: Int = 0
    private var quadVAO: Int = 0
    private var quadVBO: Int = 0

    // Uniform locations
    private val uniformLocations = mutableMapOf<String, Int>()

    // 时间跟踪
    private val startTime = System.nanoTime()
    private var lastFrameTime = startTime
    private var frameCount = 0

    // 真实渲染分辨率（动态计算得出，逻辑像素）
    private var realCanvasWidth = 1
    private var realCanvasHeight = 1
    
    // 物理像素分辨率（考虑DPI缩放）
    private var physicalCanvasWidth = 1
    private var physicalCanvasHeight = 1
    
    // 外部容器尺寸（由 onContainerResized 更新）
    private var containerWidth = 1
    private var containerHeight = 1

    @Volatile
    private var initialized = false

    @Volatile
    private var shaderCompiled = false
    
    // 渲染控制
    @Volatile
    private var renderingEnabled = true

    // Texture相关
    private val channelTextures = IntArray(4) { 0 }  // OpenGL texture IDs，0表示未创建
    private var defaultTextureId: Int = 0  // 默认1x1黑色texture的ID
    
    // 同步锁（用于线程安全）
    private val textureUpdateLock = Any()
    private val pendingTextureUpdates = mutableListOf<Pair<Int, Texture?>>()  // channelIndex -> texture
    
    // 当前激活的Shadertoy项目
    @Volatile
    private var currentProject: ShadertoyProject? = null

    private val messageBusConnection: MessageBusConnection

    init {
        thisLogger().info("[JOGL] Creating JOGL Backend...")

        // 订阅参考分辨率变更事件（Settings 修改时）
        messageBusConnection = ApplicationManager.getApplication().messageBus.connect(this)

        // 创建状态标签
        statusLabel = JLabel("Initializing JOGL...", SwingConstants.CENTER).apply {
            foreground = Color.WHITE
        }

        calculateRealCanvasResolution()

        // 创建面板
        rootPanel = JPanel(GridBagLayout()).apply {
            background = Color.BLACK
        }

        renderPanel = JPanel(BorderLayout())
        renderPanel.preferredSize = Dimension(realCanvasWidth, realCanvasHeight)

        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            weighty = 1.0
            anchor = GridBagConstraints.CENTER
            fill = GridBagConstraints.NONE
        }

        rootPanel.add(renderPanel, gbc)

//        rootPanel = JPanel(BorderLayout()).apply {
//            background = Color.BLACK
////            add(renderPanel, gbc)
//            add(statusLabel, BorderLayout.WEST)
//        }

        subscribeToRefCanvasResolutionChanges()
        subscribeToShadertoyProjectChanges()
        subscribeToApplicationFocus()

        // 在EDT初始化JOGL
        SwingUtilities.invokeLater {
            initializeJOGL()
        }
    }

    /**
     * 订阅分辨率变更事件
     * @return MessageBusConnection 连接对象，用于后续手动断开
     */
    private fun subscribeToRefCanvasResolutionChanges() {
        messageBusConnection.subscribe(STE_IDEAppEventListener.TOPIC, object : STE_IDEAppEventListener {
            override fun onRefCanvasResolutionChanged(width: Int, height: Int) {
                // 在UI线程中更新分辨率
                SwingUtilities.invokeLater {
                    updateRefCanvasResolution(width, height)
                }
            }
        })
    }

    /**
     * 订阅项目切换事件和texture变更事件
     */
    private fun subscribeToShadertoyProjectChanges() {
        messageBusConnection.subscribe(
            STE_IDEProjectEventListener.TOPIC,
            object : STE_IDEProjectEventListener {
                override fun onShadertoyProjectChanged(shadertoyProject: ShadertoyProject?) {
                    if (shadertoyProject == null) {
                        // 清空渲染 - 显示空白
                        clearRender()
                        // 清除所有texture
                        clearAllChannels()
                        thisLogger().info("[JoglBackend] Project cleared, showing blank")
                    } else {
                        thisLogger().info("[JoglBackend] Project changed to: ${shadertoyProject.name}")
                        // 记录当前项目
                        currentProject = shadertoyProject
                        // 加载项目的所有textures
                        loadProjectTextures(shadertoyProject)
                    }
                }
                
                override fun onTextureChannelChanged(
                    shadertoyProject: ShadertoyProject,
                    channelIndex: Int,
                    texturePath: String?
                ) {
                    // 只有当变更的是当前激活项目时才加载texture
                    if (shadertoyProject == currentProject) {
                        loadChannelTexture(channelIndex, texturePath)
                    } else {
                        thisLogger().info("[JoglBackend] Texture changed for non-active project, ignoring")
                    }
                }
            }
        )
    }

    private fun subscribeToApplicationFocus() {
        messageBusConnection.subscribe(
            com.intellij.openapi.application.ApplicationActivationListener.TOPIC,
            object : com.intellij.openapi.application.ApplicationActivationListener {
                override fun applicationActivated(ideFrame: IdeFrame) {
                    val settings = com.intellij.openapi.components.service<ShadertoySettings>()
                    setFPSLimit(settings.getConfig().fpsLimit.toInt())
                }

                override fun applicationDeactivated(ideFrame: IdeFrame) {
                    setFPSLimit(10)
                }
            }
        )
    }

    /**
     * 清空渲染内容
     */
    private fun clearRender() {
        // 加载一个空shader，显示深灰色背景
        val emptyShader = """
                #version 330 core
                precision highp float;
                out vec4 fragColor;
                
                void main() {
                    fragColor = vec4(0.15, 0.15, 0.15, 1.0); // 深灰色背景
                }
            """.trimIndent()

        try {
            loadShader(emptyShader)
        } catch (e: Exception) {
            thisLogger().warn("[ShadertoyOutputWindow] Failed to load empty shader", e)
        }
    }



    /**
     * 初始化JOGL和GLCanvas
     */
    private fun initializeJOGL() {
        try {
            thisLogger().info("[JOGL] Initializing JOGL on thread: ${Thread.currentThread().name}")

            // 获取OpenGL配置
            val glProfile = GLProfile.get(GLProfile.GL3)
            val glCapabilities = GLCapabilities(glProfile).apply {
                doubleBuffered = true
                hardwareAccelerated = true
            }

            // 创建GLCanvas
            glCanvas = GLCanvas(glCapabilities).apply {
                addGLEventListener(this@JoglBackend)
                preferredSize = java.awt.Dimension(realCanvasWidth, realCanvasHeight)
            }

            // 添加到面板
            renderPanel.add(glCanvas!!, BorderLayout.CENTER)
            renderPanel.revalidate()

            val settings = com.intellij.openapi.components.service<ShadertoySettings>()

            // 创建动画器（无限帧率）
            animator = FPSAnimator(glCanvas, settings.getConfig().fpsLimit, true).apply {
                if (renderingEnabled) {
                    start()
                }
            }

            initialized = true
            statusLabel.text = "JOGL Backend Ready - Waiting for shader..."

        } catch (e: Exception) {
            thisLogger().error("[JOGL] Initialization failed", e)
            statusLabel.text = "JOGL initialization failed: ${e.message}"
            statusLabel.foreground = Color.RED
        }
    }

    // ===== GLEventListener 实现 =====

    override fun init(drawable: GLAutoDrawable) {
        val gl = drawable.gl.gL3

        thisLogger().info("[JOGL] ========== OpenGL Context Initialized ==========")
        thisLogger().info("[JOGL] OpenGL Version: ${gl.glGetString(GL.GL_VERSION)}")
        thisLogger().info("[JOGL] OpenGL Vendor: ${gl.glGetString(GL.GL_VENDOR)}")
        thisLogger().info("[JOGL] OpenGL Renderer: ${gl.glGetString(GL.GL_RENDERER)}")
        thisLogger().info("[JOGL] GLSL Version: ${gl.glGetString(GL3.GL_SHADING_LANGUAGE_VERSION)}")

        val config = com.intellij.openapi.components.service<ShadertoySettings>().getConfig()
        updateRefCanvasResolution(config.canvasRefWidth, config.canvasRefHeight)

        // 创建fullscreen quad
        createQuad(gl)
        
        // 创建默认黑色texture（1x1）
        defaultTextureId = createTexture(gl, DefaultBlackTexture)
        
        // 初始化所有channel为默认texture
        for (i in 0 until 4) {
            channelTextures[i] = defaultTextureId
        }
        thisLogger().info("[JOGL] Default texture created and assigned to all channels")
        
        // 初始化 viewport（处理高DPI）
        physicalCanvasWidth     =   getPhysicalCanvasWidth(drawable, glCanvas)
        physicalCanvasHeight    =   getPhysicalCanvasHeight(drawable, glCanvas)
        gl.glViewport(0, 0, physicalCanvasWidth, physicalCanvasHeight)
        thisLogger().info("[JOGL] Initial viewport set to ${physicalCanvasWidth}x${physicalCanvasHeight} (logical: ${drawable.surfaceWidth}x${drawable.surfaceHeight}")
        
        thisLogger().info("[JOGL] Initialization complete. Waiting for shader via loadShader()...")
        thisLogger().info("[JOGL] ================================================")
    }

    override fun display(drawable: GLAutoDrawable) {
        val gl = drawable.gl.gL3

        // 如果没有shader，显示深灰色背景（表示等待shader）
        if (!shaderCompiled || shaderProgram == 0) {
            gl.glClearColor(0.2f, 0.2f, 0.2f, 1f)  // 深灰色
            gl.glClear(GL.GL_COLOR_BUFFER_BIT)
            return
        }

        // 清屏为黑色（正常渲染）
        gl.glClearColor(0f, 0f, 0f, 1f)
        gl.glClear(GL.GL_COLOR_BUFFER_BIT)

        // 使用shader
        gl.glUseProgram(shaderProgram)

        // 处理待处理的texture更新
        processPendingTextureUpdates(gl)

        // 绑定textures到对应的texture units
        bindTextures(gl)

        // 更新uniforms
        updateUniforms(gl)

        // 绘制quad
        gl.glBindVertexArray(quadVAO)
        gl.glDrawArrays(GL.GL_TRIANGLE_STRIP, 0, 4)
        gl.glBindVertexArray(0)

        frameCount++
    }


    override fun reshape(drawable: GLAutoDrawable, x: Int, y: Int, width: Int, height: Int) {
        val gl = drawable.gl.gL3

        // 计算物理像素尺寸（考虑DPI缩放）
        physicalCanvasWidth     =   getPhysicalCanvasWidth(drawable, glCanvas)
        physicalCanvasHeight    =   getPhysicalCanvasHeight(drawable, glCanvas)

        gl.glViewport(0, 0, physicalCanvasWidth, physicalCanvasHeight)
    }

    override fun dispose(drawable: GLAutoDrawable) {
        val gl = drawable.gl.gL3

        // 清理OpenGL资源
        if (shaderProgram != 0) {
            gl.glDeleteProgram(shaderProgram)
        }
        if (quadVAO != 0) {
            gl.glDeleteVertexArrays(1, intArrayOf(quadVAO), 0)
        }
        if (quadVBO != 0) {
            gl.glDeleteBuffers(1, intArrayOf(quadVBO), 0)
        }
        
        // 删除所有channel textures（除了默认texture，它在dispose时统一删除）
        for (i in 0 until 4) {
            if (channelTextures[i] != 0 && channelTextures[i] != defaultTextureId) {
                gl.glDeleteTextures(1, intArrayOf(channelTextures[i]), 0)
            }
        }
        
        // 删除默认texture
        if (defaultTextureId != 0) {
            gl.glDeleteTextures(1, intArrayOf(defaultTextureId), 0)
        }

        thisLogger().info("[JOGL] OpenGL resources cleaned up")
    }

    // ===== RenderBackend 接口实现 =====

    override fun getRootComponent(): JComponent = rootPanel

    override fun loadShader(fragmentShaderSource: String) {
        if (!initialized) {
            thisLogger().warn("[JOGL] Cannot load shader: not initialized")
            return
        }

        thisLogger().info("[JOGL] Loading shader...")
        statusLabel.text = "Compiling shader..."

        // 在GLCanvas的上下文中编译shader
        glCanvas?.invoke(false) { drawable ->
            val gl = drawable.gl.gL3

            try {
                // 删除旧program
                if (shaderProgram != 0) {
                    gl.glDeleteProgram(shaderProgram)
                }

                // 编译新shader
                shaderProgram = compileShaderProgram(gl, fragmentShaderSource)

                // 获取uniform locations
                getUniformLocations(gl)

                // 重置时间
                lastFrameTime = System.nanoTime()
                frameCount = 0

                shaderCompiled = true

                SwingUtilities.invokeLater {
                    statusLabel.text = "Shader running - ${realCanvasWidth}x${realCanvasHeight}"
                    statusLabel.foreground = JBColor.GREEN
                }

                thisLogger().info("[JOGL] Shader loaded successfully")

            } catch (e: Exception) {
                shaderCompiled = false
                thisLogger().error("[JOGL] Shader compilation failed", e)

                SwingUtilities.invokeLater {
                    statusLabel.text = "Shader compilation failed"
                    statusLabel.foreground = JBColor.RED

                    JOptionPane.showMessageDialog(
                        renderPanel,
                        e.message,
                        "Shader Compilation Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }

            true  // 返回true表示需要重绘
        }
    }

    override fun updateRefCanvasResolution(width: Int, height: Int) {
        SwingUtilities.invokeLater {
            calculateRealCanvasResolution()
            
            renderPanel.preferredSize = Dimension(realCanvasWidth, realCanvasHeight)
            renderPanel.revalidate()
            renderPanel.repaint()
            
            statusLabel.text = "Ref resolution: ${width}x${height}, Real canvas: ${realCanvasWidth}x${realCanvasHeight}"
            thisLogger().info("[JOGL] Ref resolution updated to ${width}x${height}, calculated real canvas: ${realCanvasWidth}x${realCanvasHeight}")
        }
    }
    
    override fun onContainerResized(width: Int, height: Int) {
        containerWidth = width
        containerHeight = height
        
        thisLogger().info("[JOGL] Container resized to ${width}x${height}")
        
        // 重新计算并应用分辨率
        val config = com.intellij.openapi.components.service<ShadertoySettings>().getConfig()
        updateRefCanvasResolution(config.canvasRefWidth, config.canvasRefHeight)
    }
    
    override fun enableRendering(enable: Boolean) {
        renderingEnabled = enable
        
        if (enable) {
            animator?.start()
            thisLogger().info("[JOGL] Rendering enabled")
        } else {
            animator?.stop()
            thisLogger().info("[JOGL] Rendering disabled")
        }
    }
    
    override fun setFPSLimit(fps: Int) {
        if (!initialized || glCanvas == null) {
            thisLogger().info("[JOGL] FPS limit set to ${if (fps == 0) "unlimited" else fps} (will apply after initialization)")
            return
        }

        SwingUtilities.invokeLater {
            animator?.stop()
            animator?.fps = fps
            animator?.start()

            thisLogger().info("[JOGL] FPS limit updated to ${if (fps == 0) "unlimited" else fps}")
            statusLabel.text = "FPS: ${if (fps == 0) "unlimited" else fps} | ${statusLabel.text}"
        }
    }

    private fun calculateRealCanvasResolution()
    {
        val config = com.intellij.openapi.components.service<ShadertoySettings>().getConfig()
        val refCanvasWidth = config.canvasRefWidth
        val refCanvasHeight = config.canvasRefHeight

        val toolWindowWidth = max(containerWidth, 1)
        val toolWindowHeight = max(containerHeight, 1)

        val refAspect = refCanvasWidth.toFloat() / refCanvasHeight.toFloat()
        val windowAspect = toolWindowWidth.toFloat() / toolWindowHeight.toFloat()

        if (windowAspect > refAspect) {
            // 窗口更宽，高度受限
            realCanvasHeight = toolWindowHeight
            realCanvasWidth = (toolWindowHeight * refAspect).toInt()
        } else {
            // 窗口更高，宽度受限
            realCanvasWidth = toolWindowWidth
            realCanvasHeight = (toolWindowWidth / refAspect).toInt()
        }
    }

    override fun setChannelTexture(channelIndex: Int, texture: Texture?) {
        require(channelIndex in 0..3) { "Channel index must be in range 0-3, got $channelIndex" }
        
        // 验证texture数据（如果提供）
        texture?.let {
            require(it.width > 0 && it.height > 0) { "Texture dimensions must be positive" }
            require(it.pixelData.size == it.width * it.height * 4) {
                "Invalid texture data: expected ${it.width * it.height * 4} bytes, got ${it.pixelData.size}"
            }
        }
        
        // 线程安全：将更新请求加入队列
        synchronized(textureUpdateLock) {
            pendingTextureUpdates.add(channelIndex to texture)
        }
        
        // 在OpenGL上下文中执行更新
        glCanvas?.invoke(false) { drawable ->
            processPendingTextureUpdates(drawable.gl.gL3)
            true  // 触发重绘
        }
    }

    override fun clearAllChannels() {
        for (i in 0 until 4) {
            setChannelTexture(i, null)
        }
    }

    override fun dispose() {
        thisLogger().info("[JOGL] Disposing JOGL Backend...")

        animator?.stop()
        animator = null

        glCanvas?.destroy()
        glCanvas = null

        thisLogger().info("[JOGL] JOGL Backend disposed")
    }

    // ===== 私有辅助方法 =====

    /**
     * 创建fullscreen quad
     */
    private fun createQuad(gl: GL3) {
        val vertices = floatArrayOf(
            -1f, -1f,  // 左下
             1f, -1f,  // 右下
            -1f,  1f,  // 左上
             1f,  1f   // 右上
        )

        val vaoArray = IntArray(1)
        val vboArray = IntArray(1)

        gl.glGenVertexArrays(1, vaoArray, 0)
        gl.glGenBuffers(1, vboArray, 0)

        quadVAO = vaoArray[0]
        quadVBO = vboArray[0]

        gl.glBindVertexArray(quadVAO)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, quadVBO)
        gl.glBufferData(
            GL.GL_ARRAY_BUFFER,
            vertices.size * 4L,
            java.nio.FloatBuffer.wrap(vertices),
            GL.GL_STATIC_DRAW
        )

        gl.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 0, 0)
        gl.glEnableVertexAttribArray(0)

        gl.glBindVertexArray(0)

        thisLogger().info("[JOGL] Quad created")
    }

    /**
     * 编译shader program
     */
    private fun compileShaderProgram(gl: GL3, fragmentShaderSource: String): Int {
        // Vertex shader
        val vertexShader = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """.trimIndent()

        // 编译shaders
        val vs = compileShader(gl, GL3.GL_VERTEX_SHADER, vertexShader)
        val fs = compileShader(gl, GL3.GL_FRAGMENT_SHADER, fragmentShaderSource)

        // 创建program
        val program = gl.glCreateProgram()
        gl.glAttachShader(program, vs)
        gl.glAttachShader(program, fs)
        gl.glLinkProgram(program)

        // 检查链接状态
        val linkStatus = IntArray(1)
        gl.glGetProgramiv(program, GL3.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == GL.GL_FALSE) {
            val log = getProgramLog(gl, program)
            gl.glDeleteProgram(program)
            gl.glDeleteShader(vs)
            gl.glDeleteShader(fs)
            throw RuntimeException("Shader linking failed:\n$log")
        }

        // 清理shaders
        gl.glDeleteShader(vs)
        gl.glDeleteShader(fs)

        return program
    }

    /**
     * 编译单个shader
     */
    private fun compileShader(gl: GL3, type: Int, source: String): Int {
        val shader = gl.glCreateShader(type)
        gl.glShaderSource(shader, 1, arrayOf(source), null)
        gl.glCompileShader(shader)

        val compileStatus = IntArray(1)
        gl.glGetShaderiv(shader, GL3.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == GL.GL_FALSE) {
            val log = getShaderLog(gl, shader)
            gl.glDeleteShader(shader)
            val typeName = if (type == GL3.GL_VERTEX_SHADER) "Vertex" else "Fragment"
            throw RuntimeException("$typeName shader compilation failed:\n$log")
        }

        return shader
    }

    /**
     * 获取shader编译日志
     */
    private fun getShaderLog(gl: GL3, shader: Int): String {
        val logLength = IntArray(1)
        gl.glGetShaderiv(shader, GL3.GL_INFO_LOG_LENGTH, logLength, 0)
        if (logLength[0] == 0) return ""

        val log = ByteArray(logLength[0])
        gl.glGetShaderInfoLog(shader, logLength[0], null, 0, log, 0)
        return String(log)
    }

    /**
     * 获取program链接日志
     */
    private fun getProgramLog(gl: GL3, program: Int): String {
        val logLength = IntArray(1)
        gl.glGetProgramiv(program, GL3.GL_INFO_LOG_LENGTH, logLength, 0)
        if (logLength[0] == 0) return ""

        val log = ByteArray(logLength[0])
        gl.glGetProgramInfoLog(program, logLength[0], null, 0, log, 0)
        return String(log)
    }

    /**
     * 获取uniform locations
     */
    private fun getUniformLocations(gl: GL3) {
        uniformLocations.clear()
        uniformLocations["iResolution"] = gl.glGetUniformLocation(shaderProgram, "iResolution")
        uniformLocations["iTime"] = gl.glGetUniformLocation(shaderProgram, "iTime")
        uniformLocations["iTimeDelta"] = gl.glGetUniformLocation(shaderProgram, "iTimeDelta")
        uniformLocations["iFrame"] = gl.glGetUniformLocation(shaderProgram, "iFrame")
        uniformLocations["iMouse"] = gl.glGetUniformLocation(shaderProgram, "iMouse")
        uniformLocations["iDate"] = gl.glGetUniformLocation(shaderProgram, "iDate")
        
        // Texture samplers
        uniformLocations["iChannel0"] = gl.glGetUniformLocation(shaderProgram, "iChannel0")
        uniformLocations["iChannel1"] = gl.glGetUniformLocation(shaderProgram, "iChannel1")
        uniformLocations["iChannel2"] = gl.glGetUniformLocation(shaderProgram, "iChannel2")
        uniformLocations["iChannel3"] = gl.glGetUniformLocation(shaderProgram, "iChannel3")

        thisLogger().info("[JOGL] Uniforms located: ${uniformLocations.filter { it.value != -1 }.keys}")
    }

    private fun getPhysicalCanvasWidth(drawable: GLAutoDrawable, canvas: GLCanvas?): Int {
        val globalEnv = com.intellij.openapi.components.service<GlobalEnvService>()

        val physicalScaleX = when (globalEnv.currentPlatform) {
            WINDOWS -> glCanvas?.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
            MACOS -> 1.0
            LINUX -> 1.0
            UNKNOWN -> 1.0
        }

        return (drawable.surfaceWidth * physicalScaleX).toInt()
    }

    private fun getPhysicalCanvasHeight(drawable: GLAutoDrawable, canvas: GLCanvas?): Int {
        val globalEnv = com.intellij.openapi.components.service<GlobalEnvService>()

        val physicalScaleY = when (globalEnv.currentPlatform) {
            WINDOWS -> glCanvas?.graphicsConfiguration?.defaultTransform?.scaleY ?: 1.0
            MACOS -> 1.0
            LINUX -> 1.0
            UNKNOWN -> 1.0
        }

        return (drawable.surfaceHeight * physicalScaleY).toInt()
    }

    /**
     * 更新uniforms
     */
    private fun updateUniforms(gl: GL3) {
        val now = System.nanoTime()
        val time = (now - startTime) / 1_000_000_000.0f
        val timeDelta = (now - lastFrameTime) / 1_000_000_000.0f
        lastFrameTime = now

        // iResolution - 使用真实渲染分辨率
        uniformLocations["iResolution"]?.let { loc ->
            if (loc != -1) {
                gl.glUniform3f(loc, physicalCanvasWidth.toFloat(), physicalCanvasHeight.toFloat(), 1.0f)
            }
        }

        // iTime
        uniformLocations["iTime"]?.let { loc ->
            if (loc != -1) gl.glUniform1f(loc, time)
        }

        // iTimeDelta
        uniformLocations["iTimeDelta"]?.let { loc ->
            if (loc != -1) gl.glUniform1f(loc, timeDelta)
        }

        // iFrame
        uniformLocations["iFrame"]?.let { loc ->
            if (loc != -1) gl.glUniform1i(loc, frameCount)
        }

        // iMouse
        uniformLocations["iMouse"]?.let { loc ->
            if (loc != -1) gl.glUniform4f(loc, 0f, 0f, 0f, 0f)
        }

        // iDate
        uniformLocations["iDate"]?.let { loc ->
            if (loc != -1) {
                val calendar = Calendar.getInstance()
                val year = calendar.get(Calendar.YEAR).toFloat()
                val month = (calendar.get(Calendar.MONTH) + 1).toFloat()
                val day = calendar.get(Calendar.DAY_OF_MONTH).toFloat()
                val secondsOfDay = (
                    calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
                    calendar.get(Calendar.MINUTE) * 60 +
                    calendar.get(Calendar.SECOND)
                ).toFloat()
                gl.glUniform4f(loc, year, month, day, secondsOfDay)
            }
        }
    }
    
    /**
     * 绑定textures到对应的texture units
     */
    private fun bindTextures(gl: GL3) {
        gl.glActiveTexture(GL.GL_TEXTURE0)
        gl.glBindTexture(GL.GL_TEXTURE_2D, channelTextures[0])
        uniformLocations["iChannel0"]?.let { if (it != -1) gl.glUniform1i(it, 0) }
        
        gl.glActiveTexture(GL.GL_TEXTURE1)
        gl.glBindTexture(GL.GL_TEXTURE_2D, channelTextures[1])
        uniformLocations["iChannel1"]?.let { if (it != -1) gl.glUniform1i(it, 1) }
        
        gl.glActiveTexture(GL.GL_TEXTURE2)
        gl.glBindTexture(GL.GL_TEXTURE_2D, channelTextures[2])
        uniformLocations["iChannel2"]?.let { if (it != -1) gl.glUniform1i(it, 2) }
        
        gl.glActiveTexture(GL.GL_TEXTURE3)
        gl.glBindTexture(GL.GL_TEXTURE_2D, channelTextures[3])
        uniformLocations["iChannel3"]?.let { if (it != -1) gl.glUniform1i(it, 3) }
    }
    
    /**
     * 处理待处理的texture更新
     */
    private fun processPendingTextureUpdates(gl: GL3) {
        synchronized(textureUpdateLock) {
            if (pendingTextureUpdates.isEmpty()) return
            
            val updates = pendingTextureUpdates.toList()
            pendingTextureUpdates.clear()
            
            for ((channelIndex, texture) in updates) {
                updateChannelTexture(gl, channelIndex, texture)
            }
        }
    }
    
    /**
     * 更新指定channel的texture
     */
    private fun updateChannelTexture(gl: GL3, channelIndex: Int, texture: Texture?) {
        val targetTexture = texture ?: DefaultBlackTexture
        
        // 如果该channel已有texture，先删除（除非是默认texture）
        if (channelTextures[channelIndex] != 0 && channelTextures[channelIndex] != defaultTextureId) {
            gl.glDeleteTextures(1, intArrayOf(channelTextures[channelIndex]), 0)
        }
        
        // 创建新texture
        val textureId = createTexture(gl, targetTexture)
        channelTextures[channelIndex] = textureId
        
        thisLogger().info("[JOGL] Channel $channelIndex texture updated: ${targetTexture.width}x${targetTexture.height}")
    }
    
    /**
     * 创建OpenGL texture
     * 
     * @param gl GL3上下文
     * @param texture Texture数据
     * @return OpenGL texture ID
     */
    private fun createTexture(gl: GL3, texture: Texture): Int {
        val textureIds = IntArray(1)
        gl.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]
        
        gl.glBindTexture(GL.GL_TEXTURE_2D, textureId)
        
        // 设置纹理参数（Shadertoy常用设置）
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT)
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT)
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR)
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR)
        
        // 上传像素数据
        val buffer = java.nio.ByteBuffer.allocateDirect(texture.pixelData.size)
        buffer.put(texture.pixelData)
        buffer.flip()
        
        gl.glTexImage2D(
            GL.GL_TEXTURE_2D,
            0,
            GL.GL_RGBA,
            texture.width,
            texture.height,
            0,
            GL.GL_RGBA,
            GL.GL_UNSIGNED_BYTE,
            buffer
        )
        
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0)
        
        return textureId
    }
    
    /**
     * 加载项目的所有textures
     */
    private fun loadProjectTextures(shadertoyProject: ShadertoyProject) {
        thisLogger().info("[JoglBackend] Loading all textures for project: ${shadertoyProject.name}")
        
        for (i in 0 until 4) {
            val texturePath = shadertoyProject.getChannelTexture(i)
            loadChannelTexture(i, texturePath)
        }
        
        thisLogger().info("[JoglBackend] All textures loaded for project: ${shadertoyProject.name}")
    }
    
    /**
     * 加载单个channel的texture
     * 
     * @param channelIndex channel索引（0-3）
     * @param texturePath 相对路径，null表示使用默认texture
     */
    private fun loadChannelTexture(channelIndex: Int, texturePath: String?) {
        try {
            val texture = if (texturePath != null) {
                TexturePathResolver.resolveTexture(project, texturePath)
            } else {
                null  // 使用默认黑色texture
            }
            
            setChannelTexture(channelIndex, texture)
            
            if (texturePath != null) {
                thisLogger().info("[JoglBackend] Channel $channelIndex loaded: $texturePath")
            } else {
                thisLogger().info("[JoglBackend] Channel $channelIndex using default texture")
            }
        } catch (e: Exception) {
            thisLogger().warn("[JoglBackend] Failed to load texture for channel $channelIndex: $texturePath", e)
            
            // 显示错误提示
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(
                    rootPanel,
                    "Failed to load texture for iChannel$channelIndex: ${e.message}\n" +
                    "Falling back to default black texture.",
                    "Texture Load Error",
                    JOptionPane.WARNING_MESSAGE
                )
            }
            
            // 回退到默认texture
            setChannelTexture(channelIndex, null)
        }
    }
}

