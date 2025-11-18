package com.github.edenlia.shadertoyeditor.renderBackend.impl.lwjgl

import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import javax.swing.SwingUtilities

/**
 * LWJGL渲染循环
 * 
 * 负责：
 * - 在独立线程中运行OpenGL渲染循环
 * - 绘制fullscreen quad
 * - 读取像素并转换为BufferedImage
 * - 回调到Swing EDT更新UI
 */
class RenderLoop(
    private val glContext: GLContext,
    private val onFrameReady: (BufferedImage) -> Unit
) {
    
    @Volatile
    private var running = false
    
    @Volatile
    private var currentProgram: Int = 0
    
    @Volatile
    private var currentUniforms: ShadertoyUniforms? = null
    
    private var renderThread: Thread? = null
    
    // Shader编译请求
    private data class ShaderCompilationRequest(
        val fragmentShaderSource: String,
        val onSuccess: (Int, ShadertoyUniforms) -> Unit,
        val onError: (String) -> Unit
    )
    
    @Volatile
    private var pendingShaderCompilation: ShaderCompilationRequest? = null
    
    private val shaderCompiler = ShaderCompiler()
    
    // Fullscreen quad geometry
    private val quadVAO: Int
    private val quadVBO: Int
    
    init {
        println("[LWJGL] Initializing render loop...")
        
        // 创建fullscreen quad的VAO和VBO
        // Quad覆盖整个屏幕 (-1,-1) 到 (1,1)
        val vertices = floatArrayOf(
            -1f, -1f,  // 左下
             1f, -1f,  // 右下
            -1f,  1f,  // 左上
             1f,  1f   // 右上
        )
        
        quadVAO = glGenVertexArrays()
        quadVBO = glGenBuffers()
        
        glBindVertexArray(quadVAO)
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO)
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)
        
        // 设置顶点属性 (location = 0, 2 floats per vertex)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0)
        glEnableVertexAttribArray(0)
        
        glBindVertexArray(0)
        
        println("[LWJGL] Render loop initialized")
    }
    
    /**
     * 启动渲染循环
     */
    fun start() {
        if (running) {
            println("[LWJGL] Render loop already running")
            return
        }
        
        running = true
        
        renderThread = Thread {
            println("[LWJGL] Render thread started")
            try {
                runLoop()
            } catch (e: Exception) {
                println("[LWJGL] Render thread error: ${e.message}")
                e.printStackTrace()
            } finally {
                println("[LWJGL] Render thread stopped")
            }
        }.apply {
            name = "LWJGL-RenderThread"
            isDaemon = true
            start()
        }
    }
    
    /**
     * 主渲染循环
     */
    private fun runLoop() {
        // 在渲染线程激活OpenGL上下文
        glContext.makeContextCurrent()
        
        // 必须在makeContextCurrent之后调用，为当前线程创建GL capabilities
        org.lwjgl.opengl.GL.createCapabilities()
        
        glContext.bind()
        
        while (running) {
            try {
                // 处理pending的shader编译
                processPendingShaderCompilation()
                
                // 只在有shader的情况下渲染
                if (currentProgram != 0 && currentUniforms != null) {
                    renderFrame()
                } else {
                    // 没有shader时休眠一下，避免空转
                    Thread.sleep(100)
                }
            } catch (e: Exception) {
                println("[LWJGL] Render frame error: ${e.message}")
                e.printStackTrace()
            }
        }
        
        glContext.unbind()
    }
    
    /**
     * 处理pending的shader编译请求
     */
    private fun processPendingShaderCompilation() {
        val request = pendingShaderCompilation ?: return
        pendingShaderCompilation = null
        
        try {
            println("[LWJGL] Compiling shader in render thread...")
            
            // 删除旧program
            if (currentProgram != 0) {
                glDeleteProgram(currentProgram)
            }
            
            // 编译新shader
            val program = shaderCompiler.compile(request.fragmentShaderSource)
            val uniforms = ShadertoyUniforms(program)
            
            // 更新当前状态
            currentProgram = program
            currentUniforms = uniforms
            
            // 回调成功
            request.onSuccess(program, uniforms)
            
        } catch (e: ShaderCompilationException) {
            // 回调错误
            request.onError(e.message ?: "Unknown compilation error")
        } catch (e: Exception) {
            request.onError("Unexpected error: ${e.message}")
        }
    }
    
    /**
     * 渲染单帧
     */
    private fun renderFrame() {
        // 1. 清屏
        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)
        
        // 2. 使用shader program
        glUseProgram(currentProgram)
        
        // 3. 更新uniforms
        currentUniforms?.update(glContext.width, glContext.height)
        
        // 4. 绘制fullscreen quad
        glBindVertexArray(quadVAO)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glBindVertexArray(0)
        
        // 5. 读取像素
        val pixels = glContext.readPixels()
        
        // 6. 转换为BufferedImage
        val image = pixelsToImage(pixels, glContext.width, glContext.height)
        
        // 7. 回调到Swing EDT
        SwingUtilities.invokeLater {
            onFrameReady(image)
        }
    }
    
    /**
     * 将OpenGL像素数据转换为BufferedImage
     * 
     * @param pixels ByteBuffer包含RGBA像素数据
     * @param width 图像宽度
     * @param height 图像高度
     * @return BufferedImage
     */
    private fun pixelsToImage(pixels: ByteBuffer, width: Int, height: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val data = (image.raster.dataBuffer as DataBufferInt).data
        
        // OpenGL的坐标系Y轴向上，需要翻转
        for (y in 0 until height) {
            for (x in 0 until width) {
                // 源索引：从下往上
                val srcIndex = ((height - 1 - y) * width + x) * 4
                
                // 读取RGBA分量
                val r = pixels[srcIndex].toInt() and 0xFF
                val g = pixels[srcIndex + 1].toInt() and 0xFF
                val b = pixels[srcIndex + 2].toInt() and 0xFF
                val a = pixels[srcIndex + 3].toInt() and 0xFF
                
                // 目标索引：正常顺序
                val dstIndex = y * width + x
                
                // 组合为ARGB格式
                data[dstIndex] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        
        return image
    }
    
    /**
     * 请求编译新shader
     * 线程安全 - 可从任何线程调用
     * 
     * @param fragmentShaderSource Fragment shader源代码
     * @param onSuccess 编译成功回调
     * @param onError 编译失败回调
     */
    fun requestShaderCompilation(
        fragmentShaderSource: String,
        onSuccess: (Int, ShadertoyUniforms) -> Unit,
        onError: (String) -> Unit
    ) {
        pendingShaderCompilation = ShaderCompilationRequest(
            fragmentShaderSource,
            onSuccess,
            onError
        )
    }
    
    /**
     * 停止渲染循环
     */
    fun stop() {
        if (!running) return
        
        println("[LWJGL] Stopping render loop...")
        running = false
        renderThread?.join(5000) // 等待最多5秒
        
        // 清理VAO和VBO
        glDeleteVertexArrays(quadVAO)
        glDeleteBuffers(quadVBO)
        
        println("[LWJGL] Render loop stopped")
    }
}

