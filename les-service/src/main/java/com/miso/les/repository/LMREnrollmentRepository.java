package com.miso.les.repository;

import com.miso.les.domain.EnrollmentStatus;
import com.miso.les.domain.LMREnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LMREnrollmentRepository extends JpaRepository<LMREnrollment, Long> {

    Optional<LMREnrollment> findByLmrId(String lmrId);

    boolean existsByLmrId(String lmrId);

    /** For admin view: enrollments where user requested withdraw but MECT rejected (edge case). */
    List<LMREnrollment> findByStatusOrderByWithdrawRejectedAtDesc(EnrollmentStatus status);

    List<LMREnrollment> findAllByOrderByUpdatedAtDesc();
}
