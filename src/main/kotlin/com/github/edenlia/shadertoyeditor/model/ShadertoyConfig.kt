package com.github.edenlia.shadertoyeditor.model
import com.jogamp.opengl.GL

/**
 * Shadertoy Editor 配置数据模型
 * 存储用户的配置信息
 */
data class ShadertoyConfig(
    /**
     * 用户名
     */
    var username: String = "",
    
    /**
     * 密码
     */
    var password: String = "",
    
    /**
     * 目标渲染分辨率 - 宽度
     * 范围：64-4096
     */
    var targetWidth: Int = 1280,
    
    /**
     * 目标渲染分辨率 - 高度
     * 范围：64-4096
     */
    var targetHeight: Int = 720,
    
    /**
     * 渲染后端类型
     * 可选值：JCEF, LWJGL
     * 默认：JCEF（稳定性最好）
     */
    var backendType: String = "JOGL",
    
    /**
     * 配置版本号（用于未来升级）
     */
    var version: Int = 1
) {
    /**
     * 克隆配置对象
     */
    fun clone(): ShadertoyConfig {
        return this.copy()
    }
    
    /**
     * 检查是否被修改（与另一个配置比较）
     */
    fun isModified(other: ShadertoyConfig): Boolean {
        return this != other
    }
}

