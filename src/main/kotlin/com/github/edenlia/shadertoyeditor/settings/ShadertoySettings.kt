package com.github.edenlia.shadertoyeditor.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import com.github.edenlia.shadertoyeditor.model.ShadertoyConfig

/**
 * Shadertoy 配置持久化服务
 * 负责配置的保存和加载
 * 
 * 配置会自动保存到: .idea/shadertoy-editor.xml
 */
@State(
    name = "ShadertoySettings",
    storages = [Storage("shadertoy-editor.xml")]
)
@Service(Service.Level.APP)
class ShadertoySettings : PersistentStateComponent<ShadertoySettings> {
    
    /**
     * 存储配置对象
     */
    private var config: ShadertoyConfig = ShadertoyConfig()
    
    /**
     * 获取当前配置（供外部使用）
     */
    fun getConfig(): ShadertoyConfig {
        return config
    }
    
    /**
     * 设置配置（供 Settings UI 保存时调用）
     */
    fun setConfig(config: ShadertoyConfig) {
        this.config = config
    }
    
    /**
     * 获取状态（IntelliJ Platform 调用）
     */
    override fun getState(): ShadertoySettings {
        return this
    }
    
    /**
     * 加载状态（IntelliJ Platform 调用）
     */
    override fun loadState(state: ShadertoySettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}

