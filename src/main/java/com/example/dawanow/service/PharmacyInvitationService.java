package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreatePharmacyInvitationRequest;
import com.example.dawanow.dtos.response.PharmacyInvitationResponse;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.PharmacyInvitation;
import com.example.dawanow.entity.PharmacyInvitationStatus;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.PharmacyInvitationMapper;
import com.example.dawanow.repo.PharmacistRepository;
import com.example.dawanow.repo.PharmacyInvitationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PharmacyInvitationService {
    private final PharmacyService pharmacyService;
    private final PharmacistRepository pharmacistRepository;
    private final PharmacyInvitationRepository invitationRepository;
    private final CurrentPharmacistProvider currentPharmacistProvider;
    private final PharmacyInvitationMapper invitationMapper;

    public PharmacyInvitationResponse invite(Long pharmacyId, CreatePharmacyInvitationRequest request) {
        Pharmacy pharmacy = pharmacyService.findPharmacy(pharmacyId);
        pharmacyService.requireCurrentAdmin(pharmacy);
        Pharmacist pharmacist = pharmacistRepository.findById(request.pharmacistId())
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacist not found"));
        pharmacyService.ensureNotAssignedToAnyPharmacy(pharmacist);
        invitationRepository.findByPharmacyIdAndPharmacistIdAndStatus(pharmacyId, pharmacist.getId(), PharmacyInvitationStatus.PENDING)
                .ifPresent(invitation -> { throw new IllegalArgumentException("A pending invitation already exists for this pharmacist"); });
        PharmacyInvitation invitation = new PharmacyInvitation();
        invitation.setPharmacy(pharmacy);
        invitation.setPharmacist(pharmacist);
        return invitationMapper.toResponse(invitationRepository.save(invitation));
    }

    @Transactional(readOnly = true)
    public List<PharmacyInvitationResponse> getMyPendingInvitations() {
        Pharmacist pharmacist = currentPharmacistProvider.get();
        return invitationRepository.findByPharmacistIdAndStatus(pharmacist.getId(), PharmacyInvitationStatus.PENDING)
                .stream().map(invitationMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PharmacyInvitationResponse> getPendingInvitationsForPharmacy(Long pharmacyId) {
        Pharmacy pharmacy = pharmacyService.findPharmacy(pharmacyId);
        pharmacyService.requireCurrentAdmin(pharmacy);
        return invitationRepository.findByPharmacyIdAndStatus(pharmacyId, PharmacyInvitationStatus.PENDING)
                .stream().map(invitationMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PharmacyInvitationResponse> getPendingInvitationsForMyPharmacy() {
        Pharmacist current = currentPharmacistProvider.get();
        Pharmacy pharmacy = current.getPharmacy();
        if (pharmacy == null) {
            throw new IllegalArgumentException("You are not associated with any pharmacy");
        }
        if (!pharmacy.getAdminPharmacist().getId().equals(current.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the pharmacy admin can view pending invitations");
        }
        return invitationRepository.findByPharmacyIdAndStatus(pharmacy.getId(), PharmacyInvitationStatus.PENDING)
                .stream().map(invitationMapper::toResponse).toList();
    }

    public PharmacyInvitationResponse accept(Long invitationId) {
        PharmacyInvitation invitation = getPendingInvitationForCurrentPharmacist(invitationId);
        pharmacyService.ensureNotAssignedToAnyPharmacy(invitation.getPharmacist());
        invitation.getPharmacist().setPharmacy(invitation.getPharmacy());
        invitation.setStatus(PharmacyInvitationStatus.ACCEPTED);
        return invitationMapper.toResponse(invitation);
    }

    public PharmacyInvitationResponse decline(Long invitationId) {
        PharmacyInvitation invitation = getPendingInvitationForCurrentPharmacist(invitationId);
        invitation.setStatus(PharmacyInvitationStatus.DECLINED);
        return invitationMapper.toResponse(invitation);
    }

    public void delete(Long invitationId) {
        PharmacyInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy invitation not found"));
        pharmacyService.requireCurrentAdmin(invitation.getPharmacy());
        if (invitation.getStatus() != PharmacyInvitationStatus.PENDING) {
            throw new IllegalArgumentException("Only a pending invitation can be deleted");
        }
        invitationRepository.delete(invitation);
    }

    private PharmacyInvitation getPendingInvitationForCurrentPharmacist(Long invitationId) {
        Pharmacist current = currentPharmacistProvider.get();
        PharmacyInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy invitation not found"));
        if (!invitation.getPharmacist().getId().equals(current.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("This invitation belongs to another pharmacist");
        }
        if (invitation.getStatus() != PharmacyInvitationStatus.PENDING) {
            throw new IllegalArgumentException("This invitation is no longer pending");
        }
        return invitation;
    }

}
