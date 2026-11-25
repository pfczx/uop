package com.platform.uop.integrations.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;
import com.platform.uop.proto.v1.MetricDescriptor;
import com.platform.uop.proto.v1.MetricType;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.Observation;
import com.platform.uop.proto.v1.ObservationBatch;
import com.platform.uop.proto.v1.Value;
import com.platform.uop.proto.v1.ValueType;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProtobufSerializationTest {

    @Test
    void monitorDescriptor_roundTrip() throws InvalidProtocolBufferException {
        MonitorDescriptor original = MonitorDescriptor.newBuilder()
            .setId("test-monitor")
            .setName("Test Monitor")
            .setDescription("A test monitor")
            .setVersion("1.0")
            .setActive(true)
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("cpu.usage")
                .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("percent")
                .build())
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("memory.usage")
                .setValueType(ValueType.VALUE_TYPE_INT64)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("bytes")
                .build())
            .build();

        byte[] bytes = original.toByteArray();
        MonitorDescriptor parsed = MonitorDescriptor.parseFrom(bytes);

        assertThat(parsed.getId()).isEqualTo(original.getId());
        assertThat(parsed.getName()).isEqualTo(original.getName());
        assertThat(parsed.getDescription()).isEqualTo(original.getDescription());
        assertThat(parsed.getVersion()).isEqualTo(original.getVersion());
        assertThat(parsed.getActive()).isEqualTo(original.getActive());
        assertThat(parsed.getMetricsCount()).isEqualTo(2);
        assertThat(parsed.getMetrics(0).getName()).isEqualTo("cpu.usage");
        assertThat(parsed.getMetrics(1).getName()).isEqualTo("memory.usage");
    }

    @Test
    void observationBatch_roundTrip() throws InvalidProtocolBufferException {
        String batchId = UUID.randomUUID().toString();
        ObservationBatch original = ObservationBatch.newBuilder()
            .setBatchId(batchId)
            .addObservations(Observation.newBuilder()
                .setMetric("cpu.usage")
                .setValue(Value.newBuilder().setDoubleValue(42.5).build())
                .build())
            .addObservations(Observation.newBuilder()
                .setMetric("memory.usage")
                .setValue(Value.newBuilder().setIntValue(1024L).build())
                .build())
            .build();

        byte[] bytes = original.toByteArray();
        ObservationBatch parsed = ObservationBatch.parseFrom(bytes);

        assertThat(parsed.getBatchId()).isEqualTo(batchId);
        assertThat(parsed.getObservationsCount()).isEqualTo(2);
        assertThat(parsed.getObservations(0).getMetric()).isEqualTo("cpu.usage");
        assertThat(parsed.getObservations(0).getValue().getDoubleValue()).isEqualTo(42.5);
        assertThat(parsed.getObservations(1).getMetric()).isEqualTo("memory.usage");
        assertThat(parsed.getObservations(1).getValue().getIntValue()).isEqualTo(1024L);
    }

    @Test
    void value_allTypes_roundTrip() throws InvalidProtocolBufferException {
        Value doubleVal = Value.newBuilder().setDoubleValue(3.14).build();
        Value intVal = Value.newBuilder().setIntValue(42L).build();
        Value stringVal = Value.newBuilder().setStringValue("test").build();
        Value boolVal = Value.newBuilder().setBoolValue(true).build();

        assertThat(Value.parseFrom(doubleVal.toByteArray()).getDoubleValue()).isEqualTo(3.14);
        assertThat(Value.parseFrom(intVal.toByteArray()).getIntValue()).isEqualTo(42L);
        assertThat(Value.parseFrom(stringVal.toByteArray()).getStringValue()).isEqualTo("test");
        assertThat(Value.parseFrom(boolVal.toByteArray()).getBoolValue()).isEqualTo(true);
    }

    @Test
    void metricDescriptor_allTypes_roundTrip() throws InvalidProtocolBufferException {
        for (MetricType type : MetricType.values()) {
            if (type == MetricType.METRIC_TYPE_UNSPECIFIED) continue;
            
            try {
                MetricDescriptor original = MetricDescriptor.newBuilder()
                    .setName("test." + type.name().toLowerCase())
                    .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                    .setMetricType(type)
                    .setUnit("test")
                    .build();

                MetricDescriptor parsed = MetricDescriptor.parseFrom(original.toByteArray());
                assertThat(parsed.getName()).isEqualTo(original.getName());
                assertThat(parsed.getMetricType()).isEqualTo(type);
            } catch (IllegalArgumentException e) {
                // Skip types that can't be set (like UNSPECIFIED)
            }
        }
    }

    @Test
    void value_allValueTypes_roundTrip() throws InvalidProtocolBufferException {
        for (ValueType type : ValueType.values()) {
            if (type == ValueType.VALUE_TYPE_UNSPECIFIED) continue;
            
            Value.Builder builder = Value.newBuilder();
            switch (type) {
                case VALUE_TYPE_DOUBLE -> builder.setDoubleValue(1.5);
                case VALUE_TYPE_INT64 -> builder.setIntValue(42L);
                case VALUE_TYPE_UINT64 -> builder.setUintValue(42L);
                case VALUE_TYPE_STRING -> builder.setStringValue("test");
                case VALUE_TYPE_BOOL -> builder.setBoolValue(true);
                default -> {
                    // Skip unsupported types
                    continue;
                }
            }
            
            Value original = builder.build();
            Value parsed = Value.parseFrom(original.toByteArray());
            
            assertThat(parsed.getValueCase()).isEqualTo(original.getValueCase());
        }
    }

    @Test
    void largeObservationBatch_roundTrip() throws InvalidProtocolBufferException {
        ObservationBatch.Builder builder = ObservationBatch.newBuilder()
            .setBatchId(UUID.randomUUID().toString());
        
        for (int i = 0; i < 100; i++) {
            builder.addObservations(Observation.newBuilder()
                .setMetric("metric." + i)
                .setValue(Value.newBuilder().setDoubleValue(i * 1.5).build())
                .build());
        }
        
        ObservationBatch original = builder.build();
        byte[] bytes = original.toByteArray();
        
        assertThat(bytes.length).isGreaterThan(0);
        
        ObservationBatch parsed = ObservationBatch.parseFrom(bytes);
        assertThat(parsed.getObservationsCount()).isEqualTo(100);
        assertThat(parsed.getBatchId()).isEqualTo(original.getBatchId());
    }

    @Test
    void partialMonitorDescriptor_parsing() throws InvalidProtocolBufferException {
        // Test that we can parse a descriptor with minimal fields
        byte[] minimalBytes = MonitorDescriptor.newBuilder()
            .setId("minimal")
            .setName("Minimal")
            .setVersion("1")
            .build()
            .toByteArray();
        
        MonitorDescriptor parsed = MonitorDescriptor.parseFrom(minimalBytes);
        assertThat(parsed.getId()).isEqualTo("minimal");
        assertThat(parsed.getName()).isEqualTo("Minimal");
        assertThat(parsed.getVersion()).isEqualTo("1");
        assertThat(parsed.getDescription()).isEqualTo("");
        assertThat(parsed.getActive()).isFalse();
        assertThat(parsed.getMetricsCount()).isEqualTo(0);
    }
}