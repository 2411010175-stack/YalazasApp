# Renombres internos al español

Este registro conecta los nombres usados en la documentación anterior con el código actual. Los cambios de esta etapa son de nombres internos; no cambian el formato de los documentos guardados en Firebase.

| Antes | Ahora |
| --- | --- |
| `utils/CartAnimationHelper.kt` | `utilidades/AnimadorCarrito.kt` |
| `utils/DeliveryPricingHelper.kt` | `utilidades/CalculadoraTarifaEntrega.kt` |
| `utils/DeliveryRouteOptimizer.kt` | `utilidades/OptimizadorRutasEntrega.kt` |
| `utils/PaymentFacade.kt` | `utilidades/FachadaPagos.kt` |
| `viewmodel/AuthViewModel.kt` | `modelovista/ModeloVistaAutenticacion.kt` |
| `viewmodel/MenuViewModel.kt` | `modelovista/ModeloVistaMenu.kt` |
| `AuthState` | `EstadoAutenticacion` |
| `MenuState` | `EstadoMenu` |
| `repository/AuthRepository.kt` | `repositorio/RepositorioAutenticacion.kt` |
| `repository/ProductRepository.kt` | `repositorio/RepositorioProductos.kt` |
| `repository/CartRepository.kt` | `repositorio/RepositorioCarrito.kt` |
| `repository/OrderRepository.kt` | `repositorio/RepositorioPedidos.kt` |
| `repository/FirebaseServices.kt` | `repositorio/ServiciosFirebase.kt` |
| `PlaceOrderRequest` | `SolicitudPedido` |

## Segunda etapa

Se renombró el modelo local `CartItem` a `ArticuloCarrito`. El modelo `OrderItem`, que representa los artículos guardados en el pedido, conserva su nombre y sus propiedades de Firestore.

Los adaptadores de listas también tienen nombres en español:

| Antes | Ahora |
| --- | --- |
| `ProductAdapter` | `AdaptadorProductos` |
| `CartAdapter` | `AdaptadorCarrito` |
| `CustomerTrackingAdapter` | `AdaptadorSeguimientoCliente` |
| `DeliveryHistoryAdapter` | `AdaptadorHistorialEntregas` |
| `DeliveryOrdersAdapter` | `AdaptadorPedidosReparto` |
| `AdminRecentSalesAdapter` | `AdaptadorVentasRecientes` |
| `AdminSalesAdapter` | `AdaptadorVentas` |
| `UserAdapter` | `AdaptadorUsuarios` |

Se tradujeron métodos internos de repositorios, modelos de vista y pantallas. Ejemplos: `addProduct` → `agregarProducto`, `clearCart` → `vaciarCarrito`, `getTotal` → `obtenerTotal`, `placeOrder` → `registrarPedido`, `cancelOwnOrder` → `cancelarPedidoPropio`, `loadProducts` → `cargarProductos`, `checkSession` → `verificarSesion`, `setupListeners` → `configurarEventos` y `listenToCustomerOrdersRealtime` → `escucharPedidosDelCliente`. Las llamadas y etiquetas de retorno de Kotlin se actualizaron junto con las declaraciones.

También se tradujeron variables locales de navegación en `MainActivity`, como `currentDestinationId` → `destinoActualId` e `isKeyboardVisible` → `tecladoVisible`.

Se añadieron comentarios de clase y de flujo en los archivos Kotlin. Describen la responsabilidad de cada pantalla, los datos que se escuchan en Firestore y los puntos donde se valida o guarda información. Los comentarios no sustituyen las reglas de seguridad de Firebase.

## Tercera etapa

Se renombraron las carpetas `ui/dialogs` → `ui/dialogos` y `ui/location` → `ui/ubicacion`. Las hojas y los diálogos que se abren desde Kotlin recibieron estos nombres:

| Antes | Ahora |
| --- | --- |
| `AdminChangeRoleBottomSheet` | `HojaCambioRolAdministrador` |
| `AdminChangeStatusBottomSheet` | `HojaCambioEstadoPedido` |
| `AdminSaleDetailsBottomSheet` | `HojaDetalleVenta` |
| `CompleteProfileBottomSheet` | `HojaCompletarPerfil` |
| `OrderConfirmationBottomSheet` | `HojaConfirmacionPedido` |
| `OrderSuccessDialogFragment` | `DialogoPedidoRegistrado` |
| `ProductDetailBottomSheet` | `HojaDetalleProducto` |
| `SauceSelectionBottomSheet` | `HojaSeleccionSalsas` |
| `LocationPickerBottomSheet` | `HojaSeleccionUbicacion` |
| `BatchTakeConfirmationDialogFragment` | `DialogoTomarVariosPedidos` |
| `LogoutConfirmationDialogFragment` | `DialogoCerrarSesion` |
| `MarkDeliveredConfirmationDialogFragment` | `DialogoMarcarEntregado` |
| `TakeOrderConfirmationDialogFragment` | `DialogoTomarPedido` |

Sus métodos de creación `newInstance` se llaman ahora `crear`. También se tradujeron métodos privados de estas hojas, los estados internos de los modelos de vista (`Cargando`, `Exito`, `Inactivo`), métodos auxiliares de `OrderStatus` y nombres de `FachadaPagos`. Los valores persistidos, como `PENDIENTE`, `EFECTIVO` y los campos de los pedidos, conservan su texto original.

Los fragmentos declarados en `nav_graph.xml`, la actividad principal y los identificadores XML mantienen sus nombres actuales. Así se pueden probar las pantallas sin modificar los destinos de navegación en esta etapa.

También se renombraron variables internas de los ViewModels, por ejemplo `viewModel` a `modeloVista`, `allProducts` a `todosLosProductos` y `currentCategory` a `categoriaActual`.

Los métodos de las utilidades también tienen nombres en español: `calcularDistanciaKm`, `calcularTarifaEntrega`, `formatearDistancia`, `estimarTiempoEntrega`, `optimizarSecuenciaEntregas`, `textoInsigniaParada`, `animarHaciaCarrito`, `calcularVuelto` y `copiarAlPortapapeles`. Las constantes de ubicación y tarifa ahora incluyen nombres como `LATITUD_LOCAL` y `TARIFA_BASE`.

## Contratos que permanecen iguales

- Identificador y paquete raíz de Android: `com.yalazas.app`.
- Colecciones de Firestore: `users`, `products`, `orders` y `auditoria`.
- Propiedades persistidas, entre ellas `role`, `status`, `userId`, `isDelivery`, `paymentStatus` y `deliveryDriverId`.
- Valores de roles y estados, por ejemplo `ADMINISTRADOR`, `CLIENTE`, `PENDIENTE` y `EN_PROCESO`.
- IDs de recursos XML y destinos de navegación.

Las clases `Order`, `OrderItem`, `Product`, `UserProfile`, `Role` y `OrderStatus` conservan sus nombres y propiedades persistidas. Los fragmentos declarados en navegación se mantienen mientras esas referencias no se cambien en un paso verificado por separado.
