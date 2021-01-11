/*
 * Copyright (c) Koninklijke Philips N.V., 2017.
 * All rights reserved.
 */

package com.welie.btserver;

import java.nio.ByteBuffer;

public class IEEESfloat {
    private int exponent;
    private int sign;
    private int mantissa;
    private double value;
    private byte[] valueBytes = {0, 0};

    private static final int exponentPos = 0xF000;
    private static final int signPos = 0x0800;
    private static final int mantissaPos = 0x07FF;

//    public IEEESFloat(double value) {
//        //exponent = (int) Math.log10(value) - 3; // for example for 37.8, exponent = -2 (1 - 3)
//        //mantissa = (int) value / 10^exponent; // mantissa = 37.8 / 10^-2 = 3780
//
//        int sfloat = 0;
//        double decimalCopy = value;
//        double absDecimal = Math.abs(value);
//        exponent = 0;
//
//        double fractal = modf(absDecimal, &integralDouble);
//
//            while(fractal != 0)
//            {
//                fractal = modf(decimalCopy, &integralDouble);
//
//                if(fractal != 0)
//                {
//                    decimalCopy *= 10;
//                    exponent--;
//                }
//            }
//
//            if(exponent >= 0)
//            {
//                sfloat = (uint8) (exponent << SFLOAT_MANTISSA_SIZE);
//            }
//            else
//            {
//                uint8 exp = abs(exponent);
//                uint8 mask = 0xF;
//                exp--;
//                exp = exp ^ mask;
//                sfloat = exp << SFLOAT_MANTISSA_SIZE;
//            }
//
//            if(decimal < 0)
//            {
//                sfloat |= 0x01 <<  (SFLOAT_MANTISSA_SIZE - 1);
//            }
//
//            mantissa = (uint16) integralDouble;
//            mantissa &= 0x7FF;
//            sfloat |= mantissa;
//            //DBG_PRINTF("sfloat: %x \r\n", sfloat);
//
//            //return sfloat;
//        }
//
//    }

    public IEEESfloat(byte[] data) {
        ByteBuffer bytes = ByteBuffer.wrap(data);

        String s1 = String.format("%8s", Integer.toBinaryString(bytes.get(0) & 0xFF)).replace(' ', '0');
        String s2 = String.format("%8s", Integer.toBinaryString(bytes.get(1) & 0xFF)).replace(' ', '0');

        //HBLogger.v("SFloat", "Raw data= " + s2 + s1);


        exponent = (bytes.get(1) >> 4) & 0x0F;
        //HBLogger.v("SFloat", "Exponent = " + exponent);
        sign = (bytes.get(1)) & 0x08;
        mantissa = bytes.get(0) & 0xFF;
        mantissa |= (bytes.get(1) & 0x07) << 8;

        if(exponent > 7)
        {
            exponent -= 16;
        }

        if(sign > 1) {
            mantissa *= -1;
        }

        value = (mantissa * Math.pow(10, exponent));
        //HBLogger.v("SFloat", "exponent: " + exponent + ", sign: " + sign + ", mantissa: " + mantissa + ", value: " + value);
    }

    public double getValue() {
        // remove the floating point error and round the the correct amount of digits
        double returnValue = Math.round(value * Math.pow(10, Math.abs(exponent)));
        return returnValue / Math.pow(10, Math.abs(exponent));
    }

    public byte[] getBytes() {
        return valueBytes;
    }

}
