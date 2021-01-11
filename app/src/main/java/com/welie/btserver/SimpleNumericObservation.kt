package com.welie.btserver;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteOrder;
import java.util.Date;
import java.util.Objects;

import timber.log.Timber;

import static com.welie.btserver.BluetoothBytesParser.FORMAT_FLOAT;
import static com.welie.btserver.BluetoothBytesParser.FORMAT_UINT16;
import static com.welie.btserver.BluetoothBytesParser.FORMAT_UINT32;

public class SimpleNumericObservation {
    public final short id;
    public final ObservationType type;
    public final float value;
    public final Unit unit;
    public final Date timestamp;

    private final static int handleCode = 0x00010921;
    private final static int handleLength = 2;

    private final static int typeCode = 0x0001092F;
    private final static int typeLength = 4;

    private final static int valueCode = 0x00010A56;
    private final static int valueLength = 4;

    private final static int unitCode = 0x00010996;
    private final static int unitLength = 4;

    private final static int timestampCode = 0x00010990;
    private final static int timestampLength = 8;

    public SimpleNumericObservation(short id, @NotNull ObservationType type, float value, @NotNull Unit unit, @NotNull Date timestamp) {
        this.id = id;
        this.type = type;
        this.value = value;
        this.unit = unit;
        this.timestamp = timestamp;
    }

    public static SimpleNumericObservation deserialize(@NotNull byte[] bytes)  {
        Objects.requireNonNull(bytes, "Bytes is null");
        BluetoothBytesParser parser = new BluetoothBytesParser(ByteOrder.BIG_ENDIAN);

        // Parse id
        int parsedHandleCode = parser.getIntValue(FORMAT_UINT32);
        if (parsedHandleCode != handleCode) {
            Timber.e("Expected handleCode but got %d", parsedHandleCode);
        }
        int parsedHandleLength = parser.getIntValue(FORMAT_UINT16);
        if (parsedHandleLength != handleLength) {
            Timber.e("Expected handleLength but got %d", parsedHandleLength);
        }
        int parsedId = parser.getIntValue(FORMAT_UINT32);

        // Parse type
        int parsedTypeCode = parser.getIntValue(FORMAT_UINT32);
        if (parsedTypeCode != typeCode) {
            Timber.e("Expected handleCode but got %d", parsedTypeCode);
        }
        int parsedTypeLength = parser.getIntValue(FORMAT_UINT16);
        if (parsedTypeLength != typeLength) {
            Timber.e("Expected handleLength but got %d", parsedTypeLength);
        }
        ObservationType parsedType = ObservationType.fromValue(parser.getIntValue(FORMAT_UINT32));
        if (parsedType == ObservationType.UNKNOWN_STATUS_CODE) {
            Timber.e("Unknown observation type");
        }

        // Parse value
        int parsedValueCode = parser.getIntValue(FORMAT_UINT32);
        if (parsedValueCode != valueCode) {
            Timber.e("Expected valueCode but got %d", parsedValueCode);
        }
        int parsedValueLength = parser.getIntValue(FORMAT_UINT16);
        if (parsedValueLength != valueLength) {
            Timber.e("Expected valueLength but got %d", parsedValueLength);
        }
        float parsedValue = parser.getIntValue(FORMAT_FLOAT);

        // Parse unit
        int parsedUnitCode = parser.getIntValue(FORMAT_UINT32);
        if (parsedUnitCode != unitCode) {
            Timber.e("Expected unitCode but got %d", parsedUnitCode);
        }
        int parsedUnitLength = parser.getIntValue(FORMAT_UINT16);
        if (parsedUnitLength != unitLength) {
            Timber.e("Expected handleLength but got %d", parsedUnitLength);
        }
        Unit parsedUnit = Unit.fromValue(parser.getIntValue(FORMAT_UINT32));
        if (parsedUnit == Unit.UNKNOWN_CODE) {
            Timber.e("Unknown unit type");
        }

        // Parse timestamp
        int parsedTimestampCode = parser.getIntValue(FORMAT_UINT32);
        if (parsedTimestampCode != timestampCode) {
            Timber.e("Expected timestampCode but got %d", parsedTimestampCode);
        }
        int parsedTimestampLength = parser.getIntValue(FORMAT_UINT16);
        if (parsedTimestampLength != handleLength) {
            Timber.e("Expected handleLength but got %d", parsedTimestampLength);
        }
        Date parsedTimestamp = new Date(parser.getLongValue());

        return new SimpleNumericObservation((short) parsedId, parsedType, parsedValue, parsedUnit, parsedTimestamp);
    }

    @NotNull
    public byte[] serialize() {
        BluetoothBytesParser handleParser = new BluetoothBytesParser(ByteOrder.BIG_ENDIAN);
        handleParser.setIntValue(handleCode, FORMAT_UINT32);
        handleParser.setIntValue(handleLength, FORMAT_UINT16);
        handleParser.setIntValue(id, FORMAT_UINT16);

        BluetoothBytesParser typeParser = new BluetoothBytesParser(ByteOrder.BIG_ENDIAN);
        typeParser.setIntValue(typeCode, FORMAT_UINT32);
        typeParser.setIntValue(typeLength, FORMAT_UINT16);
        typeParser.setIntValue(type.getValue(), FORMAT_UINT32);

        BluetoothBytesParser valueParser = new BluetoothBytesParser(ByteOrder.BIG_ENDIAN);
        valueParser.setIntValue(valueCode, FORMAT_UINT32);
        valueParser.setIntValue(valueLength, FORMAT_UINT16);
        valueParser.setFloatValue(value, 1);

        BluetoothBytesParser unitParser = new BluetoothBytesParser(ByteOrder.BIG_ENDIAN);
        unitParser.setIntValue(unitCode, FORMAT_UINT32);
        unitParser.setIntValue(unitLength, FORMAT_UINT16);
        unitParser.setIntValue(unit.getValue(), FORMAT_UINT32);

        BluetoothBytesParser timestampParser = new BluetoothBytesParser(ByteOrder.BIG_ENDIAN);
        timestampParser.setIntValue(timestampCode, FORMAT_UINT32);
        timestampParser.setIntValue(timestampLength, FORMAT_UINT16);
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
