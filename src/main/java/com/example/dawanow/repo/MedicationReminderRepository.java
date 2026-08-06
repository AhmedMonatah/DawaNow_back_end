package com.example.dawanow.repo;

import com.example.dawanow.entity.MedicationReminder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationReminderRepository extends JpaRepository<MedicationReminder, Long> {

    List<MedicationReminder> findByUserIdAndActiveTrueOrderByIdAsc(Long userId);

    List<MedicationReminder> findByActiveTrue();
}
