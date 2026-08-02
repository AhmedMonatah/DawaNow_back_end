package com.example.dawanow.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dawanow.entity.notification.Notification;
import com.example.dawanow.entity.notification.Notification.Category;
import com.example.dawanow.entity.notification.NotificationRecipient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class NotificationRecipientRepositoryTest {

    @Autowired
    private NotificationRecipientRepository repository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void marksAllAsReadForPharmacistUsingNativeNow() {
        Notification n1 = notificationRepository.save(
                new Notification(Category.REQUEST_IN_AREA, "title1", "body1", Map.of("k", 1)));
        Notification n2 = notificationRepository.save(
                new Notification(Category.REQUEST_IN_AREA, "title2", "body2", Map.of("k", 2)));

        NotificationRecipient r1 = new NotificationRecipient(n1, 1L);
        r1.markSent();
        repository.save(r1);

        NotificationRecipient r2 = new NotificationRecipient(n2, 1L);
        r2.markSent();
        repository.save(r2);

        NotificationRecipient other = new NotificationRecipient(
                notificationRepository.save(
                        new Notification(Category.PHARMACY_INVITATION, "title3", "body3", null)),
                2L);
        other.markSent();
        repository.save(other);

        repository.markAllReadForPharmacist(1L);

        assertThat(repository.findById(r1.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationRecipient.Status.READ);
        assertThat(repository.findById(r1.getId()).orElseThrow().getReadAt()).isNotNull();
        assertThat(repository.findById(r2.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationRecipient.Status.READ);
        assertThat(repository.findById(r2.getId()).orElseThrow().getReadAt()).isNotNull();
        assertThat(repository.findById(other.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationRecipient.Status.SENT);
    }

    @Test
    void doesNotModifyAlreadyRead() {
        Notification notification = notificationRepository.save(
                new Notification(Category.PHARMACY_INVITATION, "Invitation", "body", null));

        NotificationRecipient r = new NotificationRecipient(notification, 1L);
        r.markSent();
        repository.save(r);
        repository.markAllReadForPharmacist(1L);
        assertThat(repository.findById(r.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationRecipient.Status.READ);
    }
}
