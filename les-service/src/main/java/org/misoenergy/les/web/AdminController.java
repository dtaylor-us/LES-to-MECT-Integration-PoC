package org.misoenergy.les.web;

import org.misoenergy.les.domain.LMREnrollment;
import org.misoenergy.les.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin endpoints to see edge cases (e.g. withdrawal rejected by MECT after LES allowed the request)
 * and to correct enrollment state where necessary.
 * Write endpoints (POST) require the ADMIN role (HTTP Basic Auth); read endpoints are open.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin visibility into withdrawal rejections and state correction")
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

    @PostMapping("/lmrs/{id}/correct-withdrawal")
    @Operation(
            summary = "Correct enrollment state after a rejected withdrawal (admin only)",
            description = "Resets a WITHDRAW_REJECTED enrollment back to APPROVED so it remains active. "
                    + "Clears the rejection reason and timestamp. "
                    + "Only applies to enrollments currently in WITHDRAW_REJECTED status. "
                    + "Requires ADMIN role (HTTP Basic Auth)."
    )
    public ResponseEntity<LMREnrollment> correctWithdrawal(@PathVariable("id") String lmrId) {
        LMREnrollment updated = enrollmentService.correctRejectedWithdrawal(lmrId);
        return ResponseEntity.ok(updated);
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
