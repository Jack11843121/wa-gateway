package com.wagateway.repository;

import com.wagateway.model.WaTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaTemplateRepository extends JpaRepository<WaTemplate, Long> {

    List<WaTemplate> findByAccountIdOrderByIdDesc(Long accountId);

    void deleteByAccountId(Long accountId);
}