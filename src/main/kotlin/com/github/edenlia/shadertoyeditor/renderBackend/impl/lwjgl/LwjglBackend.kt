package com.github.edenlia.shadertoyeditor.renderBackend.impl.lwjgl

import com.github.edenlia.shadertoyeditor.renderBackend.RenderBackend
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.*

/**
 * LWJGL3 Native OpenGL 渲染后端
 *
 * 使用LWJGL3进行原生OpenGL渲染，可以达到高帧率（120fps+）
 * 
 * 技术架构：
 * - GLContext: 管理OpenGL上下文和FBO
 * - ShaderCompiler: 编译shader
 * - ShadertoyUniforms: 管理uniforms
 * - RenderLoop: 独立线程渲染循环
 *
 * @param project 当前项目实例
 */
class LwjglBackend(private val project: Project) : RenderBackend {

    private val renderPanel: JPanel
    private val imageLabel: JLabel
    private val statusLabel: JLabel

    // LWJGL组件（延迟初始化）
    private var glContext: GLContext? = null
    private var renderLoop: RenderLoop? = null

    // 当前shader program
    private var currentProgram: Int = 0
    
    // 目标分辨率
    private var targetWidth = 1280
    private var targetHeight = 720
    
    // 初始化状态
    @Volatile
    private var initialized = false

    init {
        println("[LWJGL] Creating LWJGL Backend...")
        
        // 创建UI组件
        imageLabel = JLabel().apply {
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
        }
        
        statusLabel = JLabel("Initializing LWJGL...", SwingConstants.CENTER).apply {
            foreground = Color.WHITE
        }
        
        renderPanel = JPanel(BorderLayout()).apply {
            background = Color.BLACK
            add(imageLabel, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }
        
        // GLFW必须在主线程（EDT）初始化
        // 延迟到EDT线程初始化
        SwingUtilities.invokeLater {
            try {
                initializeGL()
                statusLabel.text = "LWJGL Backend Ready - Waiting for shader..."
                
            } catch (e: Exception) {
                println("[LWJGL] Initialization failed: ${e.message}")
                e.printStackTrace()
                statusLabel.text = "LWJGL initialization failed: ${e.message}"
                statusLabel.foreground = Color.RED
            }
        }
    }

    /**
     * 初始化OpenGL上下文和渲染循环
     * 注意：必须在EDT线程调用
     */
    private fun initializeGL() {
        println("[LWJGL] Initializing OpenGL...")
        
        // 1. 创建OpenGL上下文（在EDT线程）
        glContext = GLContext(targetWidth, targetHeight).apply {
            initialize()
        }
        
        // 2. 创建渲染循环
        renderLoop = RenderLoop(glContext!!) { image ->
            // 回调：更新UI
            imageLabel.icon = ImageIcon(image)
        }
        
        // 3. 启动渲染循环
        renderLoop!!.start()
        
        initialized = true
        println("[LWJGL] OpenGL initialized successfully")
    }

    override fun getComponent(): JComponent = renderPanel

    override fun loadShader(fragmentShaderSource: String) {
        if (!initialized) {
            println("[LWJGL] Cannot load shader: not initialized yet")
            return
        }
        
        println("[LWJGL] Requesting shader compilation...")
        
        // 更新状态
        SwingUtilities.invokeLater {
            statusLabel.text = "Compiling shader..."
        }
        
        // 请求渲染循环编译shader
        // shader编译必须在有OpenGL上下文的线程（渲染线程）进行
        renderLoop!!.requestShaderCompilation(fragmentShaderSource, 
            onSuccess = { program, uniforms ->
                currentProgram = program
                SwingUtilities.invokeLater {
                    statusLabel.text = "Shader running - ${targetWidth}x${targetHeight}"
                    statusLabel.foreground = Color.GREEN
                }
                println("[LWJGL] Shader loaded successfully")
            },
            onError = { error ->
                SwingUtilities.invokeLater {
                    statusLabel.text = "Shader compilation failed"
                    statusLabel.foreground = Color.RED
                    
                    // 显示错误对话框
                    JOptionPane.showMessageDialog(
                        renderPanel,
                        error,
                        "Shader Compilation Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
                println("[LWJGL] Shader compilation failed: $error")
            }
        )
    }

    override fun setResolution(width: Int, height: Int) {
        if (!initialized) {
            println("[LWJGL] Cannot set resolution: not initialized yet")
            // 保存目标分辨率，等初始化完成后应用
            targetWidth = width
            targetHeight = height
            return
        }
        
        println("[LWJGL] Setting resolution to ${width}x${height}")
        targetWidth = width
        targetHeight = height
        
        // FBO调整必须在有OpenGL上下文的线程进行
        // 可以在EDT（初始化线程）或渲染线程
        // 为简单起见，在EDT线程进行（因为窗口在EDT创建）
        SwingUtilities.invokeLater {
            try {
                // 临时激活上下文
                glContext?.makeContextCurrent()
                glContext?.resize(width, height)
                
                statusLabel.text = "Shader running - ${width}x${height}"
                println("[LWJGL] Resolution updated")
                
            } catch (e: Exception) {
                println("[LWJGL] Failed to resize: ${e.message}")
                e.printStackTrace()
                statusLabel.text = "Failed to resize: ${e.message}"
                statusLabel.foreground = Color.RED
            }
        }
    }

    override fun dispose() {
        println("[LWJGL] Disposing LWJGL Backend...")
        
        // 停止渲染循环
        renderLoop?.stop()
        
        // 删除shader program
        if (currentProgram != 0) {
            // TODO: 在OpenGL线程中删除
            // glDeleteProgram(currentProgram)
        }
        
        // 销毁OpenGL上下文
        glContext?.destroy()
        
        println("[LWJGL] LWJGL Backend disposed")
    }
}
