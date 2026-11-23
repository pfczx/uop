package com.platform.uop.agent.sdk;

import com.platform.uop.proto.v1.MetricDescriptor;
import com.platform.uop.proto.v1.MetricType;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.Observation;
import com.platform.uop.proto.v1.ObservationBatch;
import com.platform.uop.proto.v1.Value;
import com.platform.uop.proto.v1.ValueType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ObservationServiceTest {

    private ObservationService service;
    private Map<UUID, ObservationBatch> pendingBatches;
    private MonitorDescriptor descriptor;

    @BeforeEach
    void setUp() {
        pendingBatches = new ConcurrentHashMap<>();
        descriptor = MonitorDescriptor.newBuilder()
            .setId("test-monitor")
            .setName("Test Monitor")
            .addMetrics(MetricDescriptor.newBuilder()
                .setName("test.metric")
                .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                .setUnit("count")
                .build())
            .build();
    }

    @Test
    void startAndStop() {
        service = new ObservationService(pendingBatches, descriptor);
        service.start(List.of(), descriptor);
        assertTrue(service.isRunning());
        
        service.stop();
        assertFalse(service.isRunning());
    }

    @Test
    void collectAndFlush() {
        service = new ObservationService(pendingBatches, descriptor);
        
        Collector collector = new Collector() {
            @Override
            public MetricDescriptor getDescriptor() {
                return MetricDescriptor.newBuilder()
                    .setName("test.metric")
                    .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                    .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                    .build();
            }

            @Override
            public List<Observation> collect() {
                return List.of(Observation.newBuilder()
                    .setMetric("test.metric")
                    .setValue(Value.newBuilder().setDoubleValue(42.0).build())
                    .build());
            }
        };

        service.start(List.of(collector), descriptor);
        
        // Wait for collection and flush (default flush interval is 5s)
        try {
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        service.stop();
        
        assertTrue(pendingBatches.size() > 0, "Should have created batches");
    }

    @Test
    void filterByEnabledMetrics() {
        service = new ObservationService(pendingBatches, descriptor);
        
        Collector enabledCollector = new Collector() {
            @Override
            public MetricDescriptor getDescriptor() {
                return MetricDescriptor.newBuilder()
                    .setName("test.metric")
                    .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                    .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                    .build();
            }

            @Override
            public List<Observation> collect() {
                return List.of(Observation.newBuilder()
                    .setMetric("test.metric")
                    .setValue(Value.newBuilder().setDoubleValue(1.0).build())
                    .build());
            }
        };

        Collector disabledCollector = new Collector() {
            @Override
            public MetricDescriptor getDescriptor() {
                return MetricDescriptor.newBuilder()
                    .setName("disabled.metric")
                    .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                    .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                    .build();
            }

            @Override
            public List<Observation> collect() {
                return List.of(Observation.newBuilder()
                    .setMetric("disabled.metric")
                    .setValue(Value.newBuilder().setDoubleValue(2.0).build())
                    .build());
            }
        };

        service.start(List.of(enabledCollector, disabledCollector), descriptor);
        
        // Wait for collection and flush (default flush interval is 5s)
        try {
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        service.stop();
        
        for (ObservationBatch batch : pendingBatches.values()) {
            for (Observation obs : batch.getObservationsList()) {
                assertEquals("test.metric", obs.getMetric());
            }
        }
    }

    @Test
    void doubleStartPrevented() {
        service = new ObservationService(pendingBatches, descriptor);
        service.start(List.of(), descriptor);
        service.start(List.of(), descriptor);
        service.stop();
    }

    @Test
    void backpressureDropsWhenFull() {
        service = new ObservationService(pendingBatches, descriptor);
        
        Collector floodCollector = new Collector() {
            @Override
            public MetricDescriptor getDescriptor() {
                return MetricDescriptor.newBuilder()
                    .setName("test.metric")
                    .setValueType(ValueType.VALUE_TYPE_DOUBLE)
                    .setMetricType(MetricType.METRIC_TYPE_GAUGE)
                    .build();
            }

            @Override
            public List<Observation> collect() {
                return List.of(
                    Observation.newBuilder().setMetric("test.metric").setValue(Value.newBuilder().setDoubleValue(1.0).build()).build(),
                    Observation.newBuilder().setMetric("test.metric").setValue(Value.newBuilder().setDoubleValue(2.0).build()).build()
                );
            }
        };

        service.start(List.of(floodCollector), descriptor);
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        service.stop();
        
        assertTrue(service.getQueueSize() <= 10);
    }
}