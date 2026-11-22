package com.retroplay.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import com.retroplay.RetroPlayApplication
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** Garante que a Coil (feita para OkHttp 4) funciona com o OkHttp 5 do app e reduz a imagem. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverLoadingTest {

    @Test
    fun carrega_capa_pela_rede_reduzida_ao_tamanho_do_card() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val png = ByteArrayOutputStream().also {
            Bitmap.createBitmap(512, 720, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body(Buffer().write(png)).addHeader("Content-Type", "image/png").build())
            val loader = RetroPlayApplication.buildImageLoader(context, context.cacheDir) { OkHttpClient() }
            val result = loader.execute(
                ImageRequest.Builder(context).data(server.url("/Named_Boxarts/Jogo.png").toString()).size(Size(128, 180)).build(),
            )
            assertTrue("falhou: $result", result is SuccessResult)
            val image = (result as SuccessResult).image
            // A Coil decodifica já no tamanho do card (nunca a imagem inteira).
            assertTrue(image.width <= 256 && image.height <= 360)
            assertEquals("/Named_Boxarts/Jogo.png", server.takeRequest().url.encodedPath)
        }
    }
}
