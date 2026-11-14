package com.platform.uop.monitor.dto;

import com.platform.uop.proto.v1.MonitorDescriptor;

import jakarta.validation.constraints.NotNull;

public record UpdateMonitorRequest(

    @NotNull MonitorDescriptor descriptor

) {

}
