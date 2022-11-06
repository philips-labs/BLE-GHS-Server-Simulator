package com.philips.btserver.observations

// From Bluetooth Assigned Numbers
enum class BluetoothValueFormatType(val value: Int) {
    BOOLEAN(0x01),
    TWO_BIT(0x02),
    NIBBLE(0x03),
    UINT8(0x04),
    UINT12(0x05),
    UINT16(0x06),
    UINT24(0X07),
    UINT32(0X08),
    UINT48(0X09),
    UINT64(0X0A),
    UINT128(0X0B),
    SINT8(0X0C),
    SINT12(0X0D),
    SINT16(0X0E),
    SINT24(0X0F),
    SINT32(0X10),
    SINT48(0X11),
    SINT64(0X12),
    SINT128(0X13),
    FLOAT32(0X14),      // IEEE-754 32-bit floating point
    FLOAT64(0X15),      // IEEE-754 64-bit floating point
    SFLOAT(0X16),       // IEEE-11073 16-bit SFLOAT
    FLOAT(0X17),        // IEEE-11073 32-bit FLOAT
    DUINT16(0X18),      // IEEE-20601 format
    UTF8S(0X19),
    UTF16S(0X1A),
    STRUCT(0X1B)
}