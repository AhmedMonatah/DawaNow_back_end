package com.example.dawanow.service;

import com.example.dawanow.dtos.request.UpdateUserRequest;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.dtos.response.UserResponse;
import com.example.dawanow.entity.User;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.UserMapper;
import com.example.dawanow.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final CurrentPharmacistProvider currentPharmacistProvider;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        return userMapper.toResponse(currentPharmacistProvider.getCurrentUser());
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<UserResponse> getAllUsers(Pageable pageable) {
        return PaginatedResponse.from(userRepository.findAll(pageable).map(userMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return userMapper.toResponse(findUser(id));
    }

    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findUser(id);
        if (request.email() != null) {
            User existing = userRepository.findByEmail(request.email());
            if (existing != null && !existing.getId().equals(id)) {
                throw new IllegalArgumentException("Email already in use");
            }
            user.setEmail(request.email());
        }
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.homeAddress() != null) {
            user.setHomeAddress(request.homeAddress());
        }
        if (request.dob() != null) {
            user.setDob(request.dob());
        }
        return userMapper.toResponse(user);
    }

    public void deleteUser(Long id) {
        User user = findUser(id);
        userRepository.delete(user);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
