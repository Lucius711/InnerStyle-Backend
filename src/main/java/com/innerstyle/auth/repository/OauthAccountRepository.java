package com.innerstyle.auth.repository;

import com.innerstyle.auth.entity.OauthAccount;
import com.innerstyle.auth.entity.enums.OauthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OauthAccountRepository extends JpaRepository<OauthAccount, UUID> {

    Optional<OauthAccount> findByProviderAndProviderUserId(OauthProvider provider, String providerUserId);
}
