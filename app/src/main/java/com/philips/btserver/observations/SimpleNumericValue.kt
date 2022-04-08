package com.philips.btserver.observations

data class SimpleNumericValue(
    val type: ObservationType,
    val value: Float,
    val unitCode: UnitCode,
) {

}
