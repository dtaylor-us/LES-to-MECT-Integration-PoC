package org.misoenergy.mect.web;

import org.misoenergy.mect.domain.BlockingFlag;
import org.misoenergy.mect.domain.LMR;
import org.misoenergy.mect.service.LMRService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Demo APIs to inspect LMR state and manipulate blocking flags (for PoC walkthrough).
 */
@RestController
@RequestMapping("/api/mect/lmrs")
@Tag(name = "MECT LMR", description = "Module E Capacity Tracking - LMR state and blocking flags")
public class LMRController {

    private final LMRService lmrService;

    public LMRController(LMRService lmrService) {
        this.lmrService = lmrService;
    }

    @GetMapping("/{planningYear}/{lmrId}")
    @Operation(summary = "Get LMR by planning year and LMR ID")
    public ResponseEntity<LMR> get(@PathVariable String planningYear, @PathVariable String lmrId) {
        return lmrService.findByLmrIdAndPlanningYear(lmrId, planningYear)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{planningYear}/{lmrId}/flags/{flag}/enable")
    @Operation(summary = "Enable a blocking flag (recomputes eligibility and publishes)")
    public ResponseEntity<LMR> enableFlag(@PathVariable String planningYear,
                                            @PathVariable String lmrId,
                                            @PathVariable BlockingFlag flag) {
        Optional<LMR> updated = lmrService.enableFlag(lmrId, planningYear, flag);
        return updated.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{planningYear}/{lmrId}/flags/{flag}/disable")
    @Operation(summary = "Disable a blocking flag (recomputes eligibility and publishes)")
    public ResponseEntity<LMR> disableFlag(@PathVariable String planningYear,
                                           @PathVariable String lmrId,
                                           @PathVariable BlockingFlag flag) {
        Optional<LMR> updated = lmrService.disableFlag(lmrId, planningYear, flag);
        return updated.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
