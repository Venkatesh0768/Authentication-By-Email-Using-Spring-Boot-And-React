package org.auth.fullauthenticationotp.config;

import lombok.RequiredArgsConstructor;
import org.auth.fullauthenticationotp.model.Role;
import org.auth.fullauthenticationotp.model.RoleType;
import org.auth.fullauthenticationotp.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        // Initialize roles if they don't exist
        for (RoleType roleType : RoleType.values()) {
            if (roleRepository.findByName(roleType).isEmpty()) {
                Role role = new Role();
                role.setName(roleType);
                roleRepository.save(role);
            }
        }
    }
}