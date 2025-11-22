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
     * 画布参考分辨率 - 宽度
     * 范围：64-4096
     */
    var canvasRefWidth: Int = 1280,

    /**
     * 画布参考分辨率 - 高度
     * 范围：64-4096
     */
    var canvasRefHeight: Int = 720,

    /**
     * 渲染后端类型
     * 可选值：JCEF
     * 默认：JCEF（稳定性最好）
     */
    var backendType: String = "JOGL",

    /**
     * 保存时自动编译
     * 当保存激活项目的 Image.glsl 文件时，自动触发编译
     */
    var autoCompileOnSave: Boolean = true,

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

