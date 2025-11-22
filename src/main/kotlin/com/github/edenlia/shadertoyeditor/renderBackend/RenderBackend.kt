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
 * - 管理Texture通道
 */
interface RenderBackend : Disposable {
    
    /**
     * 直接嵌在ToolWindow Component中的根Component
     * 
     * @return JComponent 渲染视图组件
     */
    fun getRootComponent(): JComponent

    /**
     * ToolWindow的Component
     *
     * @return JComponent 渲染视图组件
     */
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
    
    /**
     * 设置指定channel的texture
     * 
     * 线程安全：此方法可以在任何线程调用，内部会确保在OpenGL上下文中执行
     * 
     * @param channelIndex channel索引 (0-3，对应iChannel0-iChannel3)
     * @param texture 要设置的texture，null表示清除该channel（使用默认黑色texture）
     * @throws IllegalArgumentException 如果channelIndex不在0-3范围内
     * @throws IllegalStateException 如果texture数据无效（尺寸不匹配等）
     */
    fun setChannelTexture(channelIndex: Int, texture: Texture?)
    
    /**
     * 清除所有channel的texture（恢复到默认黑色texture）
     */
    fun clearAllChannels()
}

