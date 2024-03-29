/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserver.observations

enum class ObservationValueType(val value: Int) {
    MDC_ATTR_NU_VAL_OBS_SIMP(0x00010A56),  // Simple-Nu-Observed-Value
    MDC_ATTR_NU_CMPD_VAL_OBS(0x0001094B),  // Compound-Nu-Observed-Value, Numeric and derived objects
    MDC_ATTR_NU_VAL_OBS(0x00010950),        // Nu-Observed-Value, Numeric and derived objects
    MDC_ATTR_SA_VAL_OBS(0x0001096E),        // Sa-Observed-Value, Sample Array and derived objects
    MDC_ATTR_SA_CMPD_VAL_OBS(0x00010967),  // Compound-Sa-Observed-Value, Sample Array and derived objects
    MDC_ATTR_VAL_ENUM_OBS(0x0001099E),  // Enum-Observed-Value, Enumeration
    MDC_ATTR_VAL_ENUM_OBS_CMPD(0x0001099F),  // Compound-Enum-Observed-Value, Enumeration
    MDC_ATTR_CMPLX_VAL_OBS(0x00010A3C),  // Cmplx-Observed-Value, Complex Metric
    // TODO: WHAT IS THE MDC CODE FOR A STRING OBSERVATION? THIS VALUE IS JUST A PLACEHOLDER
    MDC_ATTR_STRING_VAL_OBS_SIMPLE(0x00010FFF),  // String value observation
    // TODO: WHAT IS THE MDC CODE FOR A TLV OBSERVATION? THIS VALUE IS JUST A PLACEHOLDER
    MDC_ATTR_TLV_VAL_OBS(0x00010FFF),  // String value observation
    UNKNOWN_TYPE_VAL_OBS(0x0);      // Uknown observation value type

    companion object {
        fun fromValue(value: Int): ObservationValueType {
            return values().find { it.value == value } ?: UNKNOWN_TYPE_VAL_OBS
        }
        const val valueByteLength = 4
    }

}

fun ObservationType.valueType(): ObservationValueType {
    return when(this) {
        ObservationType.MDC_ECG_HEART_RATE,
        ObservationType.MDC_PULS_OXIM_SAT_O2,
        ObservationType.MDC_TEMP_BODY -> ObservationValueType.MDC_ATTR_NU_VAL_OBS_SIMP
        ObservationType.MDC_PPG_TIME_PD_PP -> ObservationValueType.MDC_ATTR_SA_VAL_OBS
        ObservationType.MDC_PRESS_BLD_NONINV -> ObservationValueType.MDC_ATTR_NU_CMPD_VAL_OBS
        ObservationType.MDC_ATTR_ALERT_TYPE -> ObservationValueType.MDC_ATTR_VAL_ENUM_OBS
        ObservationType.MDC_DRUG_NAME_LABEL -> ObservationValueType.MDC_ATTR_STRING_VAL_OBS_SIMPLE
        ObservationType.MDC_DOSE_DRUG_DELIV -> ObservationValueType.MDC_ATTR_TLV_VAL_OBS
        else -> ObservationValueType.UNKNOWN_TYPE_VAL_OBS
    }
}