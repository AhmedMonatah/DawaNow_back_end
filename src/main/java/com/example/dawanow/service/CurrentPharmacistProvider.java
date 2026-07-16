package com.example.dawanow.service;

import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentPharmacistProvider {

    private final CurrentUserProvider currentUserProvider;

    public Pharmacist get() {
        User user = currentUserProvider.get();
        if (!(user instanceof Pharmacist pharmacist)) {
            throw new AccessDeniedException("A pharmacist account is required");
        }
        return pharmacist;
    }
}
