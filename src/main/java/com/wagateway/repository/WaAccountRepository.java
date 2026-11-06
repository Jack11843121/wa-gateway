package com.wagateway.repository;

import com.wagateway.model.WaAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaAccountRepository extends JpaRepository<WaAccount, Long> {

    Optional<WaAccount> findByWabaId(String wabaId);

    Optional<WaAccount> findByAppId(String appId);

    boolean existsByWabaId(String wabaId);

    List<WaAccount> findAllByStatusOrderByIdDesc(Integer status);
}