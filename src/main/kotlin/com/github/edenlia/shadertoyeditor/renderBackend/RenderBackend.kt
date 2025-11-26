package com.github.edenlia.shadertoyeditor.renderBackend

import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
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
 * - 控制渲染行为（开关、帧率）
 */
interface RenderBackend : Disposable {
    
    /**
     * 获取渲染视图的根组件
     * 
     * 此组件可以被添加到 ToolWindow 或其他容器中
     * Backend 创建后，此组件可以被反复 add/remove 而不影响 Backend 状态
     * 
     * @return JComponent 渲染视图组件
     */
    fun getRootComponent(): JComponent

    fun compileShader(shaderSource: String)
    
    /**
     * 加载并编译Shader代码
     * 
     * @param fragmentShaderSource 完整的Fragment Shader源代码
     * @throws Exception 如果编译失败
     */
    fun loadShader(fragmentShaderSource: String)
    
    /**
     * 更新参考Canvas分辨率
     * 
     * 根据设置中的参考分辨率和实际容器尺寸，计算真实渲染分辨率
     * 
     * @param width 参考宽度（像素）
     * @param height 参考高度（像素）
     */
    fun updateRefCanvasResolution(width: Int, height: Int)
    
    /**
     * 通知Backend外部容器尺寸发生变化
     * 
     * 当 ToolWindow 或其他容器的尺寸改变时，由外部主动调用此方法
     * Backend 会根据新的容器尺寸重新计算渲染分辨率
     * 
     * @param width 容器宽度（像素）
     * @param height 容器高度（像素）
     */
    fun onContainerResized(width: Int, height: Int)
    
    /**
     * 启用或禁用渲染
     * 
     * 当 ToolWindow 关闭时应调用 enableRendering(false) 以节省 CPU
     * 当 ToolWindow 打开时调用 enableRendering(true) 恢复渲染
     * 
     * @param enable true=启用渲染，false=禁用渲染
     */
    fun enableRendering(enable: Boolean)
    
    /**
     * 设置帧率限制
     * 
     * @param fps 目标帧率（帧/秒），0 表示无限帧率（尽可能快地渲染）
     */
    fun setFPSLimit(fps: Int)
    
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

    fun loadProjectTextures(shadertoyProject: ShadertoyProject?)
}

