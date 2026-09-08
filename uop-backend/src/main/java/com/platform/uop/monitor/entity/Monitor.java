package com.platform.uop.monitor.entity;

import java.util.List;

import com.platform.uop.monitor.v1.ConfigureRequest;
import com.platform.uop.monitor.v1.ConfigureResponse;
import com.platform.uop.monitor.v1.ExportRequest;
import com.platform.uop.monitor.v1.ExportResponse;
import com.platform.uop.monitor.v1.MonitorConfiguration;
import com.platform.uop.monitor.v1.MonitorDescriptor;
import com.platform.uop.monitor.v1.Observation;

public abstract class Monitor {

 protected abstract MonitorDescriptor getDescriptor();



}

