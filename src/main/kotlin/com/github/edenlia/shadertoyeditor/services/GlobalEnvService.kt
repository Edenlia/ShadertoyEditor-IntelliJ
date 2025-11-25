package com.github.edenlia.shadertoyeditor.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import java.util.Locale

/**
 * 全局环境服务
 * 
 * Application级别的服务，负责：
 * - 检测并缓存当前运行平台
 * - 提供平台相关的配置和信息
 * - 作为插件全局可访问的环境信息中心
 */
@Service(Service.Level.APP)
class GlobalEnvService {
    
    /**
     * 平台枚举
     */
    enum class Platform {
        WINDOWS,
        MACOS,
        LINUX,
        UNKNOWN;
    }
    
    /**
     * 当前运行平台（延迟初始化，只检测一次）
     */
    val currentPlatform: Platform by lazy {
        detectPlatform().also {
            thisLogger().info("[GlobalEnvService] Platform detected: $it")
        }
    }
    
    /**
     * 检测当前操作系统平台
     */
    private fun detectPlatform(): Platform {
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        
        return when {
            osName.contains("win") -> Platform.WINDOWS
            osName.contains("mac") -> Platform.MACOS
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> Platform.LINUX
            else -> {
                thisLogger().warn("[GlobalEnvService] Unknown platform: $osName")
                Platform.UNKNOWN
            }
        }
    }
    
    /**
     * 判断是否为Windows平台
     */
    fun isWindows(): Boolean = currentPlatform == Platform.WINDOWS
    
    /**
     * 判断是否为macOS平台
     */
    fun isMacOS(): Boolean = currentPlatform == Platform.MACOS
    
    /**
     * 判断是否为Linux平台
     */
    fun isLinux(): Boolean = currentPlatform == Platform.LINUX
}

