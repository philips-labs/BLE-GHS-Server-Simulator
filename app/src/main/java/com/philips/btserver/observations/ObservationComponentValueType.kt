package com.philips.btserver.observations

enum class ObservationComponentValueType(val value: Int) {
    NUMERIC(1),
    SIMPLE_DISCRETE(2),
    STRING(3),
    SAMPLE_ARRAY(4),
    COMPOUND_DISCRETE(5),
    COMPOUND_STATE_EVENT(6)
}