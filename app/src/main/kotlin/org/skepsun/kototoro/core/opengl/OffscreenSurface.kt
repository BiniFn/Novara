package org.skepsun.kototoro.core.opengl

import android.opengl.EGLSurface

class OffscreenSurface(private val eglCore: EglCore, val width: Int, val height: Int) {
    private var eglSurface: EGLSurface = eglCore.createOffscreenSurface(width, height)

    fun makeCurrent() {
        eglCore.makeCurrent(eglSurface)
    }

    fun release() {
        eglCore.releaseSurface(eglSurface)
    }
}
