package com.wagateway.repository;

import com.wagateway.model.WaPhoneNumber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaPhoneNumberRepository extends JpaRepository<WaPhoneNumber, Long> {

    Optional<WaPhoneNumber> findByPhoneNumberId(String phoneNumberId);

    Optional<WaPhoneNumber> findByDisplayPhoneNumber(String displayPhoneNumber);

    List<WaPhoneNumber> findByAccountIdAndOnlineTrue(Long accountId);

    List<WaPhoneNumber> findByAccountId(Long accountId);
}