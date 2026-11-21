package com.platform.uop.agent.sdk;

import com.platform.uop.proto.v1.MetricDescriptor;
import com.platform.uop.proto.v1.Observation;

import java.util.List;

public interface Collector {

  MetricDescriptor getDescriptor();

  List<Observation> collect();
}
