package com.github.edenlia.shadertoyeditor.model

/**
 * Shadertoy项目数据模型
 * 
 * 代表一个Shadertoy项目，包含shader文件的集合
 * 
 * @property name 项目名称（用户自定义，必须唯一）
 * @property path 相对于IDE项目根目录的路径
 * @property channel0Texture iChannel0的texture路径，null表示未设置（使用默认黑色texture）
 * @property channel1Texture iChannel1的texture路径
 * @property channel2Texture iChannel2的texture路径
 * @property channel3Texture iChannel3的texture路径
 * 
 * Texture路径格式：
 * - 插件资源：$plugin/resources/textures/xxx.png
 * - 本地文件：相对于项目根目录的路径（如 textures/noise.png）
 */
data class ShadertoyProject(
    var name: String = "",
    var path: String = "",
    var channel0Texture: String? = null,
    var channel1Texture: String? = null,
    var channel2Texture: String? = null,
    var channel3Texture: String? = null
) {
    /**
     * 获取项目的绝对路径
     * @param projectBasePath IDE项目根目录的绝对路径
     * @return 项目文件夹的绝对路径
     */
    fun getAbsolutePath(projectBasePath: String): String {
        return "$projectBasePath/$path"
    }
    
    /**
     * 获取Image.glsl文件的绝对路径
     * @param projectBasePath IDE项目根目录的绝对路径
     * @return Image.glsl的绝对路径
     */
    fun getImageGlslPath(projectBasePath: String): String {
        return "${getAbsolutePath(projectBasePath)}/Image.glsl"
    }
    
    /**
     * 获取指定channel的texture路径
     * @param channelIndex channel索引 (0-3，对应iChannel0-iChannel3)
     * @return texture路径，null表示未设置
     */
    fun getChannelTexture(channelIndex: Int): String? {
        return when (channelIndex) {
            0 -> channel0Texture
            1 -> channel1Texture
            2 -> channel2Texture
            3 -> channel3Texture
            else -> null
        }
    }
    
    /**
     * 设置指定channel的texture路径
     * @param channelIndex channel索引 (0-3)
     * @param texturePath texture路径，null表示清除
     */
    fun setChannelTexture(channelIndex: Int, texturePath: String?) {
        when (channelIndex) {
            0 -> channel0Texture = texturePath
            1 -> channel1Texture = texturePath
            2 -> channel2Texture = texturePath
            3 -> channel3Texture = texturePath
        }
    }
    
    override fun toString(): String {
        return name
    }
}

