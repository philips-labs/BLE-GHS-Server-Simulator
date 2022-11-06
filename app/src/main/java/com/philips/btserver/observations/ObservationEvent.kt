package com.philips.btserver.observations


// Partition 3
enum class ObservationEvent(val value: Int) {
    // TODO Add more events from partition 3
    MDC_EVT_CUFF_INCORRECT( 196834),
    MDC_EVT_CUFF_LEAK( 196836),
    MDC_EVT_CUFF_NOT_DEFLATED( 196838),
    MDC_EVT_CUFF_INFLAT_OVER( 196840),
    MDC_EVT_CUFF_LOOSE( 196848),
    MDC_EVT_SENSOR_DISCONN( 196916),
    MDC_EVT_SENSOR_MALF( 196918),
    MDC_EVT_XDUCR_ABSENT( 196942),
    MDC_EVT_XDUCR_DISCONN( 196944),
    MDC_EVT_XDUCR_MALF( 196946),
    MDC_EVT_CUFF_POSN_ERR( 197038),
}

fun ObservationEvent.asGHSByteArray(): ByteArray {
    // Note: Optimizing for fact that events are in Partition 3
    return byteArrayOf((value and 0xff).toByte(), ((value and 0xff00) shr 8).toByte(), 0x3, 0x0)
}

