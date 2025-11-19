package com.github.edenlia.shadertoyeditor.renderBackend

import com.intellij.openapi.Disposable
import javax.swing.JComponent

/**
 * 渲染后端接口
 * 
 * 定义了Shadertoy渲染器的核心功能：
 * - 提供Swing组件用于显示
 * - 加载和编译Shader代码
 * - 更新渲染参数
 */
interface RenderBackend : Disposable {
    
    /**
     * 获取可嵌入Swing容器的渲染组件
     * 
     * @return JComponent 渲染视图组件
     */
    fun getComponent(): JComponent

    fun getOuterComponent(): JComponent
    
    /**
     * 加载并编译Shader代码
     * 
     * @param fragmentShaderSource 完整的Fragment Shader源代码
     * @throws Exception 如果编译失败
     */
    fun loadShader(fragmentShaderSource: String)
    
    /**
     * 更新settings中设置的ref canvas的大小
     * 
     * @param width 目标宽度（像素）
     * @param height 目标高度（像素）
     */
    fun updateRefCanvasResolution(width: Int, height: Int)

    fun updateOuterResolution(width: Int, height: Int)
}

