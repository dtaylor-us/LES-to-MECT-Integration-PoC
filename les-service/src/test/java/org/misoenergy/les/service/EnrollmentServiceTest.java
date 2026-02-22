package org.misoenergy.les.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.misoenergy.les.domain.EnrollmentStatus;
import org.misoenergy.les.domain.LMREnrollment;
import org.misoenergy.les.outbox.OutboxRepository;
import org.misoenergy.les.repository.LMREnrollmentRepository;
import org.misoenergy.les.repository.LMRWithdrawEligibilityRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private LMREnrollmentRepository enrollmentRepository;
    @Mock
    private LMRWithdrawEligibilityRepository eligibilityRepository;
    @Mock
    private OutboxRepository outboxRepository;

    private EnrollmentService service;

    @BeforeEach
    void setUp() {
        service = new EnrollmentService(enrollmentRepository, eligibilityRepository, outboxRepository, new ObjectMapper());
    }

    // --- correctRejectedWithdrawal ---

    @Test
    void correctRejectedWithdrawal_resetsStatusToApproved() {
        LMREnrollment enrollment = withdrawRejectedEnrollment("LMR-001");
        when(enrollmentRepository.findByLmrId("LMR-001")).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LMREnrollment result = service.correctRejectedWithdrawal("LMR-001");

        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
    }

    @Test
    void correctRejectedWithdrawal_clearsRejectionFields() {
        LMREnrollment enrollment = withdrawRejectedEnrollment("LMR-002");
        when(enrollmentRepository.findByLmrId("LMR-002")).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LMREnrollment result = service.correctRejectedWithdrawal("LMR-002");

        assertThat(result.getWithdrawRejectReason()).isNull();
        assertThat(result.getWithdrawRejectedAt()).isNull();
    }

    @Test
    void correctRejectedWithdrawal_persistsTheChange() {
        LMREnrollment enrollment = withdrawRejectedEnrollment("LMR-003");
        when(enrollmentRepository.findByLmrId("LMR-003")).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.correctRejectedWithdrawal("LMR-003");

        ArgumentCaptor<LMREnrollment> captor = ArgumentCaptor.forClass(LMREnrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
    }

    @Test
    void correctRejectedWithdrawal_throwsNotFound_whenEnrollmentMissing() {
        when(enrollmentRepository.findByLmrId("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.correctRejectedWithdrawal("UNKNOWN"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("LMR not found");
    }

    @Test
    void correctRejectedWithdrawal_throwsConflict_whenStatusIsNotWithdrawRejected() {
        LMREnrollment enrollment = new LMREnrollment();
        enrollment.setLmrId("LMR-004");
        enrollment.setStatus(EnrollmentStatus.APPROVED);

        when(enrollmentRepository.findByLmrId("LMR-004")).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.correctRejectedWithdrawal("LMR-004"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("WITHDRAW_REJECTED");
    }

    @Test
    void correctRejectedWithdrawal_throwsConflict_whenStatusIsDraft() {
        LMREnrollment enrollment = new LMREnrollment();
        enrollment.setLmrId("LMR-005");
        enrollment.setStatus(EnrollmentStatus.DRAFT);

        when(enrollmentRepository.findByLmrId("LMR-005")).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.correctRejectedWithdrawal("LMR-005"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("WITHDRAW_REJECTED");
    }

    // --- helpers ---

    private LMREnrollment withdrawRejectedEnrollment(String lmrId) {
        LMREnrollment e = new LMREnrollment();
        e.setLmrId(lmrId);
        e.setStatus(EnrollmentStatus.WITHDRAW_REJECTED);
        e.setWithdrawRejectReason("A ZRC transaction exists.");
        e.setWithdrawRejectedAt(Instant.now());
        return e;
    }
}
