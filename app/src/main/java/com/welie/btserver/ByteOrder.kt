/*
 * Copyright (c) Koninklijke Philips N.V. 2020
 * All rights reserved.
 */
package com.welie.btserver

enum class ByteOrder {
    /**
     * Constant denoting big-endian byte order.  In this order, the bytes of a
     * multibyte value are ordered from most significant to least significant.
     */
    BIG_ENDIAN,
    /**
     * Constant denoting little-endian byte order.  In this order, the bytes of
     * a multibyte value are ordered from least significant to most
     * significant.
     */
    LITTLE_ENDIAN;
}
