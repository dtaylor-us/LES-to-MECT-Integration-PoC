import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LesApiService, CreateEnrollmentRequest } from '../services/les-api.service';

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <div class="app-container">
      <div class="page-head">
        <h1>New LMR enrollment</h1>
        <a routerLink="/enrollments" class="btn btn-secondary">Back to list</a>
      </div>

      @if (error) {
        <div class="alert alert-error">{{ error }}</div>
      }

      <div class="card" style="max-width: 32rem;">
        <form (ngSubmit)="onSubmit()">
          <div class="form-group">
            <label for="lmrId">LMR ID</label>
            <input id="lmrId" name="lmrId" [(ngModel)]="form.lmrId" required />
          </div>
          <div class="form-group">
            <label for="lmrName">LMR name</label>
            <input id="lmrName" name="lmrName" [(ngModel)]="form.lmrName" required />
          </div>
          <div class="form-group">
            <label for="marketParticipantName">Market participant name</label>
            <input id="marketParticipantName" name="marketParticipantName" [(ngModel)]="form.marketParticipantName" required />
          </div>
          <div class="form-group">
            <label for="resourceType">Resource type</label>
            <select id="resourceType" name="resourceType" [(ngModel)]="form.resourceType" required>
              <option value="LMR_DR">LMR_DR</option>
              <option value="LMR_BTMG">LMR_BTMG</option>
            </select>
          </div>
          <div class="form-group">
            <label for="planningYear">Planning year</label>
            <input id="planningYear" name="planningYear" [(ngModel)]="form.planningYear" required placeholder="e.g. 2026" />
          </div>
          <div style="display: flex; gap: 0.75rem;">
            <button type="submit" class="btn btn-primary" [disabled]="saving">Create enrollment</button>
            <a routerLink="/enrollments" class="btn btn-secondary">Cancel</a>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .page-head {
      margin-bottom: 2rem;
    }
    .card {
      margin-bottom: 2rem;
    }
  `],
})
export class EnrollmentCreateComponent {
  form: CreateEnrollmentRequest = {
    lmrId: '',
    lmrName: '',
    marketParticipantName: '',
    resourceType: 'LMR_DR',
    planningYear: '',
  };
  saving = false;
  error: string | null = null;

  constructor(private api: LesApiService, private router: Router) {}

  onSubmit(): void {
    this.error = null;
    this.saving = true;
    this.api.createEnrollment(this.form).subscribe({
      next: () => this.router.navigate(['/enrollments']),
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Create failed';
        this.saving = false;
      },
    });
  }
}
