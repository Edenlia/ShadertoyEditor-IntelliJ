package com.github.edenlia.shadertoyeditor.renderBackend

/**
 * 纹理数据接口
 * 
 * 抽象表示一张纹理，只包含原始像素数据（比特流）
 * 不涉及文件路径或加载逻辑，保持接口的纯粹性
 */
interface Texture {
    /**
     * 纹理的原始像素数据（RGBA格式，每个像素4字节）
     * 数据格式：从左到右、从上到下，每个像素为 [R, G, B, A]
     * 总长度必须等于 width * height * 4
     */
    val pixelData: ByteArray
    
    /**
     * 纹理宽度（像素）
     */
    val width: Int
    
    /**
     * 纹理高度（像素）
     */
    val height: Int
}

/**
 * 默认黑色纹理（1x1，RGBA(0,0,0,255)）
 * 当channel没有设置texture时使用
 */
object DefaultBlackTexture : Texture {
    override val pixelData: ByteArray = byteArrayOf(0, 0, 0, 255.toByte())  // 1x1黑色，完全不透明
    override val width: Int = 1
    override val height: Int = 1
}

