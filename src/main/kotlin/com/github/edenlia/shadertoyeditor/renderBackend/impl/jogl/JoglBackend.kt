package com.github.edenlia.shadertoyeditor.renderBackend.impl.jogl

import com.github.edenlia.shadertoyeditor.renderBackend.RenderBackend
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.jogamp.opengl.*
import com.jogamp.opengl.awt.GLCanvas
import com.jogamp.opengl.util.FPSAnimator
import java.awt.BorderLayout
import java.awt.Color
import java.util.Calendar
import javax.swing.*

/**
 * JOGL OpenGL 渲染后端
 *
 * 使用JOGL (Java OpenGL) + GLCanvas进行原生OpenGL渲染
 *
 * 优点：
 * - 无线程限制（不需要GLFW）
 * - 原生AWT/Swing集成
 * - 支持所有平台（macOS/Windows/Linux）
 * - 高性能（120fps+）
 *
 * @param project 当前项目实例
 */
class JoglBackend(private val project: Project) : RenderBackend, GLEventListener {

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

    // 分辨率
    private var targetWidth = 1280
    private var targetHeight = 720

    @Volatile
    private var initialized = false

    @Volatile
    private var shaderCompiled = false

    init {
        thisLogger().info("[JOGL] Creating JOGL Backend...")

        // 创建状态标签
        statusLabel = JLabel("Initializing JOGL...", SwingConstants.CENTER).apply {
            foreground = Color.WHITE
        }

        // 创建面板
        renderPanel = JPanel(BorderLayout()).apply {
            background = Color.BLACK
            add(statusLabel, BorderLayout.SOUTH)
        }

        // 在EDT初始化JOGL
        SwingUtilities.invokeLater {
            initializeJOGL()
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
                preferredSize = java.awt.Dimension(targetWidth, targetHeight)
            }

            // 添加到面板
            renderPanel.add(glCanvas!!, BorderLayout.CENTER)
            renderPanel.revalidate()

            // 创建动画器（无限制帧率，设为0表示最快）
            animator = FPSAnimator(glCanvas, 0, true).apply {
                start()
            }

            initialized = true
            statusLabel.text = "JOGL Backend Ready - Waiting for shader..."
            thisLogger().info("[JOGL] JOGL initialized successfully")

        } catch (e: Exception) {
            thisLogger().error("[JOGL] Initialization failed", e)
            statusLabel.text = "JOGL initialization failed: ${e.message}"
            statusLabel.foreground = Color.RED
        }
    }

    // ===== GLEventListener 实现 =====

    override fun init(drawable: GLAutoDrawable) {
        val gl = drawable.gl.gL3

        thisLogger().info("[JOGL] OpenGL Context initialized")
        thisLogger().info("[JOGL] OpenGL Version: ${gl.glGetString(GL.GL_VERSION)}")
        thisLogger().info("[JOGL] OpenGL Vendor: ${gl.glGetString(GL.GL_VENDOR)}")
        thisLogger().info("[JOGL] OpenGL Renderer: ${gl.glGetString(GL.GL_RENDERER)}")

        // 创建fullscreen quad
        createQuad(gl)
    }

    override fun display(drawable: GLAutoDrawable) {
        if (!shaderCompiled || shaderProgram == 0) return

        val gl = drawable.gl.gL3

        // 清屏
        gl.glClearColor(0f, 0f, 0f, 1f)
        gl.glClear(GL.GL_COLOR_BUFFER_BIT)

        // 使用shader
        gl.glUseProgram(shaderProgram)

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
        gl.glViewport(0, 0, width, height)
        thisLogger().info("[JOGL] Viewport resized to ${width}x${height}")
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

        thisLogger().info("[JOGL] OpenGL resources cleaned up")
    }

    // ===== RenderBackend 接口实现 =====

    override fun getComponent(): JComponent = renderPanel

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
                    statusLabel.text = "Shader running - ${targetWidth}x${targetHeight}"
                    statusLabel.foreground = Color.GREEN
                }

                thisLogger().info("[JOGL] Shader loaded successfully")

            } catch (e: Exception) {
                shaderCompiled = false
                thisLogger().error("[JOGL] Shader compilation failed", e)

                SwingUtilities.invokeLater {
                    statusLabel.text = "Shader compilation failed"
                    statusLabel.foreground = Color.RED

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

    override fun setResolution(width: Int, height: Int) {
        targetWidth = width
        targetHeight = height

        SwingUtilities.invokeLater {
            glCanvas?.preferredSize = java.awt.Dimension(width, height)
            glCanvas?.size = java.awt.Dimension(width, height)
            renderPanel.revalidate()

            statusLabel.text = "Shader running - ${width}x${height}"
            thisLogger().info("[JOGL] Resolution set to ${width}x${height}")
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

        thisLogger().info("[JOGL] Uniforms located: ${uniformLocations.filter { it.value != -1 }.keys}")
    }

    /**
     * 更新uniforms
     */
    private fun updateUniforms(gl: GL3) {
        val now = System.nanoTime()
        val time = (now - startTime) / 1_000_000_000.0f
        val timeDelta = (now - lastFrameTime) / 1_000_000_000.0f
        lastFrameTime = now

        // iResolution
        uniformLocations["iResolution"]?.let { loc ->
            if (loc != -1) {
                gl.glUniform3f(loc, targetWidth.toFloat(), targetHeight.toFloat(), 1.0f)
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
}

