package com.platform.uop.monitor.dto;

import com.platform.uop.proto.v1.MonitorDescriptor;

import jakarta.validation.constraints.NotBlank;

public record CreateMonitorRequest(

    @NotBlank MonitorDescriptor descriptor

) {

}
