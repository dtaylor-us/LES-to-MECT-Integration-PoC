import { Component, OnDestroy, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { interval, Subscription, Observable } from 'rxjs';
import { LesApiService, LMREnrollment, WithdrawEligibility } from '../services/les-api.service';

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="app-container">
      <div class="page-head">
        <h1>Enrollment: {{ enrollment?.lmrId }}</h1>
        <a routerLink="/enrollments" class="btn btn-secondary">Back to list</a>
      </div>

      @if (error) {
        <div class="alert alert-error">{{ error }}</div>
      }

      @if (loading && !enrollment) {
        <p>Loading…</p>
      } @else if (enrollment) {
        <div class="card">
          <h2>Details</h2>
          <dl class="detail-grid">
            <dt>LMR ID</dt><dd>{{ enrollment.lmrId }}</dd>
            <dt>LMR name</dt><dd>{{ enrollment.lmrName }}</dd>
            <dt>Market participant</dt><dd>{{ enrollment.marketParticipantName }}</dd>
            <dt>Resource type</dt><dd>{{ enrollment.resourceType }}</dd>
            <dt>Planning year</dt><dd>{{ enrollment.planningYear }}</dd>
            <dt>Status</dt><dd><span class="badge" [class]="'status-' + enrollment.status">{{ enrollment.status }}</span></dd>
          </dl>
          @if (enrollment.withdrawRejectReason) {
            <div class="alert alert-error">
              <strong>Withdrawal rejected by MECT:</strong> {{ enrollment.withdrawRejectReason }}
            </div>
          }
        </div>

        <!-- Eligibility message (when user cannot withdraw) -->
        @if (eligibility && !eligibility.canWithdraw && eligibility.message) {
          <div class="alert alert-info">
            {{ eligibility.message }}
          </div>
        }

        <div class="card">
          <h2>Actions</h2>
          <div class="actions">
            @if (enrollment.status === 'DRAFT') {
              <button class="btn btn-primary" (click)="submit()" [disabled]="actionLoading">Submit for approval</button>
            }
            @if (enrollment.status === 'SUBMITTED') {
              <button class="btn btn-primary" (click)="approve()" [disabled]="actionLoading">Approve enrollment</button>
            }
            @if (enrollment.status === 'APPROVED' && eligibility?.canWithdraw) {
              <button class="btn btn-danger" (click)="withdraw()" [disabled]="actionLoading">Withdraw</button>
              <span class="action-note">Eligible to withdraw. Request will be sent to MECT.</span>
            }
            @if (enrollment.status === 'APPROVED' && eligibility && !eligibility.canWithdraw) {
              <span class="action-note">Withdraw button is not shown when eligibility says you cannot withdraw (see message above).</span>
            }
            @if (enrollment.status === 'APPROVED' && polling) {
              <span class="status-pending-dot">Checking eligibility periodically.</span>
            }
            @if (enrollment.status === 'WITHDRAWN_REQUESTED') {
              <div class="status-pending">
                <span class="status-pending-text">Withdrawal requested. Waiting for MECT to respond…</span>
                @if (polling) {
                  <span class="status-pending-dot">Checking status automatically.</span>
                }
              </div>
            }
            @if (enrollment.status === 'WITHDRAWN') {
              <div class="alert alert-success">
                <strong>Withdrawal completed.</strong> This LMR has been withdrawn by MECT.
              </div>
            }
            @if (enrollment.status === 'WITHDRAW_REJECTED') {
              <div class="alert alert-error">
                <strong>Withdrawal rejected by MECT:</strong> {{ enrollment.withdrawRejectReason || 'See message above.' }}
              </div>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .page-head {
      margin-bottom: 2rem;
    }
    .detail-grid {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 0.5rem 1.5rem;
      margin: 0;
    }
    .detail-grid dt { color: var(--miso-text-muted); font-weight: 500; }
    .detail-grid dd { margin: 0; }
    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem;
    }
    .action-note {
      color: var(--miso-text-muted);
      font-size: 0.875rem;
    }
    .status-pending {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .status-pending-text { font-weight: 500; }
    .status-pending-dot {
      color: var(--miso-text-muted);
      font-size: 0.8125rem;
    }
  `],
})
export class EnrollmentDetailComponent implements OnInit, OnDestroy {
  enrollment: LMREnrollment | null = null;
  eligibility: WithdrawEligibility | null = null;
  loading = true;
  actionLoading = false;
  polling = false;
  error: string | null = null;
  private lmrId = '';
  private pollSub: Subscription | null = null;

  constructor(
    private route: ActivatedRoute,
    private api: LesApiService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.lmrId = this.route.snapshot.paramMap.get('lmrId') ?? '';
    this.load();
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  /** Statuses that need live enrollment + eligibility updates (polling). */
  private static readonly POLL_STATUSES = ['APPROVED', 'WITHDRAWN_REQUESTED'] as const;

  private shouldPoll(e: LMREnrollment): boolean {
    return (EnrollmentDetailComponent.POLL_STATUSES as readonly string[]).includes(e.status);
  }

  load(): void {
    this.loading = true;
    this.api.getEnrollment(this.lmrId).subscribe({
      next: (e) => {
        this.enrollment = e;
        this.loading = false;
        if (e.status === 'APPROVED' || e.status === 'WITHDRAWN' || e.status === 'WITHDRAWN_REQUESTED' || e.status === 'WITHDRAW_REJECTED') {
          this.loadEligibility();
        }
        if (this.shouldPoll(e)) {
          this.startPolling();
        } else {
          this.stopPolling();
        }
      },
      error: (err) => {
        this.error = err?.message || 'Failed to load enrollment';
        this.loading = false;
        this.stopPolling();
      },
    });
  }

  /**
   * Single polling loop: refreshes enrollment and (when relevant) eligibility every 2s.
   * Runs while status is APPROVED or WITHDRAWN_REQUESTED; stops when status changes to a terminal state.
   */
  private startPolling(): void {
    this.stopPolling();
    this.polling = true;
    const POLL_MS = 2000;
    this.pollSub = interval(POLL_MS).subscribe(() => this.tick());
  }

  private tick(): void {
    this.api.getEnrollment(this.lmrId).subscribe({
      next: (e) => {
        this.enrollment = e;
        if (e.status === 'APPROVED' || e.status === 'WITHDRAWN' || e.status === 'WITHDRAWN_REQUESTED' || e.status === 'WITHDRAW_REJECTED') {
          this.api.getWithdrawEligibility(this.lmrId).subscribe({
            next: (el) => {
              this.eligibility = el;
              this.cdr.detectChanges();
            },
            error: () => {
              this.eligibility = null;
              this.cdr.detectChanges();
            },
          });
        }
        if (!this.shouldPoll(e)) {
          this.stopPolling();
        }
        this.cdr.detectChanges();
      },
      error: () => {
        this.stopPolling();
      },
    });
  }

  private stopPolling(): void {
    if (this.pollSub) {
      this.pollSub.unsubscribe();
      this.pollSub = null;
    }
    this.polling = false;
  }

  loadEligibility(): void {
    this.api.getWithdrawEligibility(this.lmrId).subscribe({
      next: (el) => {
        this.eligibility = el;
        this.cdr.detectChanges();
      },
      error: () => {
        this.eligibility = null;
        this.cdr.detectChanges();
      },
    });
  }

  submit(): void {
    this.runAction(() => this.api.submit(this.lmrId));
  }

  approve(): void {
    this.runAction(() => this.api.approve(this.lmrId));
  }

  withdraw(): void {
    this.runAction(() => this.api.withdraw(this.lmrId));
  }

  private runAction(op: () => Observable<LMREnrollment>): void {
    this.actionLoading = true;
    this.error = null;
    op().subscribe({
      next: (e) => {
        this.enrollment = e;
        this.actionLoading = false;
        this.cdr.detectChanges();
        if (e.status === 'APPROVED' || e.status === 'WITHDRAWN' || e.status === 'WITHDRAWN_REQUESTED' || e.status === 'WITHDRAW_REJECTED') {
          this.loadEligibility();
        }
        if (this.shouldPoll(e)) {
          this.startPolling();
        } else {
          this.stopPolling();
        }
      },
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Action failed';
        this.actionLoading = false;
        this.load();
      },
    });
  }
}
