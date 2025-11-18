package com.github.edenlia.shadertoyeditor.renderBackend.impl.lwjgl

import org.lwjgl.opengl.GL20.*
import java.util.Calendar

/**
 * Shadertoy Uniforms管理器
 * 
 * 负责管理和更新所有Shadertoy标准uniforms：
 * - iResolution: vec3 - viewport分辨率 (width, height, pixel aspect ratio)
 * - iTime: float - shader运行时间（秒）
 * - iTimeDelta: float - 上一帧的渲染时间（秒）
 * - iFrame: int - 帧计数器
 * - iMouse: vec4 - 鼠标位置 (暂不支持)
 * - iDate: vec4 - (year, month, day, time in seconds)
 */
class ShadertoyUniforms(private val program: Int) {
    
    // Uniform locations
    private val locations = mutableMapOf<String, Int>()
    
    // 时间追踪
    private val startTime = System.nanoTime()
    private var lastFrameTime = startTime
    private var frameCount = 0
    
    init {
        // 获取所有uniform locations
        locations["iResolution"] = glGetUniformLocation(program, "iResolution")
        locations["iTime"] = glGetUniformLocation(program, "iTime")
        locations["iTimeDelta"] = glGetUniformLocation(program, "iTimeDelta")
        locations["iFrame"] = glGetUniformLocation(program, "iFrame")
        locations["iMouse"] = glGetUniformLocation(program, "iMouse")
        locations["iDate"] = glGetUniformLocation(program, "iDate")
        
        // 打印找到的uniforms
        println("[LWJGL] Uniforms found:")
        locations.forEach { (name, location) ->
            if (location != -1) {
                println("[LWJGL]   - $name: $location")
            }
        }
        
        // 重置计时器
        reset()
    }
    
    /**
     * 重置时间和帧计数器
     * 在加载新shader后调用
     */
    fun reset() {
        val now = System.nanoTime()
        lastFrameTime = now
        frameCount = 0
        println("[LWJGL] Uniforms reset")
    }
    
    /**
     * 更新所有uniforms
     * 在每帧渲染前调用
     * 
     * @param width 渲染目标宽度
     * @param height 渲染目标高度
     */
    fun update(width: Int, height: Int) {
        val now = System.nanoTime()
        
        // 计算时间（秒）
        val time = (now - startTime) / 1_000_000_000.0
        val timeDelta = (now - lastFrameTime) / 1_000_000_000.0
        lastFrameTime = now
        
        // 更新 iResolution (vec3: width, height, pixel aspect ratio)
        val loc = locations["iResolution"]
        if (loc != null && loc != -1) {
            glUniform3f(loc, width.toFloat(), height.toFloat(), 1.0f)
        }
        
        // 更新 iTime (float: seconds since start)
        val timeL = locations["iTime"]
        if (timeL != null && timeL != -1) {
            glUniform1f(timeL, time.toFloat())
        }
        
        // 更新 iTimeDelta (float: seconds since last frame)
        val deltaL = locations["iTimeDelta"]
        if (deltaL != null && deltaL != -1) {
            glUniform1f(deltaL, timeDelta.toFloat())
        }
        
        // 更新 iFrame (int: frame counter)
        val frameL = locations["iFrame"]
        if (frameL != null && frameL != -1) {
            glUniform1i(frameL, frameCount)
        }
        
        // 更新 iMouse (vec4: mouse coords - 暂时设为0)
        val mouseL = locations["iMouse"]
        if (mouseL != null && mouseL != -1) {
            glUniform4f(mouseL, 0f, 0f, 0f, 0f)
        }
        
        // 更新 iDate (vec4: year, month, day, seconds of day)
        val dateL = locations["iDate"]
        if (dateL != null && dateL != -1) {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR).toFloat()
            val month = (calendar.get(Calendar.MONTH) + 1).toFloat()
            val day = calendar.get(Calendar.DAY_OF_MONTH).toFloat()
            val secondsOfDay = (
                calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
                calendar.get(Calendar.MINUTE) * 60 +
                calendar.get(Calendar.SECOND)
            ).toFloat()
            
            glUniform4f(dateL, year, month, day, secondsOfDay)
        }
        
        frameCount++
    }
}

