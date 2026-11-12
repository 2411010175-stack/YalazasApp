package com.yalazas.app.model

/** Acciones que la aplicación asocia a cada rol de usuario. */
enum class Permission {
    MANAGE_USERS,
    MANAGE_PRODUCTS,
    MANAGE_INVENTORY,
    MANAGE_TABLES,
    VIEW_FINANCES,
    MANAGE_OFFERS,
    CREATE_ORDER,
    VIEW_ORDERS,
    VIEW_TABLES,
    UPDATE_ORDER_STATUS,
    VIEW_ASSIGNED_ORDERS,
    UPDATE_DELIVERY_STATUS,
    CREATE_OWN_ORDER,
    VIEW_OWN_ORDER_STATUS
}

/** Roles reconocidos por la aplicación; `value` conserva el texto usado por Firebase. */
enum class Role(val value: String, val permissions: List<Permission>) {
    ADMIN("ADMINISTRADOR", listOf(
        Permission.MANAGE_USERS,
        Permission.MANAGE_PRODUCTS,
        Permission.MANAGE_INVENTORY,
        Permission.MANAGE_TABLES,
        Permission.VIEW_FINANCES,
        Permission.MANAGE_OFFERS
    )),
    COCINERO("COCINERO", listOf(
        Permission.VIEW_ORDERS,
        Permission.UPDATE_ORDER_STATUS
    )),
    MESERO("MESERO", listOf(
        Permission.CREATE_ORDER,
        Permission.VIEW_TABLES,
        Permission.VIEW_ORDERS,
        Permission.UPDATE_ORDER_STATUS
    )),
    REPARTIDOR("REPARTIDOR", listOf(
        Permission.VIEW_ASSIGNED_ORDERS,
        Permission.UPDATE_DELIVERY_STATUS
    )),
    CLIENTE("CLIENTE", listOf(
        Permission.CREATE_OWN_ORDER,
        Permission.VIEW_OWN_ORDER_STATUS
    ));

    companion object {
        fun desdeTexto(role: String): Role {
            return values().find { it.name.equals(role, ignoreCase = true) || it.value.equals(role, ignoreCase = true) } ?: CLIENTE
        }
    }
}
