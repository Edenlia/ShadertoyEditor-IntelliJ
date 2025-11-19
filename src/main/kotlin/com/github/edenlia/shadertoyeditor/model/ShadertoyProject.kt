package com.github.edenlia.shadertoyeditor.model

/**
 * Shadertoy项目数据模型
 * 
 * 代表一个Shadertoy项目，包含shader文件的集合
 * 
 * @property name 项目名称（用户自定义，必须唯一）
 * @property path 相对于IDE项目根目录的路径
 */
data class ShadertoyProject(
    var name: String = "",
    var path: String = ""
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
    
    override fun toString(): String {
        return name
    }
}

