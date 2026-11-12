package com.yalazas.app.utilidades

import org.junit.Assert.assertEquals
import org.junit.Test

class ImagenCatalogoTest {
    @Test fun `la tarjeta solicita dimensiones pequeñas al CDN`() {
        val origen = "https://res.cloudinary.com/yalazas/image/upload/v123/alitas%20bbq.jpg"
        assertEquals(
            "https://res.cloudinary.com/yalazas/image/upload/c_fill,w_320,h_320,q_auto,f_auto/v123/alitas%20bbq.jpg",
            ImagenCatalogo.miniatura(origen, 320, 320)
        )
    }

    @Test fun `no cambia una imagen ajena a Cloudinary`() {
        val origen = "https://ejemplo.com/foto.jpg"
        assertEquals(origen, ImagenCatalogo.miniatura(origen, 320, 320))
    }
}
