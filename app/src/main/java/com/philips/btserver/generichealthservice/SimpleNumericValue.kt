package com.philips.btserver.generichealthservice

data class SimpleNumericValue(
    val type: ObservationType,
    val value: Float,
    val unitCode: UnitCode,
) {

}
