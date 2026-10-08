package com.mindfulcart.assistant.repository;

import com.mindfulcart.assistant.model.AccountToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
  Optional<AccountToken> findByTokenAndTypeAndUsedFalse(String token, String type);
}
