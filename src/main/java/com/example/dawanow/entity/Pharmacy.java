package com.example.dawanow.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pharmacy")
@Getter
@Setter
@NoArgsConstructor
public class Pharmacy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "address")
    private String address;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "license_document_path", nullable = false)
    private String licenseDocumentPath;

    /** The pharmacist who administers this pharmacy; not a platform ADMIN user. */
    @OneToOne
    @JoinColumn(name = "admin_pharmacist_id", nullable = false, unique = true)
    private Pharmacist adminPharmacist;

    @OneToMany(mappedBy = "pharmacy")
    private List<Pharmacist> pharmacists = new ArrayList<>();

    @OneToMany(mappedBy = "pharmacy")
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "pharmacy")
    private List<PharmacyOffer> offers = new ArrayList<>();
}
