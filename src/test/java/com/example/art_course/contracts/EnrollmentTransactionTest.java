package com.example.art_course.contracts;

import com.example.art_course.modules.payment.service.EnrollmentGrantService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EnrollmentTransactionTest {
    @Test
    void grantAndRevokeJoinTheCallerTransaction() throws Exception {
        for (String method : new String[]{"grantAccess", "revokeAccess"}) {
            Transactional tx = DefaultEnrollmentContract.class
                    .getMethod(method, UUID.class, UUID.class, UUID.class).getAnnotation(Transactional.class);
            assertNotNull(tx);
            assertEquals(Propagation.REQUIRED, tx.propagation());
        }
    }

    @Test
    void failedPostPaymentGrantIsLeftForTheRepairJob() {
        RuntimeException failure = new RuntimeException("enrollment failed");
        var service = new EnrollmentGrantService(mock(IEnrollmentContract.class));
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> service.recoverGrantAccess(failure, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())));
    }
}
