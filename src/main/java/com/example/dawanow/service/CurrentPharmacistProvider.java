package com.example.dawanow.service;

import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.User;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentPharmacistProvider {
    private final UserRepository userRepository;

    public Pharmacist get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }

        User user = userRepository.findByEmail(authentication.getName());
        if (user == null) {
            throw new ResourceNotFoundException("Current user not found");
        }
        if (!(user instanceof Pharmacist pharmacist)) {
            throw new AccessDeniedException("A pharmacist account is required");
        }
        return pharmacist;
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        User user = userRepository.findByEmail(authentication.getName());
        if (user == null) {
            throw new ResourceNotFoundException("Current user not found");
        }
        return user;
    }
}
