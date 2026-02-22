import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LesApiService, LMREnrollment } from '../services/les-api.service';

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="app-container">
      <div class="page-head">
        <h1>LMR Enrollments</h1>
        <a routerLink="/enrollments/new" class="btn btn-primary">New enrollment</a>
      </div>

      @if (error) {
        <div class="alert alert-error">{{ error }}</div>
      }

      @if (loading) {
        <p>Loading enrollments…</p>
      } @else if (enrollments.length === 0) {
        <div class="card">
          <p class="muted">No enrollments yet. <a routerLink="/enrollments/new">Create one</a>.</p>
        </div>
      } @else {
        <div class="table-wrap">
          <table class="miso-table">
            <thead>
              <tr>
                <th>LMR ID</th>
                <th>Name</th>
                <th>Participant</th>
                <th>Planning year</th>
                <th>Resource type</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (e of enrollments; track e.lmrId) {
                <tr>
                  <td><a [routerLink]="['/enrollments', e.lmrId]">{{ e.lmrId }}</a></td>
                  <td>{{ e.lmrName }}</td>
                  <td>{{ e.marketParticipantName }}</td>
                  <td>{{ e.planningYear }}</td>
                  <td>{{ e.resourceType }}</td>
                  <td><span class="badge" [class]="'status-' + e.status">{{ e.status }}</span></td>
                  <td><a [routerLink]="['/enrollments', e.lmrId]" class="btn btn-secondary">View</a></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .page-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 1.5rem;
      flex-wrap: wrap;
      gap: 1rem;
    }
    .page-head h1 { margin: 0; }
    .muted { color: var(--miso-text-muted); margin: 0; }
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
    .miso-table th {
      background: var(--miso-surface);
      font-weight: 600;
      font-size: 0.875rem;
    }
    .miso-table tbody tr:hover { background: var(--miso-surface); }
    .miso-table a { font-weight: 500; }
  `],
})
export class EnrollmentListComponent implements OnInit {
  enrollments: LMREnrollment[] = [];
  loading = true;
  error: string | null = null;

  constructor(private api: LesApiService) {}

  ngOnInit(): void {
    this.api.listEnrollments().subscribe({
      next: (list) => {
        this.enrollments = list;
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.message || 'Failed to load enrollments. Is the LES API running on port 8081?';
        this.loading = false;
      },
    });
  }
}
