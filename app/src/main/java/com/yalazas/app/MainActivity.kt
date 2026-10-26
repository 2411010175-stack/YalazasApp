package com.yalazas.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.yalazas.app.databinding.ActivityMainBinding

/** Aloja la navegación y coordina las barras inferiores según el rol. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar iconos de la barra de estado en blanco (tema oscuro)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        // Cada grupo usa una barra inferior distinta; login y formularios ocupan toda la pantalla.
        val destinosAdmin = setOf(
            R.id.nav_admin_dashboard,
            R.id.nav_admin_inventory,
            R.id.nav_admin_users,
            R.id.nav_admin_sales
        )

        val destinosReparto = setOf(
            R.id.nav_delivery_orders,
            R.id.nav_delivery_map,
            R.id.nav_delivery_history,
            R.id.nav_delivery_profile
        )

        val destinosPantallaCompleta = setOf(
            R.id.nav_login,
            R.id.nav_register,
            R.id.nav_admin_panel,
            R.id.nav_admin_settings
        )

        var destinoActualId = R.id.nav_home
        var tecladoVisible = false

        // La barra se oculta también al abrir el teclado para no tapar los campos de entrada.
        fun actualizarVisibilidadNavegacion() {
            if (tecladoVisible) {
                // Cuando el teclado está abierto (ej. buscando pedidos, usuarios, productos),
                // ocultamos la barra de navegación para que el teclado se superponga directamente
                // y no empuje la barra hacia arriba ocupando espacio visual
                binding.navContainer.visibility = View.GONE
                binding.bottomNav.visibility = View.GONE
                binding.adminBottomNav.visibility = View.GONE
                binding.deliveryBottomNav.visibility = View.GONE
            } else {
                when (destinoActualId) {
                    in destinosPantallaCompleta -> {
                        binding.navContainer.visibility = View.GONE
                        binding.bottomNav.visibility = View.GONE
                        binding.adminBottomNav.visibility = View.GONE
                        binding.deliveryBottomNav.visibility = View.GONE
                    }
                    in destinosAdmin -> {
                        binding.navContainer.visibility = View.VISIBLE
                        binding.bottomNav.visibility = View.GONE
                        binding.adminBottomNav.visibility = View.VISIBLE
                        binding.deliveryBottomNav.visibility = View.GONE
                    }
                    in destinosReparto -> {
                        binding.navContainer.visibility = View.VISIBLE
                        binding.bottomNav.visibility = View.GONE
                        binding.adminBottomNav.visibility = View.GONE
                        binding.deliveryBottomNav.visibility = View.VISIBLE
                    }
                    else -> {
                        binding.navContainer.visibility = View.VISIBLE
                        binding.bottomNav.visibility = View.VISIBLE
                        binding.adminBottomNav.visibility = View.GONE
                        binding.deliveryBottomNav.visibility = View.GONE
                    }
                }
            }
        }

        // Aplicar insets seguros: padding superior para notch, padding inferior para teclado o barra gestual
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val statusBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )
            val imeInsets = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )
            val isImeOpen = insets.isVisible(WindowInsetsCompat.Type.ime()) || imeInsets.bottom > 0

            tecladoVisible = isImeOpen
            actualizarVisibilidadNavegacion()

            if (isImeOpen) {
                // Con teclado visible: fragmentContainer tiene padding inferior igual a la altura del teclado
                binding.fragmentContainer.setPadding(0, statusBarInsets.top, 0, imeInsets.bottom)
            } else {
                // Sin teclado: ajustamos padding según visibilidad del contenedor de navegación
                val isNavVisible = destinoActualId !in destinosPantallaCompleta
                val bottomPadding = if (isNavVisible) 0 else navBarInsets.bottom
                binding.fragmentContainer.setPadding(0, statusBarInsets.top, 0, bottomPadding)
                binding.navContainer.setPadding(0, 0, 0, navBarInsets.bottom)
            }

            insets
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)
        binding.adminBottomNav.setupWithNavController(navController)
        binding.deliveryBottomNav.setupWithNavController(navController)

        // Badge en la pestaña de Pedidos del Administrador
        val badge = binding.adminBottomNav.getOrCreateBadge(R.id.nav_admin_sales)
        badge.backgroundColor = ContextCompat.getColor(this, R.color.admin_nav_badge)
        badge.badgeTextColor = ContextCompat.getColor(this, R.color.white)
        badge.number = 0
        badge.isVisible = false

        // Badge en la pestaña de Entregas del Repartidor
        val deliveryBadge = binding.deliveryBottomNav.getOrCreateBadge(R.id.nav_delivery_orders)
        deliveryBadge.backgroundColor = ContextCompat.getColor(this, R.color.yalaza_pink)
        deliveryBadge.badgeTextColor = ContextCompat.getColor(this, R.color.white)
        deliveryBadge.number = 0
        deliveryBadge.isVisible = false

        // Observar cantidad de productos en el carrito para el badge del cliente
        com.yalazas.app.repositorio.RepositorioCarrito.items.observe(this) { items ->
            val count = items.sumOf { it.quantity }
            val cartBadge = binding.bottomNav.getOrCreateBadge(R.id.nav_cart)
            cartBadge.backgroundColor = ContextCompat.getColor(this, R.color.yalaza_pink)
            cartBadge.badgeTextColor = ContextCompat.getColor(this, R.color.white)
            if (count > 0) {
                cartBadge.number = count
                cartBadge.isVisible = true
            } else {
                cartBadge.isVisible = false
            }
        }

        // Mantiene la barra correcta al cambiar de pantalla mediante el gráfico de navegación.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            destinoActualId = destination.id
            actualizarVisibilidadNavegacion()
        }
    }

    /**
     * Dispara la animación Fly-to-Cart desde la vista clickeada hacia el icono de carrito en la barra de navegación
     */
    fun animarAgregadoAlCarrito(fromView: View, onEnd: (() -> Unit)? = null) {
        val cartItemView = binding.bottomNav.findViewById<View>(R.id.nav_cart) ?: binding.bottomNav
        com.yalazas.app.utilidades.AnimadorCarrito.animarHaciaCarrito(this, fromView, cartItemView, onEnd)
    }

    fun abrirWhatsAppConMensaje(message: String) {
        val phone = getString(R.string.whatsapp_phone_e164)
        val url = "https://wa.me/$phone?text=${Uri.encode(message)}"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    fun irAlMenu() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as NavHostFragment
        navHostFragment.navController.navigate(R.id.nav_menu)
    }

    fun abrirInstagram() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, "https://www.instagram.com/yalazas_pe/".toUri()))
        } catch (_: ActivityNotFoundException) {
        }
    }

    fun abrirMapa() {
        val lat = -13.061851
        val lng = -76.352075
        val label = Uri.encode("Yalaza - Av Ramos 309")
        val uri = "geo:$lat,$lng?q=$lat,$lng($label)".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val webUri = "https://www.google.com/maps/search/?api=1&query=$lat,$lng".toUri()
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }
}
