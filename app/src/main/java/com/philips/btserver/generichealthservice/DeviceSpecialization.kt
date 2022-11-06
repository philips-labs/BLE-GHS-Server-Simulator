package com.philips.btserver.generichealthservice

// Partition 8
enum class DeviceSpecialization(val value: Int) {
    // TODO Add remaining device specializations
    MDC_DEV_SPEC_PROFILE_HYDRA( 528384),
    MDC_DEV_SPEC_PROFILE_INFUS( 528385),
    MDC_DEV_SPEC_PROFILE_VENT( 528386),
    MDC_DEV_SPEC_PROFILE_VS_MON( 528387),
    MDC_DEV_SPEC_PROFILE_PULS_OXIM( 528388),
    MDC_DEV_SPEC_PROFILE_DEFIB( 528389),
    MDC_DEV_SPEC_PROFILE_ECG( 528390),
    MDC_DEV_SPEC_PROFILE_BP( 528391),
    MDC_DEV_SPEC_PROFILE_TEMP( 528392)
}

fun DeviceSpecialization.asByteArray(): ByteArray {
    return byteArrayOf((value and 0xff).toByte(), ((value and 0xff00) shr 8).toByte(), versionNumber())
}

fun DeviceSpecialization.versionNumber(): Byte { return 1 }