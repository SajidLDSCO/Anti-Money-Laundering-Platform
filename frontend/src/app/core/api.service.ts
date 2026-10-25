import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Account, AccountStatus, AlertDetail, AlertStatus, AlertSummary, Customer, CustomerDetail, CustomerRequest,
  Dashboard, EvaluationResult, ImportResult, Note, Page, RiskLevel, Rule, SanctionsEntry, Sar, ScreeningResult,
  Role, Severity, Transaction, TransactionRequest, TransactionStatus, User,
} from './models';

/** Builds query params, dropping empty values. */
function params(values: Record<string, string | number | boolean | null | undefined>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(values)) {
    if (v !== null && v !== undefined && v !== '') {
      p = p.set(k, String(v));
    }
  }
  return p;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  // Dashboard
  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${this.base}/dashboard`);
  }

  // Customers & accounts
  customers(q: string, riskLevel: RiskLevel | null, page = 0, size = 20): Observable<Page<Customer>> {
    return this.http.get<Page<Customer>>(`${this.base}/customers`, { params: params({ q, riskLevel, page, size }) });
  }
  customer(id: number): Observable<CustomerDetail> {
    return this.http.get<CustomerDetail>(`${this.base}/customers/${id}`);
  }
  createCustomer(body: CustomerRequest): Observable<Customer> {
    return this.http.post<Customer>(`${this.base}/customers`, body);
  }
  updateCustomer(id: number, body: CustomerRequest): Observable<Customer> {
    return this.http.put<Customer>(`${this.base}/customers/${id}`, body);
  }
  recalculateRisk(id: number): Observable<Customer> {
    return this.http.post<Customer>(`${this.base}/customers/${id}/recalculate-risk`, {});
  }
  openAccount(customerId: number, accountType: string, currency: string): Observable<Account> {
    return this.http.post<Account>(`${this.base}/customers/${customerId}/accounts`, { accountType, currency });
  }
  accounts(): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.base}/accounts`);
  }
  setAccountStatus(id: number, status: AccountStatus): Observable<Account> {
    return this.http.patch<Account>(`${this.base}/accounts/${id}/status`, { status });
  }

  // Transactions
  transactions(filter: { status?: TransactionStatus | null; customerId?: number | null; q?: string },
               page = 0, size = 25): Observable<Page<Transaction>> {
    return this.http.get<Page<Transaction>>(`${this.base}/transactions`, { params: params({ ...filter, page, size }) });
  }
  submitTransaction(body: TransactionRequest): Observable<EvaluationResult> {
    return this.http.post<EvaluationResult>(`${this.base}/transactions`, body);
  }

  // Alerts & SARs
  alerts(filter: { status?: AlertStatus | null; openOnly?: boolean; severity?: Severity | null },
         page = 0, size = 25): Observable<Page<AlertSummary>> {
    return this.http.get<Page<AlertSummary>>(`${this.base}/alerts`, { params: params({ ...filter, page, size }) });
  }
  alert(id: number): Observable<AlertDetail> {
    return this.http.get<AlertDetail>(`${this.base}/alerts/${id}`);
  }
  // The analyst on workflow actions is taken from the session by the server.
  assignAlert(id: number): Observable<AlertDetail> {
    return this.http.post<AlertDetail>(`${this.base}/alerts/${id}/assign`, {});
  }
  addNote(id: number, text: string): Observable<Note> {
    return this.http.post<Note>(`${this.base}/alerts/${id}/notes`, { text });
  }
  changeAlertStatus(id: number, status: AlertStatus, comment: string): Observable<AlertDetail> {
    return this.http.post<AlertDetail>(`${this.base}/alerts/${id}/status`, { status, comment });
  }
  fileSar(id: number, narrative: string): Observable<AlertDetail> {
    return this.http.post<AlertDetail>(`${this.base}/alerts/${id}/sar`, { narrative });
  }
  sars(): Observable<Sar[]> {
    return this.http.get<Sar[]>(`${this.base}/sars`);
  }

  // Screening & sanctions list
  screen(name: string): Observable<ScreeningResult> {
    return this.http.get<ScreeningResult>(`${this.base}/screening`, { params: params({ name }) });
  }
  sanctions(): Observable<SanctionsEntry[]> {
    return this.http.get<SanctionsEntry[]>(`${this.base}/sanctions`);
  }
  addSanction(entry: SanctionsEntry): Observable<SanctionsEntry> {
    return this.http.post<SanctionsEntry>(`${this.base}/sanctions`, entry);
  }
  deleteSanction(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/sanctions/${id}`);
  }
  importSanctions(file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>(`${this.base}/sanctions/import`, form);
  }

  // Rules
  rules(): Observable<Rule[]> {
    return this.http.get<Rule[]>(`${this.base}/rules`);
  }
  toggleRule(code: string, enabled: boolean): Observable<Rule> {
    return this.http.patch<Rule>(`${this.base}/rules/${code}`, { enabled });
  }

  // Users (admin)
  users(): Observable<User[]> {
    return this.http.get<User[]>(`${this.base}/users`);
  }
  updateUser(id: number, change: { role?: Role; enabled?: boolean }): Observable<User> {
    return this.http.patch<User>(`${this.base}/users/${id}`, change);
  }
  /** Declines a sign-up that was never approved: deletes it and emails the person. */
  declineUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/users/${id}`);
  }
}
