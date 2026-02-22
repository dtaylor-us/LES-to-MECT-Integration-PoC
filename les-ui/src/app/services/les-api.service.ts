import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LMREnrollment {
  id: number;
  lmrId: string;
  marketParticipantName: string;
  lmrName: string;
  resourceType: string;
  planningYear: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  withdrawRejectReason?: string | null;
  withdrawRejectedAt?: string | null;
}

export interface CreateEnrollmentRequest {
  lmrId: string;
  marketParticipantName: string;
  lmrName: string;
  resourceType: 'LMR_DR' | 'LMR_BTMG';
  planningYear: string;
}

export interface WithdrawEligibility {
  lmrId: string;
  planningYear: string;
  canWithdraw: boolean;
  message: string;
  updatedAt: string;
}

export interface WithdrawRejectionDto {
  lmrId: string;
  planningYear: string;
  lmrName: string;
  marketParticipantName: string;
  message: string;
  withdrawRejectedAt: string;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class LesApiService {
  private base = environment.apiUrl;

  constructor(private http: HttpClient) {}

  listEnrollments(): Observable<LMREnrollment[]> {
    return this.http.get<LMREnrollment[]>(`${this.base}/lmrs`);
  }

  getEnrollment(lmrId: string): Observable<LMREnrollment> {
    return this.http.get<LMREnrollment>(`${this.base}/lmrs/${encodeURIComponent(lmrId)}`);
  }

  createEnrollment(body: CreateEnrollmentRequest): Observable<LMREnrollment> {
    return this.http.post<LMREnrollment>(`${this.base}/lmrs`, body);
  }

  submit(lmrId: string): Observable<LMREnrollment> {
    return this.http.post<LMREnrollment>(`${this.base}/lmrs/${encodeURIComponent(lmrId)}/submit`, {});
  }

  approve(lmrId: string): Observable<LMREnrollment> {
    return this.http.post<LMREnrollment>(`${this.base}/lmrs/${encodeURIComponent(lmrId)}/approve`, {});
  }

  withdraw(lmrId: string): Observable<LMREnrollment> {
    return this.http.post<LMREnrollment>(`${this.base}/lmrs/${encodeURIComponent(lmrId)}/withdraw`, {});
  }

  getWithdrawEligibility(lmrId: string): Observable<WithdrawEligibility> {
    return this.http.get<WithdrawEligibility>(`${this.base}/lmrs/${encodeURIComponent(lmrId)}/withdraw-eligibility`);
  }

  listWithdrawRejections(): Observable<WithdrawRejectionDto[]> {
    return this.http.get<WithdrawRejectionDto[]>(`${this.base}/admin/withdraw-rejections`);
  }
}
