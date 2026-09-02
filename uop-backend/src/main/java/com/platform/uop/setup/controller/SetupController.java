package com.platform.uop.setup.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.platform.uop.setup.dto.CreateAdminRequest;
import com.platform.uop.setup.dto.SetupResponse;
import com.platform.uop.setup.service.SetupService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;

    @PostMapping("/admin")
    public SetupResponse createAdmin(
        @Valid @RequestBody CreateAdminRequest request) {

        return setupService.createAdmin(request);
    }
}
