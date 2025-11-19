package com.github.edenlia.shadertoyeditor.model

/**
 * Shadertoy项目配置文件数据模型
 * 
 * 对应.shadertoy-editor.config.yml文件的结构
 * 用于持久化存储所有Shadertoy项目的元数据
 */
data class ShadertoyProjectConfig(
    /**
     * 所有Shadertoy项目列表
     */
    var projects: MutableList<ShadertoyProject> = mutableListOf()
)

