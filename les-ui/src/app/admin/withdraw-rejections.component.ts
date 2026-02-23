import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LesApiService, WithdrawRejectionDto } from '../services/les-api.service';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="app-container">
      <div class="page-head">
        <h1>Withdrawal rejections</h1>
        <a routerLink="/enrollments" class="btn btn-secondary">Back to enrollments</a>
      </div>

      <p class="muted">
        Enrollments where the user requested withdrawal but MECT rejected (e.g. state changed in MECT after the button was shown).
        Use <strong>Restore</strong> to reset the enrollment back to active (APPROVED) status so it remains valid.
      </p>

      @if (actionError) {
        <div class="alert alert-error">{{ actionError }}</div>
      }
      @if (actionSuccess) {
        <div class="alert alert-success">{{ actionSuccess }}</div>
      }

      @if (error) {
        <div class="alert alert-error">{{ error }}</div>
      }

      @if (loading) {
        <p>Loading…</p>
      } @else if (list.length === 0) {
        <div class="card">
          <p class="muted">No withdrawal rejections.</p>
        </div>
      } @else {
        <div class="table-wrap">
          <table class="miso-table">
            <thead>
              <tr>
                <th>LMR ID</th>
                <th>Planning year</th>
                <th>LMR name</th>
                <th>Participant</th>
                <th>Message (from MECT)</th>
                <th>Rejected at</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (r of list; track r.lmrId + r.withdrawRejectedAt) {
                <tr>
                  <td><a [routerLink]="['/enrollments', r.lmrId]">{{ r.lmrId }}</a></td>
                  <td>{{ r.planningYear }}</td>
                  <td>{{ r.lmrName }}</td>
                  <td>{{ r.marketParticipantName }}</td>
                  <td>{{ r.message }}</td>
                  <td>{{ r.withdrawRejectedAt | date:'short' }}</td>
                  <td class="action-cell">
                    <button
                      class="btn btn-primary btn-sm"
                      (click)="correctWithdrawal(r.lmrId)"
                      [disabled]="correcting === r.lmrId"
                    >
                      {{ correcting === r.lmrId ? 'Restoring…' : 'Restore to Approved' }}
                    </button>
                    <a [routerLink]="['/enrollments', r.lmrId]" class="btn btn-secondary btn-sm">View</a>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1rem; flex-wrap: wrap; gap: 1rem; }
    .page-head h1 { margin: 0; }
    .muted { color: var(--miso-text-muted); margin-bottom: 1rem; font-size: 0.875rem; }
    .table-wrap { overflow-x: auto; }
    .miso-table {
      width: 100%;
      border-collapse: collapse;
      background: var(--miso-card);
      border-radius: 8px;
      overflow: hidden;
      box-shadow: var(--shadow-sm);
    }
    .miso-table th, .miso-table td {
      padding: 0.75rem 1rem;
      text-align: left;
      border-bottom: 1px solid var(--miso-border);
    }
    .miso-table th { background: var(--miso-surface); font-weight: 600; font-size: 0.875rem; }
    .miso-table tbody tr:hover { background: var(--miso-surface); }
    .action-cell { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; }
    .btn-sm { font-size: 0.8125rem; padding: 0.25rem 0.75rem; }
  `],
})
export class WithdrawRejectionsComponent implements OnInit {
  list: WithdrawRejectionDto[] = [];
  loading = true;
  error: string | null = null;
  correcting: string | null = null;
  actionError: string | null = null;
  actionSuccess: string | null = null;

  constructor(private api: LesApiService) {}

  ngOnInit(): void {
    this.loadList();
  }

  private loadList(): void {
    this.loading = true;
    this.api.listWithdrawRejections().subscribe({
      next: (data) => {
        this.list = data;
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.message || 'Failed to load rejections';
        this.loading = false;
      },
    });
  }

  correctWithdrawal(lmrId: string): void {
    this.correcting = lmrId;
    this.actionError = null;
    this.actionSuccess = null;
    this.api.correctWithdrawal(lmrId).subscribe({
      next: () => {
        this.correcting = null;
        this.actionSuccess = `Enrollment ${lmrId} has been restored to APPROVED.`;
        this.loadList();
      },
      error: (err) => {
        this.correcting = null;
        this.actionError = err?.error?.message || err?.message || 'Failed to correct enrollment state.';
      },
    });
  }
}
