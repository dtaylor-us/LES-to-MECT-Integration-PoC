package com.miso.les.web;

import com.miso.les.domain.LMREnrollment;
import com.miso.les.domain.LMRWithdrawEligibility;
import com.miso.les.service.CreateEnrollmentRequest;
import com.miso.les.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/lmrs")
@Tag(name = "LMR Enrollment", description = "Load enrollment lifecycle and withdrawal")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    @Operation(summary = "List all enrollments (newest first)")
    public ResponseEntity<List<LMREnrollment>> list() {
        return ResponseEntity.ok(enrollmentService.listAll());
    }

    @PostMapping
    @Operation(summary = "Create LMR enrollment (DRAFT)")
    public ResponseEntity<LMREnrollment> create(@Valid @RequestBody CreateEnrollmentRequest request) {
        LMREnrollment created = enrollmentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit enrollment for approval")
    public ResponseEntity<LMREnrollment> submit(@PathVariable("id") String lmrId) {
        LMREnrollment updated = enrollmentService.submit(lmrId);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve enrollment; publishes lmr.approved.v1 to Kafka")
    public ResponseEntity<LMREnrollment> approve(@PathVariable("id") String lmrId) {
        LMREnrollment updated = enrollmentService.approve(lmrId);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Request withdrawal; checks local eligibility, emits lmr.withdraw.requested.v1 if allowed")
    public ResponseEntity<LMREnrollment> withdraw(@PathVariable("id") String lmrId) {
        LMREnrollment updated = enrollmentService.withdraw(lmrId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/withdraw-eligibility")
    @Operation(
            summary = "Get withdrawal eligibility for UI",
            description = "From MECT via Kafka. Use canWithdraw to decide whether to show the Withdraw button. "
                    + "When canWithdraw is false, display 'message' to the user (from MECT; LES does not map codes). "
                    + "Returns 200 with a default (canWithdraw=false) when enrollment exists but MECT has not yet sent eligibility."
    )
    public ResponseEntity<?> getWithdrawEligibility(@PathVariable("id") String lmrId) {
        Optional<LMRWithdrawEligibility> el = enrollmentService.getEligibility(lmrId);
        if (el.isPresent()) {
            LMRWithdrawEligibility e = el.get();
            String message = e.getReason() != null ? e.getReason() : "";
            return ResponseEntity.ok(Map.of(
                    "lmrId", e.getLmrId(),
                    "planningYear", e.getPlanningYear(),
                    "canWithdraw", e.isCanWithdraw(),
                    "message", message,
                    "updatedAt", e.getUpdatedAt()
            ));
        }
        // Eligibility not in LES yet; if enrollment exists, return 200 with default so UI does not 404
        return enrollmentService.getByLmrId(lmrId)
                .map(en -> ResponseEntity.ok(Map.<String, Object>of(
                        "lmrId", en.getLmrId(),
                        "planningYear", en.getPlanningYear(),
                        "canWithdraw", false,
                        "message", "Eligibility not yet available from MECT.",
                        "updatedAt", en.getUpdatedAt()
                )))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "LMR not found")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get enrollment by LMR ID")
    public ResponseEntity<LMREnrollment> get(@PathVariable("id") String lmrId) {
        return enrollmentService.getByLmrId(lmrId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
