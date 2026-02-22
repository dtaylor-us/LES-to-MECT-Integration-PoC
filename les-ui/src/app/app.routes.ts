import { Routes } from '@angular/router';
import { EnrollmentListComponent } from './enrollments/enrollment-list.component';
import { EnrollmentCreateComponent } from './enrollments/enrollment-create.component';
import { EnrollmentDetailComponent } from './enrollments/enrollment-detail.component';
import { WithdrawRejectionsComponent } from './admin/withdraw-rejections.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'enrollments' },
  { path: 'enrollments', component: EnrollmentListComponent },
  { path: 'enrollments/new', component: EnrollmentCreateComponent },
  { path: 'enrollments/:lmrId', component: EnrollmentDetailComponent },
  { path: 'admin/withdraw-rejections', component: WithdrawRejectionsComponent },
  { path: '**', redirectTo: 'enrollments' },
];
