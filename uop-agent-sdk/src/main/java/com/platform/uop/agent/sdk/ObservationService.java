package com.platform.uop.agent.sdk;

import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.proto.v1.Observation;
import com.platform.uop.proto.v1.ObservationBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ObservationService {

  private final ConcurrentLinkedQueue<Observation> queue = new ConcurrentLinkedQueue<>();
  private final Map<UUID, ObservationBatch> pendingBatches;
  private final Set<String> enabledMetrics = ConcurrentHashMap.newKeySet();

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
      r -> { Thread t = new Thread(r, "observation-service"); t.setDaemon(true); return t; });

  private final Map<Collector, ScheduledFuture<?>> collectorTasks = new ConcurrentHashMap<>();

  private int batchSize = 100;
  private long flushIntervalSeconds = 5;
  private int maxQueueSize = 10000;

  private volatile boolean running = false;

  public ObservationService(Map<UUID, ObservationBatch> pendingBatches, MonitorDescriptor descriptor) {
    this.pendingBatches = pendingBatches;
    if (descriptor != null) {
      enabledMetrics.addAll(descriptor.getMetricsList().stream()
          .map(m -> m.getName())
          .toList());
    }
  }

  public void start(List<Collector> collectors, MonitorDescriptor descriptor) {
    if (running) return;
    running = true;

    if (descriptor != null) {
      enabledMetrics.clear();
      enabledMetrics.addAll(descriptor.getMetricsList().stream()
          .map(m -> m.getName())
          .toList());
    }

    for (Collector collector : collectors) {
      String metricName = collector.getDescriptor().getName();
      if (!enabledMetrics.contains(metricName)) continue;

      long intervalMillis = collector.getInterval().toMillis();
      if (intervalMillis <= 0) intervalMillis = 1000;

      ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(
          () -> {
            if (!running) return;
            collect(collector);
          },
          0, intervalMillis, TimeUnit.MILLISECONDS);
      collectorTasks.put(collector, task);
    }

    scheduler.scheduleAtFixedRate(
        this::flushObservations,
        flushIntervalSeconds, flushIntervalSeconds, TimeUnit.SECONDS);
  }

  private void collect(Collector collector) {
    List<Observation> collected = collector.collect();
    if (collected == null || collected.isEmpty()) return;

    for (Observation obs : collected) {
      if (queue.size() >= maxQueueSize) continue;
      queue.add(obs);
    }
  }

  private void flushObservations() {
    if (!running) return;

    while (!queue.isEmpty()) {
      int currentBatchSize = Math.min(queue.size(), batchSize);
      List<Observation> batchObservations = new ArrayList<>(currentBatchSize);

      for (int i = 0; i < currentBatchSize; i++) {
        Observation obs = queue.poll();
        if (obs == null) break;
        batchObservations.add(obs);
      }
      if (batchObservations.isEmpty()) break;

      UUID batchId = UUID.randomUUID();
      ObservationBatch batch = ObservationBatch.newBuilder()
          .setBatchId(batchId.toString())
          .addAllObservations(batchObservations)
          .build();
      pendingBatches.put(batchId, batch);
    }
  }

  public void stop() {
    running = false;

    for (ScheduledFuture<?> task : collectorTasks.values()) {
      task.cancel(false);
    }
    collectorTasks.clear();

    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
    flushObservations();
  }

  public boolean isRunning() { return running; }
  
  public int getQueueSize() { return queue.size(); }
}