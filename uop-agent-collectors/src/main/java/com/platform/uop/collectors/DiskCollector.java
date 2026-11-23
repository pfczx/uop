package com.platform.uop.collectors;

import com.platform.uop.agent.sdk.Collector;
import com.platform.uop.proto.v1.MetricDescriptor;
import com.platform.uop.proto.v1.MetricType;
import com.platform.uop.proto.v1.Observation;
import com.platform.uop.proto.v1.Value;
import com.platform.uop.proto.v1.ValueType;

import oshi.SystemInfo;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.List;

public class DiskCollector implements Collector {

    private static final MetricDescriptor DISK_USAGE = MetricDescriptor.newBuilder()
        .setName("disk.usage_percent")
        .setDescription("Disk usage percentage")
        .setValueType(ValueType.VALUE_TYPE_DOUBLE)
        .setMetricType(MetricType.METRIC_TYPE_GAUGE)
        .setUnit("percent")
        .build();

    private static final MetricDescriptor DISK_FREE = MetricDescriptor.newBuilder()
        .setName("disk.free_bytes")
        .setDescription("Free disk space in bytes")
        .setValueType(ValueType.VALUE_TYPE_INT64)
        .setMetricType(MetricType.METRIC_TYPE_GAUGE)
        .setUnit("bytes")
        .build();

    private static final MetricDescriptor DISK_TOTAL = MetricDescriptor.newBuilder()
        .setName("disk.total_bytes")
        .setDescription("Total disk space in bytes")
        .setValueType(ValueType.VALUE_TYPE_INT64)
        .setMetricType(MetricType.METRIC_TYPE_GAUGE)
        .setUnit("bytes")
        .build();

    private final SystemInfo systemInfo = new SystemInfo();

    @Override
    public MetricDescriptor getDescriptor() {
        return DISK_USAGE;
    }

    @Override
    public List<Observation> collect() {
        List<Observation> observations = new ArrayList<>();
        OperatingSystem os = systemInfo.getOperatingSystem();
        FileSystem fileSystem = os.getFileSystem();

        for (OSFileStore store : fileSystem.getFileStores()) {
            String mount = store.getMount();
            long total = store.getTotalSpace();
            long free = store.getUsableSpace();
            long used = total - free;
            double usagePercent = total > 0 ? (double) (total - free) / total * 100.0 : 0.0;

            observations.add(Observation.newBuilder()
                .setMetric("disk.usage_percent")
                .setValue(Value.newBuilder().setDoubleValue(usagePercent).build())
                .build());

            observations.add(Observation.newBuilder()
                .setMetric("disk.free_bytes")
                .setValue(Value.newBuilder().setIntValue(free).build())
                .build());

            observations.add(Observation.newBuilder()
                .setMetric("disk.total_bytes")
                .setValue(Value.newBuilder().setIntValue(total).build())
                .build());
        }

        return observations;
    }
}