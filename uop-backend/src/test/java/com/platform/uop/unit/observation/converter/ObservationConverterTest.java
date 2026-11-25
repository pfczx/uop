package com.platform.uop.unit.observation.converter;

import com.platform.uop.observation.converter.ObservationConverter;
import com.platform.uop.proto.v1.Observation;
import com.platform.uop.proto.v1.Value;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationConverterTest {

    private ObservationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ObservationConverter();
    }

    @Test
    void convertToDatabaseColumn_serializesObservation() {
        Observation observation = Observation.newBuilder()
            .setMetric("cpu.usage")
            .setValue(Value.newBuilder().setDoubleValue(42.5).build())
            .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(1_000L))
            .build();

        byte[] bytes = converter.convertToDatabaseColumn(observation);

        assertThat(bytes).isNotNull();
        assertThat(bytes).isEqualTo(observation.toByteArray());
    }

    @Test
    void convertToDatabaseColumn_nullReturnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_deserializesObservation() {
        Observation original = Observation.newBuilder()
            .setMetric("memory.usage")
            .setValue(Value.newBuilder().setIntValue(1024).build())
            .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(2_000L))
            .build();

        Observation restored = converter.convertToEntityAttribute(original.toByteArray());

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void convertToEntityAttribute_nullReturnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_invalidBytesThrows() {
        byte[] invalid = new byte[] {0x00, 0x01, 0x02, 0x03};

        assertThatThrownBy(() -> converter.convertToEntityAttribute(invalid))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Failed to deserialize observation");
    }

    @Test
    void roundTrip_preservesObservation() {
        Observation original = Observation.newBuilder()
            .setMetric("disk.io")
            .setValue(Value.newBuilder().setDoubleValue(99.9).build())
            .setTimestamp(com.google.protobuf.util.Timestamps.fromMillis(3_000L))
            .build();

        Observation restored = converter.convertToEntityAttribute(
            converter.convertToDatabaseColumn(original));

        assertThat(restored).isEqualTo(original);
    }
}