package com.bryan.fluxgate.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bryan.fluxgate.entity.ApiRequestLog;

@Repository
public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, UUID> {

}
