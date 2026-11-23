package com.platform.uop.collectors;

import com.platform.uop.agent.sdk.Collector;
import com.platform.uop.proto.v1.MetricDescriptor;
import com.platform.uop.proto.v1.Observation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiskCollectorTest {

    @Test
    void collectReturnsObservations() {
        DiskCollector collector = new DiskCollector();
        
        List<Observation> observations = collector.collect();
        
        assertNotNull(observations);
        assertFalse(observations.isEmpty(), "Should collect at least one filesystem");
        
        for (Observation obs : observations) {
            assertNotNull(obs.getMetric());
            assertFalse(obs.getMetric().isBlank());
            assertTrue(obs.getMetric().startsWith("disk."));
        }
    }

    @Test
    void getDescriptorReturnsExpected() {
        DiskCollector collector = new DiskCollector();
        
        MetricDescriptor descriptor = collector.getDescriptor();
        
        assertEquals("disk.usage_percent", descriptor.getName());
        assertEquals("percent", descriptor.getUnit());
    }
}