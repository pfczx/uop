package com.platform.uop.observation.converter;

import com.google.protobuf.InvalidProtocolBufferException;
import com.platform.uop.proto.v1.Observation;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ObservationConverter
        implements AttributeConverter<Observation, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(Observation observation) {
        return observation == null ? null : observation.toByteArray();
    }

    @Override
    public Observation convertToEntityAttribute(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        try {
            return Observation.parseFrom(bytes);
        } catch (InvalidProtocolBufferException exception) {
            throw new IllegalArgumentException(
                    "Failed to deserialize observation", exception);
        }
    }
}
