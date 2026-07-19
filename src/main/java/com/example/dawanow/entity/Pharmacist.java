package com.example.dawanow.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("PHARMACIST")
@Getter
@Setter
@NoArgsConstructor
public class Pharmacist extends User {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacy_id")
    private Pharmacy pharmacy;

    @OneToOne(mappedBy = "adminPharmacist")
    private Pharmacy administeredPharmacy;

    @OneToMany(mappedBy = "pharmacist")
    private List<Order> orders = new ArrayList<>();

}
