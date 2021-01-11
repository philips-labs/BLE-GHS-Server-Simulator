package com.welie.btserver;

import org.jetbrains.annotations.NotNull;

public enum Unit {

    BPM(0x00040AA0),
    MMHG(0x00040F20),
    CELSIUS(0x000417A0),
    PERCENT(0x00040220),
    UNKNOWN_CODE(0xFFFFFFFF);

    Unit(int value) {
        this.value = value;
    }

    private final int value;

    public int getValue() {
        return value;
    }

    @NotNull
    public static Unit fromValue(int value) {
        for (Unit type : values()) {
            if (type.getValue() == value)
                return type;
        }
        return UNKNOWN_CODE;
    }
}
