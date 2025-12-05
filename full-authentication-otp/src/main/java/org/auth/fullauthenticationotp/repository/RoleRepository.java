package org.auth.fullauthenticationotp.repository;

import org.auth.fullauthenticationotp.model.Role;
import org.auth.fullauthenticationotp.model.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleType name);
}