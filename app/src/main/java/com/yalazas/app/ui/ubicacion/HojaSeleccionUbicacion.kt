package com.yalazas.app.ui.ubicacion

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.yalazas.app.R
import com.yalazas.app.repositorio.RepositorioAutenticacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale

/** Permite elegir una dirección y coordenadas en el mapa. */
class HojaSeleccionUbicacion : BottomSheetDialogFragment() {

    private var mapView: MapView? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var tvDetectedAddress: TextView
    private lateinit var tvCoordinates: TextView
    private lateinit var etReference: TextInputEditText
    private lateinit var etMapSearch: EditText
    private lateinit var btnMapSearchAction: AppCompatImageButton
    private lateinit var btnConfirm: MaterialButton
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var fabZoomIn: FloatingActionButton
    private lateinit var fabZoomOut: FloatingActionButton
    private lateinit var pbGeocoding: ProgressBar
    private lateinit var ivCenterPin: AppCompatImageView
    private lateinit var layoutCenterPin: View

    private var currentTargetLat: Double = -13.061851
    private var currentTargetLng: Double = -76.352075
    private var currentFormattedAddress: String = ""
    // Se cancela la búsqueda anterior si el usuario mueve el mapa de nuevo.
    private var geocodeJob: Job? = null
    private var isPinLifted: Boolean = false

    // Coordenadas iniciales por defecto (Cañete, Lima / Zona Yalaza)
    private val defaultLat = -13.061851
    private val defaultLng = -76.352075

    var initialAddress: String = ""
    var initialReference: String = ""
    var initialLat: Double = 0.0
    var initialLng: Double = 0.0

    var onLocationConfirmed: ((address: String, reference: String, lat: Double, lng: Double) -> Unit)? = null

    // El permiso GPS es opcional: el cliente puede elegir su dirección moviendo el mapa.
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            moverAUbicacionActual()
        } else {
            Toast.makeText(
                requireContext(),
                "Permiso de ubicación no otorgado. Puedes mover el mapa manualmente.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Configurar OSMDroid User Agent y caché local
        val ctx = requireContext().applicationContext
        Configuration.getInstance().userAgentValue = ctx.packageName
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("yalazas_osm_prefs", Context.MODE_PRIVATE))

        val root = inflater.inflate(R.layout.bottom_sheet_location_picker, container, false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        mapView = root.findViewById(R.id.mapView)
        tvDetectedAddress = root.findViewById(R.id.tvSheetDetectedAddress)
        tvCoordinates = root.findViewById(R.id.tvSheetCoordinates)
        etReference = root.findViewById(R.id.etSheetLocationReference)
        etMapSearch = root.findViewById(R.id.etMapSearchAddress)
        btnMapSearchAction = root.findViewById(R.id.btnMapSearchAction)
        btnConfirm = root.findViewById(R.id.btnConfirmDeliveryLocation)
        fabMyLocation = root.findViewById(R.id.fabMyLocation)
        fabZoomIn = root.findViewById(R.id.fabZoomIn)
        fabZoomOut = root.findViewById(R.id.fabZoomOut)
        pbGeocoding = root.findViewById(R.id.pbMapGeocoding)
        ivCenterPin = root.findViewById(R.id.ivMapCenterPin)
        layoutCenterPin = root.findViewById(R.id.layoutCenterPin)

        // Botón cerrar X
        root.findViewById<AppCompatImageButton>(R.id.btnCloseLocationSheet)?.setOnClickListener {
            dismiss()
        }

        // Zoom In / Zoom Out Controls
        fabZoomIn.setOnClickListener {
            mapView?.controller?.zoomIn()
        }
        fabZoomOut.setOnClickListener {
            mapView?.controller?.zoomOut()
        }

        // Búsqueda de dirección o calle por texto
        etMapSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                buscarDireccion(etMapSearch.text.toString())
                true
            } else {
                false
            }
        }
        btnMapSearchAction.setOnClickListener {
            buscarDireccion(etMapSearch.text.toString())
        }

        // Quick Preset Chips (Cañete & Alrededores)
        root.findViewById<MaterialButton>(R.id.chipGpsQuick)?.setOnClickListener {
            solicitarPermisoUbicacion()
        }
        root.findViewById<MaterialButton>(R.id.chipSanVicente)?.setOnClickListener {
            moverAUbicacionPredefinida(-13.0769, -76.3871, "San Vicente de Cañete")
        }
        root.findViewById<MaterialButton>(R.id.chipImperial)?.setOnClickListener {
            moverAUbicacionPredefinida(-13.0583, -76.3533, "Imperial, Cañete")
        }
        root.findViewById<MaterialButton>(R.id.chipQuilmana)?.setOnClickListener {
            moverAUbicacionPredefinida(-12.9467, -76.3817, "Quilmaná, Cañete")
        }
        root.findViewById<MaterialButton>(R.id.chipNuevoImperial)?.setOnClickListener {
            moverAUbicacionPredefinida(-13.0736, -76.3167, "Nuevo Imperial, Cañete")
        }

        // Inicializar coordenadas objetivo
        if (initialLat != 0.0 && initialLng != 0.0) {
            currentTargetLat = initialLat
            currentTargetLng = initialLng
        } else {
            currentTargetLat = defaultLat
            currentTargetLng = defaultLng
        }

        if (initialAddress.isNotBlank()) {
            currentFormattedAddress = initialAddress
            tvDetectedAddress.text = initialAddress
        } else {
            currentFormattedAddress = "Ubicación fijada en el mapa"
            tvDetectedAddress.text = "Ubicación fijada en el mapa"
        }

        val latStr = String.format(Locale.US, "%.5f", currentTargetLat)
        val lngStr = String.format(Locale.US, "%.5f", currentTargetLng)
        tvCoordinates.text = "Lat: $latStr · Lng: $lngStr"

        if (initialReference.isNotBlank()) {
            etReference.setText(initialReference)
        }

        configurarMapa()

        // Evitar que el BottomSheet intercepte los gestos táctiles sobre el mapa
        val mapContainer = root.findViewById<View>(R.id.layoutMapContainer)
        mapContainer.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        mapView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        fabMyLocation.setOnClickListener {
            solicitarPermisoUbicacion()
        }

        btnConfirm.setOnClickListener {
            val address = if (currentFormattedAddress.isNotBlank()) {
                currentFormattedAddress
            } else {
                tvDetectedAddress.text.toString().trim().ifBlank {
                    "Ubicación (${String.format(Locale.US, "%.4f", currentTargetLat)}, ${String.format(Locale.US, "%.4f", currentTargetLng)})"
                }
            }
            val reference = etReference.text?.toString()?.trim().orEmpty()

            // Guardar ubicación en el perfil del usuario en segundo plano
            viewLifecycleOwner.lifecycleScope.launch {
                RepositorioAutenticacion.actualizarUbicacionEntrega(
                    address = address,
                    reference = reference,
                    lat = currentTargetLat,
                    lng = currentTargetLng
                )
            }

            onLocationConfirmed?.invoke(address, reference, currentTargetLat, currentTargetLng)
            dismiss()
        }

        return root
    }

    /** Busca direcciones escritas y ofrece coincidencias para ubicar el mapa. */
    private fun buscarDireccion(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            Toast.makeText(requireContext(), "Escribe una calle, avenida o lugar para buscar", Toast.LENGTH_SHORT).show()
            return
        }

        // Ocultar teclado
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etMapSearch.windowToken, 0)

        pbGeocoding.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val searchResult = withContext(Dispatchers.IO) {
                try {
                    if (Geocoder.isPresent()) {
                        val geocoder = Geocoder(requireContext(), Locale("es", "PE"))
                        val fullQuery = if (!cleanQuery.lowercase().contains("cañete") && !cleanQuery.lowercase().contains("lima")) {
                            "$cleanQuery, Cañete, Perú"
                        } else {
                            "$cleanQuery, Perú"
                        }
                        @Suppress("DEPRECATION")
                        val results = geocoder.getFromLocationName(fullQuery, 1)
                        results?.firstOrNull() ?: run {
                            @Suppress("DEPRECATION")
                            geocoder.getFromLocationName(cleanQuery, 1)?.firstOrNull()
                        }
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }

            pbGeocoding.visibility = View.GONE
            if (searchResult != null) {
                val point = GeoPoint(searchResult.latitude, searchResult.longitude)
                mapView?.controller?.animateTo(point, 18.0, 700L)
                Toast.makeText(requireContext(), "📍 Ubicado: ${searchResult.featureName ?: cleanQuery}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    "No encontramos '$cleanQuery'. Puedes mover el mapa libremente.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun moverAUbicacionPredefinida(lat: Double, lng: Double, name: String) {
        val point = GeoPoint(lat, lng)
        mapView?.controller?.animateTo(point, 17.5, 600L)
        Toast.makeText(requireContext(), "Moviendo a $name 🛵", Toast.LENGTH_SHORT).show()
    }

    private fun configurarMapa() {
        val map = mapView ?: return

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true

        val startPoint = GeoPoint(currentTargetLat, currentTargetLng)
        map.controller.setZoom(17.5)
        map.controller.setCenter(startPoint)

        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                alMoverMapa()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                alMoverMapa()
                return false
            }
        })

        // Geocodificar posición inicial
        buscarDireccionDelCentro(currentTargetLat, currentTargetLng)

        // Si no tenía coordenadas previas, intentar mover al GPS del dispositivo automáticamente
        if (initialLat == 0.0 || initialLng == 0.0) {
            val hasFine = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                moverAUbicacionActual()
            }
        }
    }

    private fun alMoverMapa() {
        if (!isPinLifted) {
            isPinLifted = true
            animarElevacionMarcador()
        }
        pbGeocoding.visibility = View.VISIBLE

        geocodeJob?.cancel()
        geocodeJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(350) // Debounce rápido cuando el usuario suelta el mapa
            val map = mapView ?: return@launch
            val center = map.mapCenter
            val lat = center.latitude
            val lng = center.longitude

            if (isPinLifted) {
                isPinLifted = false
                animarDescensoMarcador()
            }

            buscarDireccionDelCentro(lat, lng)
        }
    }

    /** Resuelve la dirección del centro del mapa; conserva coordenadas si falla Geocoder. */
    private fun buscarDireccionDelCentro(lat: Double, lng: Double) {
        currentTargetLat = lat
        currentTargetLng = lng

        val latStr = String.format(Locale.US, "%.5f", lat)
        val lngStr = String.format(Locale.US, "%.5f", lng)
        tvCoordinates.text = "Lat: $latStr · Lng: $lngStr"

        val fallback = "Ubicación fijada ($latStr, $lngStr)"
        if (currentFormattedAddress.isBlank()) {
            currentFormattedAddress = fallback
            tvDetectedAddress.text = fallback
        }

        geocodeJob?.cancel()
        geocodeJob = viewLifecycleOwner.lifecycleScope.launch {
            pbGeocoding.visibility = View.VISIBLE
            try {
                val addressResult = withTimeoutOrNull(2500) {
                    withContext(Dispatchers.IO) {
                        try {
                            if (Geocoder.isPresent()) {
                                val geocoder = Geocoder(requireContext(), Locale("es", "PE"))
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(lat, lng, 1)
                                addresses?.firstOrNull()
                            } else {
                                null
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                procesarResultadoGeocodificacion(addressResult, lat, lng)
            } catch (_: Exception) {
                procesarResultadoGeocodificacion(null, lat, lng)
            } finally {
                pbGeocoding.visibility = View.GONE
            }
        }
    }

    private fun procesarResultadoGeocodificacion(address: Address?, lat: Double, lng: Double) {
        pbGeocoding.visibility = View.GONE
        if (address != null) {
            val street = address.thoroughfare ?: address.featureName ?: "Calle"
            val number = address.subThoroughfare.orEmpty()
            val district = address.subLocality ?: address.locality ?: address.subAdminArea ?: "Cañete"

            val formatted = when {
                address.maxAddressLineIndex >= 0 && !address.getAddressLine(0).isNullOrBlank() -> address.getAddressLine(0)
                number.isNotBlank() -> "$street $number, $district"
                else -> "$street, $district"
            }
            currentFormattedAddress = formatted
            tvDetectedAddress.text = formatted
        } else {
            val fallback = "Ubicación seleccionada (${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lng)})"
            currentFormattedAddress = fallback
            tvDetectedAddress.text = fallback
        }
    }

    private fun animarElevacionMarcador() {
        layoutCenterPin.animate()
            .translationY(-24f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(150)
            .start()
    }

    private fun animarDescensoMarcador() {
        val dropAnim = ObjectAnimator.ofFloat(layoutCenterPin, "translationY", -24f, 0f).apply {
            duration = 300
            interpolator = OvershootInterpolator(2.2f)
        }
        val scaleXAnim = ObjectAnimator.ofFloat(layoutCenterPin, "scaleX", 1.1f, 1.0f).apply {
            duration = 220
        }
        val scaleYAnim = ObjectAnimator.ofFloat(layoutCenterPin, "scaleY", 1.1f, 1.0f).apply {
            duration = 220
        }

        AnimatorSet().apply {
            playTogether(dropAnim, scaleXAnim, scaleYAnim)
            start()
        }
    }

    private fun solicitarPermisoUbicacion() {
        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            moverAUbicacionActual()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun moverAUbicacionActual() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        pbGeocoding.visibility = View.VISIBLE

        // 1. Obtener última posición conocida inmediatamente (caché rápida)
        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
            if (lastLoc != null) {
                pbGeocoding.visibility = View.GONE
                mapView?.controller?.animateTo(GeoPoint(lastLoc.latitude, lastLoc.longitude), 18.0, 600L)
            }
        }

        // 2. Obtener posición GPS satelital precisa
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                pbGeocoding.visibility = View.GONE
                if (location != null) {
                    mapView?.controller?.animateTo(GeoPoint(location.latitude, location.longitude), 18.0, 700L)
                    Toast.makeText(requireContext(), "📍 Centrado en tu ubicación actual", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                pbGeocoding.visibility = View.GONE
            }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            // Desactivar el arrastre del modal para que mover el mapa sea 100% fluido en vista estándar
            behavior.isDraggable = false
            bottomSheet.layoutParams.height = (resources.displayMetrics.heightPixels * 0.94).toInt()
            bottomSheet.requestLayout()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        mapView?.onDetach()
        super.onDestroyView()
    }

    companion object {
        const val TAG = "HojaSeleccionUbicacion"

        fun crear(
            currentAddress: String = "",
            currentReference: String = "",
            lat: Double = 0.0,
            lng: Double = 0.0,
            onLocationConfirmed: ((address: String, reference: String, lat: Double, lng: Double) -> Unit)? = null
        ): HojaSeleccionUbicacion {
            return HojaSeleccionUbicacion().apply {
                this.initialAddress = currentAddress
                this.initialReference = currentReference
                this.initialLat = lat
                this.initialLng = lng
                this.onLocationConfirmed = onLocationConfirmed
            }
        }
    }
}
