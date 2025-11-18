package com.github.edenlia.shadertoyeditor.renderBackend.impl.lwjgl

import com.intellij.openapi.diagnostic.thisLogger
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.BufferUtils
import java.nio.ByteBuffer

/**
 * OpenGL上下文管理器
 * 
 * 负责：
 * - 初始化GLFW和OpenGL
 * - 创建offscreen渲染上下文（隐藏窗口）
 * - 管理FBO (Framebuffer Object)
 * - 提供像素读取功能
 */
class GLContext(initialWidth: Int, initialHeight: Int) {
    
    private var window: Long = NULL
    private var fbo: Int = 0
    private var colorTexture: Int = 0
    
    var width: Int = initialWidth
        private set
    var height: Int = initialHeight
        private set
    
    /**
     * 初始化GLFW和OpenGL上下文
     * 注意：这个方法必须在主线程调用！
     */
    fun initialize() {
        thisLogger().info("[LWJGL] Initializing OpenGL context on thread: ${Thread.currentThread().name}")
        
        // 检查是否在EDT线程（主线程）
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            thisLogger().warn("[LWJGL] initialize() called from non-EDT thread")
        }
        
        // 1. 初始化GLFW（必须在主线程）
        if (!glfwInit()) {
            throw RuntimeException("Failed to initialize GLFW")
        }
        
        // 2. 配置OpenGL版本和profile
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        
        // macOS特殊配置
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("mac")) {
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
            thisLogger().info("[LWJGL] Detected macOS, enabled forward compatibility")
        }
        
        // 3. 创建不可见窗口（用于offscreen渲染）
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
        
        window = glfwCreateWindow(1, 1, "LWJGL Offscreen Context", NULL, NULL)
        if (window == NULL) {
            glfwTerminate()
            throw RuntimeException("Failed to create GLFW window")
        }
        
        // 4. 在当前线程激活OpenGL上下文
        glfwMakeContextCurrent(window)
        GL.createCapabilities()
        
        println("[LWJGL] OpenGL Version: ${glGetString(GL_VERSION)}")
        println("[LWJGL] OpenGL Vendor: ${glGetString(GL_VENDOR)}")
        println("[LWJGL] OpenGL Renderer: ${glGetString(GL_RENDERER)}")
        
        // 5. 创建FBO
        createFramebuffer()
        
        // 6. 解除上下文绑定，让渲染线程可以使用
        glfwMakeContextCurrent(NULL)
        
        println("[LWJGL] OpenGL context initialized successfully (${width}x${height})")
    }
    
    /**
     * 激活当前线程的OpenGL上下文
     * 在渲染线程使用OpenGL前必须调用
     */
    fun makeContextCurrent() {
        glfwMakeContextCurrent(window)
    }
    
    /**
     * 创建Framebuffer Object
     */
    private fun createFramebuffer() {
        // 生成FBO
        fbo = glGenFramebuffers()
        
        // 生成颜色纹理
        colorTexture = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, colorTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as ByteBuffer?)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        
        // 绑定FBO并附加纹理
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture, 0)
        
        // 检查FBO状态
        val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer is not complete: $status")
        }
        
        // 解绑
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glBindTexture(GL_TEXTURE_2D, 0)
    }
    
    /**
     * 调整FBO大小
     */
    fun resize(newWidth: Int, newHeight: Int) {
        if (newWidth == width && newHeight == height) return
        
        println("[LWJGL] Resizing FBO from ${width}x${height} to ${newWidth}x${newHeight}")
        
        width = newWidth
        height = newHeight
        
        // 删除旧纹理
        glDeleteTextures(colorTexture)
        
        // 重新创建纹理
        colorTexture = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, colorTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as ByteBuffer?)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        
        // 重新附加到FBO
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture, 0)
        
        val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer is not complete after resize: $status")
        }
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glBindTexture(GL_TEXTURE_2D, 0)
    }
    
    /**
     * 绑定FBO作为渲染目标
     */
    fun bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        glViewport(0, 0, width, height)
    }
    
    /**
     * 解绑FBO
     */
    fun unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }
    
    /**
     * 从FBO读取像素数据
     * 
     * @return ByteBuffer 包含RGBA像素数据
     */
    fun readPixels(): ByteBuffer {
        val pixels = BufferUtils.createByteBuffer(width * height * 4)
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
        return pixels
    }
    
    /**
     * 释放所有OpenGL资源
     */
    fun destroy() {
        println("[LWJGL] Destroying OpenGL context...")
        
        // 删除FBO和纹理
        if (fbo != 0) glDeleteFramebuffers(fbo)
        if (colorTexture != 0) glDeleteTextures(colorTexture)
        
        // 销毁窗口和GLFW
        if (window != NULL) {
            glfwDestroyWindow(window)
        }
        glfwTerminate()
        
        println("[LWJGL] OpenGL context destroyed")
    }
}

