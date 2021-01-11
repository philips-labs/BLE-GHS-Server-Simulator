package com.welie.btserver

enum class Unit(val value: Int) {
    BPM(0x00040AA0),
    CELSIUS(0x000417A0),
    MMHG(0x00040F20),
    PERCENT(0x00040220),
    UNKNOWN_CODE(0xFFFFFFF);

    companion object {
        fun fromValue(value: Int): Unit {
            for (type in values()) {
                if (type.value == value) return type
            }
            return UNKNOWN_CODE
        }
    }
}