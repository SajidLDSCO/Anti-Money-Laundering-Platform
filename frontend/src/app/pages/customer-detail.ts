import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { LabelPipe, errorMessage } from '../core/labels';
import { Account, CustomerDetail } from '../core/models';

@Component({
  selector: 'app-customer-detail',
  imports: [FormsModule, RouterLink, DatePipe, CurrencyPipe, LabelPipe],
  template: `
    <div class="page">
      @if (error()) { <div class="banner banner--error">{{ error() }}</div> }

      @if (d(); as d) {
        <header class="page-head">
          <div>
            <a class="back" routerLink="/customers">Back to customers</a>
            <h1>{{ d.customer.fullName }}</h1>
            <p>{{ d.customer.customerNumber }}, {{ d.customer.customerType | label }}, customer since {{ d.customer.createdAt | date:'d MMM y' }}</p>
          </div>
          <div class="actions">
            <button class="btn" type="button" (click)="recalc()">Recalculate risk</button>
            <a class="btn" [routerLink]="['/customers', d.customer.id, 'edit']">Edit</a>
          </div>
        </header>

        <div class="grid grid--main-side">
          <div class="stack">
            <section class="panel panel--flush">
              <h2>Alerts</h2>
              <div class="table-wrap">
                <table class="data">
                  <thead><tr><th>Alert</th><th>Finding</th><th class="num">Score</th><th>Status</th></tr></thead>
                  <tbody>
                    @for (a of d.alerts; track a.id) {
                      <tr>
                        <td class="nowrap"><a [routerLink]="['/alerts', a.id]">{{ a.alertNumber }}</a><br><span class="faint">{{ a.createdAt | date:'d MMM y' }}</span></td>
                        <td>{{ a.summary }}</td>
                        <td class="num"><span class="pill {{ a.severity }}">{{ a.score }}</span></td>
                        <td><span class="pill {{ a.status }}">{{ a.status | label }}</span></td>
                      </tr>
                    } @empty {
                      <tr><td colspan="4" class="empty">No alerts for this customer.</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            </section>

            <section class="panel panel--flush">
              <h2>Recent transactions</h2>
              <div class="table-wrap">
                <table class="data">
                  <thead><tr><th>When</th><th>Type</th><th>Counterparty</th><th class="num">Amount</th><th class="num">Score</th><th>Outcome</th></tr></thead>
                  <tbody>
                    @for (t of d.recentTransactions; track t.id) {
                      <tr>
                        <td class="nowrap">{{ t.timestamp | date:'d MMM, HH:mm' }}</td>
                        <td class="nowrap">{{ t.type | label }}</td>
                        <td>{{ t.counterpartyName ?? '—' }} @if (t.counterpartyCountry) { <span class="faint">{{ t.counterpartyCountry }}</span> }</td>
                        <td class="num" [class.out]="isOutflow(t.type)">{{ isOutflow(t.type) ? '−' : '' }}{{ t.amount | currency:t.currency }}</td>
                        <td class="num">{{ t.riskScore }}</td>
                        <td><span class="pill {{ t.status }}">{{ t.status === 'BLOCKED' ? 'Held' : (t.status | label) }}</span></td>
                      </tr>
                    } @empty {
                      <tr><td colspan="6" class="empty">No transactions yet.</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            </section>
          </div>

          <div class="stack">
            <section class="panel">
              <h2>Risk rating</h2>
              <div class="rating rating--{{ d.customer.riskLevel }}">
                <strong>{{ d.customer.riskScore }}</strong>
                <span>{{ d.customer.riskLevel | label }} risk</span>
              </div>
              <ul class="factors">
                @for (f of factors(); track f) { <li>{{ f }}</li> }
              </ul>
            </section>

            <section class="panel">
              <h2>Profile</h2>
              <dl class="facts">
                <dt>KYC</dt><dd><span class="pill {{ d.customer.kycStatus }}">{{ d.customer.kycStatus | label }}</span></dd>
                <dt>PEP</dt><dd>{{ d.customer.pep ? 'Yes' : 'No' }}</dd>
                <dt>{{ d.customer.customerType === 'BUSINESS' ? 'Incorporated' : 'Born' }}</dt><dd>{{ (d.customer.dateOfBirth | date:'d MMM y') ?? '—' }}</dd>
                <dt>Nationality</dt><dd>{{ d.customer.nationality ?? '—' }}</dd>
                <dt>Residence</dt><dd>{{ d.customer.countryOfResidence ?? '—' }}</dd>
                <dt>Occupation</dt><dd>{{ d.customer.occupation ?? '—' }}</dd>
                <dt>Email</dt><dd>{{ d.customer.email ?? '—' }}</dd>
              </dl>
            </section>

            <section class="panel">
              <h2>Accounts</h2>
              <ul class="accounts">
                @for (a of d.accounts; track a.id) {
                  <li>
                    <div>
                      <strong>{{ a.accountNumber }}</strong>
                      <span class="faint">{{ a.accountType | label }}, {{ a.currency }}</span>
                    </div>
                    <div class="num">
                      {{ a.balance | currency:a.currency }}<br>
                      @if (a.status !== 'ACTIVE') {
                        <span class="pill {{ a.status }}">{{ a.status | label }}</span>
                      }
                      @if (auth.can('MLRO')) {
                        @if (a.status === 'ACTIVE') {
                          <button type="button" class="btn--quiet linkish" (click)="setStatus(a, 'FROZEN')">Freeze</button>
                        } @else {
                          <button type="button" class="btn--quiet linkish" (click)="setStatus(a, 'ACTIVE')">Unfreeze</button>
                        }
                      }
                    </div>
                  </li>
                } @empty {
                  <li class="muted">No accounts yet.</li>
                }
              </ul>
              <form class="open-account" (submit)="$event.preventDefault(); openAccount()">
                <select name="accType" [(ngModel)]="newType" aria-label="Account type">
                  <option value="CURRENT">Current</option><option value="SAVINGS">Savings</option><option value="BUSINESS">Business</option>
                </select>
                <input type="text" name="ccy" [(ngModel)]="newCurrency" maxlength="3" aria-label="Currency" />
                <button class="btn" type="submit">Open account</button>
              </form>
            </section>
          </div>
        </div>
      } @else if (!error()) {
        <p class="muted">Loading…</p>
      }
    </div>
  `,
  styles: `
    .rating { display: flex; align-items: baseline; gap: 10px; padding-left: 12px; border-left: 4px solid var(--risk-low); }
    .rating strong { font-size: 2.5rem; line-height: 1; font-weight: 700; }
    .rating span { color: var(--ink-soft); }
    .rating--MEDIUM { border-left-color: var(--risk-medium); }
    .rating--HIGH { border-left-color: var(--risk-high); }
    .factors { margin: 14px 0 0; padding-left: 18px; color: var(--ink-soft); font-size: var(--step--1); }
    .factors li + li { margin-top: 4px; }
    .accounts { list-style: none; margin: 0; padding: 0; }
    .accounts li { display: flex; justify-content: space-between; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--rule-soft); }
    .accounts li div:first-child { display: flex; flex-direction: column; }
    .linkish { background: none; border: 0; padding: 0; font: inherit; font-size: var(--step--1); cursor: pointer; color: var(--action); }
    .open-account { display: grid; grid-template-columns: 1fr 72px auto; gap: 8px; margin-top: 14px; }
    td.out { color: var(--ink); }
  `,
})
export class CustomerDetailPage {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  readonly id = input.required<string>();

  protected readonly d = signal<CustomerDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly factors = computed(() => (this.d()?.customer.riskFactors ?? '').split('; ').filter(Boolean));

  protected newType = 'CURRENT';
  protected newCurrency = 'USD';

  constructor() {
    effect(() => this.load(Number(this.id())));
  }

  protected isOutflow(type: string): boolean {
    return !(type.endsWith('_IN') || type === 'CASH_DEPOSIT');
  }

  protected recalc(): void {
    this.api.recalculateRisk(Number(this.id())).subscribe({
      next: c => { const cur = this.d(); if (cur) { this.d.set({ ...cur, customer: c }); } },
      error: e => this.error.set(errorMessage(e)),
    });
  }

  protected setStatus(a: Account, status: 'ACTIVE' | 'FROZEN'): void {
    this.api.setAccountStatus(a.id, status).subscribe({
      next: () => this.load(Number(this.id())),
      error: e => this.error.set(errorMessage(e)),
    });
  }

  protected openAccount(): void {
    this.api.openAccount(Number(this.id()), this.newType, this.newCurrency.toUpperCase()).subscribe({
      next: () => this.load(Number(this.id())),
      error: e => this.error.set(errorMessage(e)),
    });
  }

  private load(id: number): void {
    this.api.customer(id).subscribe({
      next: d => { this.d.set(d); this.error.set(null); },
      error: e => this.error.set(errorMessage(e)),
    });
  }
}
