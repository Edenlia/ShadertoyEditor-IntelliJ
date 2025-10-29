package com.github.edenlia.shadertoyeditor.model

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

