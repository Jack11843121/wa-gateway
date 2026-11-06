package com.wagateway.repository;

import com.wagateway.model.WaMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaMessageRepository extends JpaRepository<WaMessage, Long> {

    Optional<WaMessage> findByRequestId(String requestId);

    Optional<WaMessage> findByMessageId(String messageId);

    List<WaMessage> findByStatusOrderByCreatedAtAsc(String status);
}