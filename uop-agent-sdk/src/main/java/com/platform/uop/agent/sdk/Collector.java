package com.platform.uop.agent.sdk;

import com.platform.uop.proto.v1.MetricDescriptor;
import com.platform.uop.proto.v1.Observation;

import java.util.List;
import java.time.Duration;

public interface Collector {

  MetricDescriptor getDescriptor();

  List<Observation> collect();

  default Duration getInterval() {
    return Duration.ofSeconds(1);
  }
}
