package org.skepsun.kototoro.reader.translate.data

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.core.opengl.EglCore
import org.skepsun.kototoro.core.opengl.GlUtil
import org.skepsun.kototoro.core.opengl.OffscreenSurface
import java.io.File
import java.nio.ByteBuffer

class Anime4kImageEngine(private val context: Context) {
    private val TAG = "Anime4kImageEngine"

    private var eglCore: EglCore? = null
    private var surface: OffscreenSurface? = null
    private var isInitialized = false

    private val passes = mutableListOf<Anime4kPass>()
    private val programs = mutableListOf<Int>()

    private val vertexShaderCode = """
        #version 300 es
        layout(location = 0) in vec4 aPosition;
        layout(location = 1) in vec2 aTexCoord;
        out vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    suspend fun initialize(shadersDir: File, presetShaders: List<String>): Boolean = withContext(Dispatchers.Default) {
        if (isInitialized) return@withContext true

        try {
            val sources = presetShaders.map { shaderFile ->
                File(shadersDir, shaderFile).readText()
            }
            passes.addAll(Anime4kCompiler.parse(sources))

            eglCore = EglCore()
            // create a dummy 1x1 surface just to have a current context
            surface = OffscreenSurface(eglCore!!, 1, 1).apply { makeCurrent() }

            // Compile shaders
            for (pass in passes) {
                val fSource = Anime4kCompiler.compileToFragmentShader(pass)
                val prog = GlUtil.createProgram(vertexShaderCode, fSource)
                if (prog == 0) throw RuntimeException("Failed to compile pass: ${pass.desc}")
                programs.add(prog)
            }

            isInitialized = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            release()
            false
        }
    }

    suspend fun process(inBitmap: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        if (!isInitialized) return@withContext null

        surface?.makeCurrent()

        val textureSizes = mutableMapOf<String, Pair<Int, Int>>()
        textureSizes["MAIN"] = Pair(inBitmap.width, inBitmap.height)
        textureSizes["OUTPUT"] = Pair(inBitmap.width, inBitmap.height)

        val textures = mutableMapOf<String, Int>()
        val fbos = mutableMapOf<String, Int>()
        val allCreatedTextures = mutableListOf<Int>()
        val allCreatedFbos = mutableListOf<Int>()

        var currentOutputTex = 0
        var currentWidth = inBitmap.width
        var currentHeight = inBitmap.height

        try {
            val initialMainTex = GlUtil.createTexture(inBitmap)
            textures["MAIN"] = initialMainTex
            allCreatedTextures.add(initialMainTex)
            currentOutputTex = textures["MAIN"]!!

            val vboData = floatArrayOf(
                // x, y, u, v
                -1f, -1f, 0f, 1f,
                 1f, -1f, 1f, 1f,
                -1f,  1f, 0f, 0f,
                 1f,  1f, 1f, 0f
            )
            val vboBuffer = GlUtil.createFloatBuffer(vboData)

            for ((i, pass) in passes.withIndex()) {
                val prog = programs[i]
                GLES30.glUseProgram(prog)

                // Calculate pass output dimensions
                val passW = if (pass.widthExpression != null) {
                    Anime4kSizeEvaluator.evaluate(pass.widthExpression, textureSizes)
                } else textureSizes[pass.hook]?.first ?: currentWidth

                val passH = if (pass.heightExpression != null) {
                    Anime4kSizeEvaluator.evaluate(pass.heightExpression, textureSizes)
                } else textureSizes[pass.hook]?.second ?: currentHeight

                currentWidth = passW
                currentHeight = passH

                // Setup output FBO
                val saveName = pass.save
                val outTex = if (saveName != null && (!textures.containsKey(saveName) || fbos[saveName] == null)) {
                    val tex = GlUtil.createEmptyTexture(passW, passH)
                    textures[saveName] = tex
                    allCreatedTextures.add(tex)
                    textureSizes[saveName] = Pair(passW, passH)
                    val fbo = IntArray(1).also { GLES30.glGenFramebuffers(1, it, 0) }[0]
                    allCreatedFbos.add(fbo)
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
                    GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex, 0)
                    fbos[saveName] = fbo
                    tex
                } else if (saveName != null) {
                    val tex = textures[saveName]!!
                    val fbo = fbos[saveName]!!
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
                    GLES30.glViewport(0, 0, passW, passH)
                    tex
                } else {
                    // Update MAIN or default output
                    val tex = GlUtil.createEmptyTexture(passW, passH)
                    allCreatedTextures.add(tex)
                    val fbo = IntArray(1).also { GLES30.glGenFramebuffers(1, it, 0) }[0]
                    allCreatedFbos.add(fbo)
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
                    GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex, 0)
                    fbos["TEMP_$i"] = fbo
                    textures["TEMP_$i"] = tex
                    textureSizes["MAIN"] = Pair(passW, passH)
                    textures["MAIN"] = tex // Main gets updated
                    tex
                }
                
                currentOutputTex = outTex
                GLES30.glViewport(0, 0, passW, passH)

                // Bind uniforms
                var texUnit = 0
                val binds = mutableListOf("MAIN")
                binds.addAll(pass.binds)
                if (pass.hook != "MAIN") binds.add(pass.hook)

                for (bind in binds.distinct()) {
                    val location = GLES30.glGetUniformLocation(prog, bind)
                    if (location >= 0) {
                        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + texUnit)
                        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[bind] ?: textures["MAIN"]!!)
                        GLES30.glUniform1i(location, texUnit)
                        
                        val sizeLoc = GLES30.glGetUniformLocation(prog, "${bind}_size")
                        val hw = textureSizes[bind] ?: textureSizes["MAIN"]!!
                        if (sizeLoc >= 0) GLES30.glUniform2f(sizeLoc, hw.first.toFloat(), hw.second.toFloat())

                        val ptLoc = GLES30.glGetUniformLocation(prog, "${bind}_pt")
                        if (ptLoc >= 0) GLES30.glUniform2f(ptLoc, 1.0f / hw.first, 1.0f / hw.second)
                        
                        texUnit++
                    }
                }

                // Draw quad
                val posLoc = GLES30.glGetAttribLocation(prog, "aPosition")
                val texLoc = GLES30.glGetAttribLocation(prog, "aTexCoord")
                
                GLES30.glEnableVertexAttribArray(posLoc)
                GLES30.glEnableVertexAttribArray(texLoc)

                vboBuffer.position(0)
                GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 4 * 4, vboBuffer)
                vboBuffer.position(2)
                GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 4 * 4, vboBuffer)
                
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

                GLES30.glDisableVertexAttribArray(posLoc)
                GLES30.glDisableVertexAttribArray(texLoc)
            }

            // Readback
            val buffer = ByteBuffer.allocateDirect(currentWidth * currentHeight * 4)
            GLES30.glReadPixels(0, 0, currentWidth, currentHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
            
            val outBmp = Bitmap.createBitmap(currentWidth, currentHeight, Bitmap.Config.ARGB_8888)
            buffer.position(0)
            outBmp.copyPixelsFromBuffer(buffer)

            return@withContext outBmp
        } catch (e: Exception) {
            Log.e(TAG, "Process failed", e)
            return@withContext null
        } finally {
            if (allCreatedFbos.isNotEmpty()) {
                GLES30.glDeleteFramebuffers(allCreatedFbos.size, allCreatedFbos.toIntArray(), 0)
            }
            if (allCreatedTextures.isNotEmpty()) {
                GLES30.glDeleteTextures(allCreatedTextures.size, allCreatedTextures.toIntArray(), 0)
            }
        }
    }

    fun release() {
        surface?.release()
        surface = null
        for (prog in programs) {
            GLES30.glDeleteProgram(prog)
        }
        programs.clear()
        eglCore?.release()
        eglCore = null
        isInitialized = false
    }
}
