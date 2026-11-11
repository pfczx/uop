package com.platform.uop.monitor.repository;

import com.platform.uop.monitor.entity.AgentCredential;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentCredentialRepository
    extends JpaRepository<AgentCredential, UUID> {

  Optional<AgentCredential> findByMonitor_Id(UUID monitorId);

  Optional<AgentCredential> findByCredentialHash(String credentialHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select credential
      from AgentCredential credential
      where credential.registrationTokenHash = :tokenHash
      """)
  Optional<AgentCredential> findByRegistrationTokenHashForUpdate(
      @Param("tokenHash") String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select credential
      from AgentCredential credential
      where credential.monitor.id = :monitorId
      """)
  Optional<AgentCredential> findByMonitorIdForUpdate(
      @Param("monitorId") UUID monitorId);
}
