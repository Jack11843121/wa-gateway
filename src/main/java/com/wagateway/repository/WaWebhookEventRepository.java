package com.wagateway.repository;

import com.wagateway.model.WaWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaWebhookEventRepository extends JpaRepository<WaWebhookEvent, Long> {

    boolean existsByDedupKey(String dedupKey);

    List<WaWebhookEvent> findByStatusInOrderByCreatedAtAsc(List<Integer> statuses);
}