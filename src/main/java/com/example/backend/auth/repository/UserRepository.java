package com.example.backend.auth.repository;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.entity.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByRole(Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.role = :role order by user.id")
    List<User> findAllByRoleForUpdate(@Param("role") Role role);
}
