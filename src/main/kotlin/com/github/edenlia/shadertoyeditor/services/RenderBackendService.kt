package com.github.edenlia.shadertoyeditor.services

import com.github.edenlia.shadertoyeditor.renderBackend.RenderBackend
import com.github.edenlia.shadertoyeditor.renderBackend.impl.jogl.JoglBackend
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

/**
 * 渲染后端服务
 * 
 * Project 级别的 Service，负责管理 RenderBackend 的生命周期
 * - 懒加载：首次调用 getBackend() 时创建
 * - 单例：每个 Project 一个 Backend 实例
 * - 持久化：Backend 在 Project 关闭前一直存活，ToolWindow 关闭不影响
 */
@Service(Service.Level.PROJECT)
class RenderBackendService(private val project: Project) : com.intellij.openapi.Disposable {
    
    private var backend: RenderBackend? = null
    
    /**
     * 获取 RenderBackend 实例（懒加载）
     * 
     * @return RenderBackend 实例
     */
    fun getBackend(): RenderBackend {
        if (backend == null) {
            thisLogger().info("[RenderBackendService] Creating JoglBackend for project: ${project.name}")
            backend = JoglBackend(project)
        }
        return backend!!
    }
    
    /**
     * 释放 Backend 资源（Project 关闭时自动调用）
     */
    override fun dispose() {
        backend?.let {
            thisLogger().info("[RenderBackendService] Disposing backend for project: ${project.name}")
            it.dispose()
        }
        backend = null
    }
    
    companion object {
        /**
         * 获取 Project 的 RenderBackendService 实例
         */
        fun getInstance(project: Project): RenderBackendService {
            return project.getService(RenderBackendService::class.java)
        }
    }
}

