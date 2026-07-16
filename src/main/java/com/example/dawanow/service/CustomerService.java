package com.example.dawanow.service;

import com.example.dawanow.dtos.request.UpdateCustomerProfileRequest;
import com.example.dawanow.dtos.response.CustomerResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.entity.Customer;
import com.example.dawanow.entity.User;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.CustomerMapper;
import com.example.dawanow.repo.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CurrentUserProvider currentUserProvider;
    private final CustomerMapper customerMapper;

    @Transactional(readOnly = true)
    public CustomerResponse getCurrentCustomer() {
        return customerMapper.toResponse(getCurrentCustomerEntity());
    }

    public CustomerResponse updateCurrentCustomer(UpdateCustomerProfileRequest request) {
        Customer customer = getCurrentCustomerEntity();
        if (request.homeAddress() != null) {
            customer.setHomeAddress(request.homeAddress());
        }
        if (request.dob() != null) {
            customer.setDob(request.dob());
        }
        return customerMapper.toResponse(customer);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CustomerResponse> getAllCustomers(Pageable pageable) {
        return PaginatedResponse.from(customerRepository.findAll(pageable).map(customerMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long id) {
        return customerMapper.toResponse(findCustomer(id));
    }

    public void deleteCustomer(Long id) {
        Customer customer = findCustomer(id);
        customerRepository.delete(customer);
    }

    private Customer getCurrentCustomerEntity() {
        User user = currentUserProvider.get();
        if (!(user instanceof Customer customer)) {
            throw new AccessDeniedException("A customer account is required");
        }
        return customer;
    }

    private Customer findCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }
}
