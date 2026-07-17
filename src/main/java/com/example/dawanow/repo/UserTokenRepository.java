package com.example.dawanow.repo;

import com.example.dawanow.entity.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {
    @Query("""
SELECT ut
FROM UserToken ut
JOIN FETCH ut.user
WHERE ut.refreshToken = :token
""")
    Optional<UserToken> findByRefreshToken(@Param("token")String refreshToken);

}
