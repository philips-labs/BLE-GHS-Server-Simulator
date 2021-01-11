package com.welie.btserver;

import org.jetbrains.annotations.NotNull;

public enum ObservationType {
    SPO2(0x00024BB8),
    ORAL_TEMPERATURE(0x0002E008),
    PULSE_RATE(0x0002481A),
    UNKNOWN_STATUS_CODE(0xFFFFFFFF);

    ObservationType(int value) {
        this.value = value;
    }

    private final int value;

    public int getValue() {
        return value;
    }

    @NotNull
    public static ObservationType fromValue(int value) {
        for (ObservationType type : values()) {
            if (type.getValue() == value)
                return type;
        }
        return UNKNOWN_STATUS_CODE;
    }
}
