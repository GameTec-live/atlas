package org.gtlv.core.shift

enum class ShiftRole(
    val apiValue: String
) {
    DRIVER("driver"),
    DISPATCHER("dispatcher");

    companion object {
        fun fromApiValue(value: String): ShiftRole? {
            return entries.firstOrNull {
                it.apiValue.equals(value, ignoreCase = true)
            }
        }
    }
}