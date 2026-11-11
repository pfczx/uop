package com.platform.uop.monitor.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.platform.uop.monitor.dto.CreateMonitorRequest;
import com.platform.uop.monitor.dto.MonitorResponse;
import com.platform.uop.monitor.dto.RemoveMonitorRequest;
import com.platform.uop.monitor.dto.SetActiveRequest;
import com.platform.uop.monitor.dto.SetInactiveRequest;
import com.platform.uop.monitor.dto.UpdateMonitorRequest;
import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.workspace.entity.Workspace;
import com.platform.uop.workspace.repository.WorkspaceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class MonitorService {

  private final MonitorRepository monitorRepository;
  private final WorkspaceRepository workspaceRepository;
  private final AgentCredentialService agentCredentialService;
  private final AgentConnectionService agentConnectionService;

  @Value("${installer-url}")
  private String installerUrl;

  @Value("${platform-url}")
  private String platformUrl;

  private Workspace getWorkspace(UUID workspaceId) {
    return workspaceRepository.findById(workspaceId)
        .orElseThrow(() -> new ResponseStatusException(
            NOT_FOUND, "Workspace not found"));
  }

  private Monitor getMonitor(UUID monitorId) {
    return monitorRepository.findById(monitorId)
        .orElseThrow(() -> new ResponseStatusException(
            NOT_FOUND, "Monitor not found"));
  }

  public MonitorResponse create(
      CreateMonitorRequest request,
      UUID workspaceId) {

    Workspace workspace = getWorkspace(workspaceId);

    Monitor monitor = new Monitor(workspace, request.descriptor());
    monitor = monitorRepository.save(monitor);

    return MonitorResponse.from(monitor);
  }

  public MonitorResponse update(
      UpdateMonitorRequest request,
      UUID workspaceId,
      UUID monitorId) {

    getWorkspace(workspaceId);
    Monitor monitor = getMonitor(monitorId);

    monitor.updateDescriptor(request.descriptor());
    monitorRepository.save(monitor);

    synchronizeDescriptorAfterCommit(monitor.getId(),
        monitor.getDescriptor());

    return MonitorResponse.from(monitor);
  }

  public void remove(RemoveMonitorRequest request, UUID monitorId) {
    Monitor monitor = getMonitor(monitorId);

    monitorRepository.delete(monitor);

    agentCredentialService.revokeAgentCredentials(monitorId);

    afterCommit(() -> {
      agentConnectionService.closeMonitorConnection(monitorId);
    });
  }

  public MonitorResponse setActive(
      SetActiveRequest request,
      UUID monitorId) {

    Monitor monitor = getMonitor(monitorId);
    monitor.setActive();
    monitorRepository.save(monitor);

    synchronizeDescriptorAfterCommit(monitor.getId(),
        monitor.getDescriptor());

    return MonitorResponse.from(monitor);
  }

  public MonitorResponse setInactive(
      SetInactiveRequest request,
      UUID monitorId) {

    Monitor monitor = getMonitor(monitorId);
    monitor.setInactive();
    monitorRepository.save(monitor);

    synchronizeDescriptorAfterCommit(monitor.getId(),
        monitor.getDescriptor());

    return MonitorResponse.from(monitor);
  }

  public String generateInstallationCommand(UUID monitorId) {
    Monitor monitor = getMonitor(monitorId);

    String registrationToken = agentCredentialService.createRegistrationToken(monitor.getId());

    // TODO: instalation script
    return "curl -fsSL '" + installerUrl
        + "' | sh -s -- --platform '" + platformUrl
        + "' --registration-token '" + registrationToken + "'";
  }

  public MonitorDescriptor getDescriptor(UUID monitorId) {
    return getMonitor(monitorId).getDescriptor();
  }

  private void synchronizeDescriptorAfterCommit(
      UUID monitorId,
      MonitorDescriptor descriptor) {

    afterCommit(() -> agentConnectionService.sendDescriptor(monitorId, descriptor));
  }

  private void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }
}
