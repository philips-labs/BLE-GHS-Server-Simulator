package com.welie.btserver

enum class ObservationType(val value: Int) {
    SPO2(0x00024BB8),
    ORAL_TEMPERATURE(0x0002E008),
    PULSE_RATE(0x0002481A),
    UNKNOWN_STATUS_CODE(-0x1);

    companion object {
        fun fromValue(value: Int): ObservationType {
            for (type in values()) {
                if (type.value == value) return type
            }
            return UNKNOWN_STATUS_CODE
        }
    }
}