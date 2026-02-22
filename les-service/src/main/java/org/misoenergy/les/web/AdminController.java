package org.misoenergy.les.web;

import org.misoenergy.les.domain.LMREnrollment;
import org.misoenergy.les.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin endpoints to see edge cases (e.g. withdrawal rejected by MECT after LES allowed the request).
 * Standard workflow gives immediate feedback via eligibility; these views help admins act when
 * the rare "button was shown, then state changed in MECT" case occurs.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin visibility into withdrawal rejections and edge cases")
public class AdminController {

    private final EnrollmentService enrollmentService;

    public AdminController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/withdraw-rejections")
    @Operation(
            summary = "List withdrawals rejected by MECT",
            description = "Enrollments where the user requested withdrawal (button was shown) but MECT rejected, "
                    + "e.g. because state changed in MECT between eligibility check and request. "
                    + "Visible so admins can act; if this happens often in production, automatic reconciliation may be added later."
    )
    public ResponseEntity<List<WithdrawRejectionDto>> listWithdrawRejections() {
        List<LMREnrollment> list = enrollmentService.listWithdrawRejected();
        List<WithdrawRejectionDto> body = list.stream()
                .map(e -> new WithdrawRejectionDto(
                        e.getLmrId(),
                        e.getPlanningYear(),
                        e.getLmrName(),
                        e.getMarketParticipantName(),
                        e.getWithdrawRejectReason(),
                        e.getWithdrawRejectedAt(),
                        e.getUpdatedAt()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(body);
    }

    /** DTO for admin list: no need to expose full entity. */
    public record WithdrawRejectionDto(
            String lmrId,
            String planningYear,
            String lmrName,
            String marketParticipantName,
            String message,
            Instant withdrawRejectedAt,
            Instant updatedAt
    ) {}
}
