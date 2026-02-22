import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LesApiService, WithdrawRejectionDto } from '../services/les-api.service';

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="app-container">
      <div class="page-head">
        <h1>Withdrawal rejections (admin)</h1>
        <a routerLink="/enrollments" class="btn btn-secondary">Back to enrollments</a>
      </div>

      <p class="muted">
        Enrollments where the user requested withdrawal but MECT rejected (e.g. state changed in MECT after the button was shown).
        Use this to identify the edge case and act; if frequent in production, automatic reconciliation may be added.
      </p>

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
                  <td><a [routerLink]="['/enrollments', r.lmrId]" class="btn btn-secondary">View</a></td>
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
  `],
})
export class WithdrawRejectionsComponent implements OnInit {
  list: WithdrawRejectionDto[] = [];
  loading = true;
  error: string | null = null;

  constructor(private api: LesApiService) {}

  ngOnInit(): void {
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
}
