package com.welie.btserver;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteOrder;
import java.util.Date;

public class SimpleNumericObservation {
    public final short id;
    public final ObservationType type;
    public final float value;
    public final Unit unit;
    public final Date timestamp;

    public SimpleNumericObservation(short id, @NotNull ObservationType type, float value, @NotNull Unit unit, @NotNull Date timestamp) {
        this.id = id;
        this.type = type;
        this.value = value;
        this.unit = unit;
        this.timestamp = timestamp;
    }

    @NotNull
    public byte[] serialize() {
        int handleCode = 0x00010921;
        int handleLength = 2;

        int typeCode = 0x0001092F;
        int typeLength = 4;

        int valueCode = 0x00010A56;
        int valueLength = 4;

        int unitCode = 0x00010996;
        int unitLength = 4;

        int timestampCode = 0x00010990;
        int timestampLength = 8;

        BluetoothBytesParser handleParser = new BluetoothBytesParser(ByteOrder.BIG_ENDIAN);
        handleParser.setIntValue(handleCode, BluetoothBytesParser.FORMAT_UINT32);
        handleParser.setIntValue(handleLength, BluetoothBytesParser.FORMAT_UINT16);
        handleParser.setIntValue(id, BluetoothBytesParser.FORMAT_UINT16);

        BluetoothBytesParser typeParser = new BluetoothBytesParser(ByteOrder.BIG_ENDIAN);
        typeParser.setIntValue(typeCode, BluetoothBytesParser.FORMAT_UINT32);
        typeParser.setIntValue(typeLength, BluetoothBytesParser.FORMAT_UINT16);
        typeParser.setIntValue(type.getValue(), BluetoothBytesParser.FORMAT_UINT32);

        BluetoothBytesParser valueParser = new BluetoothBytesParser(ByteOrder.BIG_ENDIAN);
        valueParser.setIntValue(valueCode, BluetoothBytesParser.FORMAT_UINT32);
        valueParser.setIntValue(valueLength, BluetoothBytesParser.FORMAT_UINT16);
        valueParser.setFloatValue(value, 1);

        BluetoothBytesParser unitParser = new BluetoothBytesParser(ByteOrder.BIG_ENDIAN);
        unitParser.setIntValue(unitCode, BluetoothBytesParser.FORMAT_UINT32);
        unitParser.setIntValue(unitLength, BluetoothBytesParser.FORMAT_UINT16);
        unitParser.setIntValue(unit.getValue(), BluetoothBytesParser.FORMAT_UINT32);

        BluetoothBytesParser timestampParser = new BluetoothBytesParser(ByteOrder.BIG_ENDIAN);
        timestampParser.setIntValue(timestampCode, BluetoothBytesParser.FORMAT_UINT32);
        timestampParser.setIntValue(timestampLength, BluetoothBytesParser.FORMAT_UINT16);
        timestampParser.setLong(timestamp.getTime());

        return BluetoothBytesParser.mergeArrays(
                handleParser.getValue(),
                typeParser.getValue(),
                valueParser.getValue(),
                unitParser.getValue(),
                timestampParser.getValue()
        );
    }
}
