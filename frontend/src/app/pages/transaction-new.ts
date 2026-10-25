import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { LabelPipe, errorMessage } from '../core/labels';
import { Account, EvaluationResult, TRANSACTION_TYPES, TransactionType } from '../core/models';
import { EvidenceBar } from '../shared/evidence-bar';

interface Scenario {
  name: string;
  hint: string;
  values: { type: TransactionType; amount: number; counterpartyName: string; counterpartyCountry: string; description: string };
}

const SCENARIOS: Scenario[] = [
  { name: 'Ordinary card payment', hint: 'Should clear',
    values: { type: 'CARD_PAYMENT', amount: 64.9, counterpartyName: 'Corner Grocery', counterpartyCountry: 'GB', description: 'Groceries' } },
  { name: 'Payment to a listed entity', hint: 'Misspelt sanctions match, held',
    values: { type: 'WIRE_OUT', amount: 12850, counterpartyName: 'Golden Cresent Trdg', counterpartyCountry: 'AE', description: 'Invoice 4471' } },
  { name: 'Cash just under the threshold', hint: 'Submit three times to trigger structuring',
    values: { type: 'CASH_DEPOSIT', amount: 9600, counterpartyName: '', counterpartyCountry: '', description: 'Branch cash deposit' } },
  { name: 'Wire to a high-risk country', hint: 'Geographic risk',
    values: { type: 'WIRE_OUT', amount: 7400, counterpartyName: 'Pyongyang Trade Office', counterpartyCountry: 'KP', description: 'Machinery parts' } },
];

@Component({
  selector: 'app-transaction-new',
  imports: [ReactiveFormsModule, RouterLink, CurrencyPipe, LabelPipe, EvidenceBar],
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <a class="back" routerLink="/transactions">Back to transactions</a>
          <h1>Submit transaction</h1>
          <p>Send a transaction through monitoring as the core banking system would. The decision and the evidence behind it appear on the right.</p>
        </div>
      </header>

      <div class="grid grid--2">
        <section class="panel">
          <h2>Try a scenario</h2>
          <div class="scenarios">
            @for (s of scenarios; track s.name) {
              <button type="button" class="scenario" (click)="apply(s)">
                <strong>{{ s.name }}</strong><span>{{ s.hint }}</span>
              </button>
            }
          </div>

          <form [formGroup]="form" (ngSubmit)="submit()" class="form-grid" style="margin-top:20px">
            <label class="field span-2">
              <span>Account</span>
              <select formControlName="accountId">
                <option [ngValue]="null" disabled>Choose an account</option>
                @for (a of accounts(); track a.id) {
                  <option [ngValue]="a.id" [disabled]="a.status !== 'ACTIVE'">{{ a.customerName }}, {{ a.accountNumber }} ({{ a.currency }}){{ a.status !== 'ACTIVE' ? ', ' + (a.status | label) : '' }}</option>
                }
              </select>
            </label>
            <label class="field">
              <span>Type</span>
              <select formControlName="type">
                @for (t of types; track t) { <option [value]="t">{{ t | label }}</option> }
              </select>
            </label>
            <label class="field">
              <span>Amount</span>
              <input type="number" formControlName="amount" min="0.01" step="0.01" />
            </label>
            <label class="field">
              <span>Counterparty name</span>
              <input type="text" formControlName="counterpartyName" />
            </label>
            <label class="field">
              <span>Counterparty country</span>
              <input type="text" formControlName="counterpartyCountry" maxlength="2" placeholder="e.g. AE" />
              <small>Two-letter ISO code</small>
            </label>
            <label class="field">
              <span>Counterparty account</span>
              <input type="text" formControlName="counterpartyAccount" />
            </label>
            <label class="field">
              <span>Channel</span>
              <select formControlName="channel">
                <option value="ONLINE">Online</option><option value="BRANCH">Branch</option>
                <option value="ATM">ATM</option><option value="MOBILE">Mobile</option>
              </select>
            </label>
            <label class="field span-2">
              <span>Description</span>
              <input type="text" formControlName="description" />
            </label>
            <div class="span-2 actions">
              <button class="btn btn--primary" type="submit" [disabled]="form.invalid || busy()">Submit for monitoring</button>
            </div>
          </form>
        </section>

        <section class="panel" aria-live="polite">
          @if (error()) { <div class="banner banner--error">{{ error() }}</div> }
          @if (result(); as r) {
            <div class="verdict verdict--{{ r.decision }}">
              <h2>{{ verdictTitle(r) }}</h2>
              <p>{{ r.transaction.reference }}: {{ r.transaction.type | label }} of {{ r.transaction.amount | currency:r.transaction.currency }}</p>
            </div>
            <app-evidence-bar [hits]="r.hits" [score]="r.score" />
            @if (r.alertId) {
              <p style="margin-top:16px">Alert <a [routerLink]="['/alerts', r.alertId]">{{ r.alertNumber }}</a> opened for review.</p>
            }
          } @else {
            <h2>Monitoring result</h2>
            <p class="muted">Submit a transaction to see how the rules score it. Pick a scenario on the left to start quickly.</p>
          }
        </section>
      </div>
    </div>
  `,
  styles: `
    .scenarios { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
    .scenario {
      display: flex; flex-direction: column; align-items: flex-start; gap: 2px; text-align: left;
      padding: 10px 12px; border: 1px solid var(--rule); border-radius: var(--radius-sm);
      background: var(--surface-sunk); font: inherit; cursor: pointer; color: var(--ink);
    }
    .scenario:hover { border-color: var(--action); background: var(--action-tint); }
    .scenario strong { font-size: var(--step--1); }
    .scenario span { font-size: 0.75rem; color: var(--ink-soft); }
    .verdict { padding: 0 0 16px 14px; margin-bottom: 18px; border-left: 4px solid var(--risk-low); }
    .verdict p { color: var(--ink-soft); margin-top: 4px; }
    .verdict--FLAGGED { border-left-color: var(--risk-medium); }
    .verdict--BLOCKED { border-left-color: var(--risk-high); }
    @media (max-width: 560px) { .scenarios { grid-template-columns: 1fr; } }
  `,
})
export class TransactionNewPage {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly scenarios = SCENARIOS;
  protected readonly types = TRANSACTION_TYPES;
  protected readonly accounts = signal<Account[]>([]);
  protected readonly result = signal<EvaluationResult | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly form = this.fb.group({
    accountId: this.fb.control<number | null>(null, Validators.required),
    type: this.fb.nonNullable.control<TransactionType>('WIRE_OUT', Validators.required),
    amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    counterpartyName: [''],
    counterpartyCountry: ['', Validators.pattern(/^[A-Za-z]{2}$|^$/)],
    counterpartyAccount: [''],
    channel: ['ONLINE'],
    description: [''],
  });

  constructor() {
    this.api.accounts().subscribe({
      next: a => this.accounts.set(a),
      error: e => this.error.set(errorMessage(e)),
    });
  }

  protected apply(s: Scenario): void {
    this.form.patchValue({ ...s.values, channel: s.values.type.startsWith('CASH') ? 'BRANCH' : 'ONLINE' });
    if (this.form.value.accountId === null && this.accounts().length) {
      const first = this.accounts().find(a => a.status === 'ACTIVE');
      this.form.patchValue({ accountId: first?.id ?? null });
    }
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.api.submitTransaction({
      accountId: v.accountId!,
      type: v.type,
      amount: Number(v.amount),
      counterpartyName: v.counterpartyName || null,
      counterpartyCountry: v.counterpartyCountry ? v.counterpartyCountry.toUpperCase() : null,
      counterpartyAccount: v.counterpartyAccount || null,
      channel: v.channel,
      description: v.description || null,
    }).subscribe({
      next: r => { this.result.set(r); this.busy.set(false); },
      error: e => { this.error.set(errorMessage(e)); this.busy.set(false); },
    });
  }

  protected verdictTitle(r: EvaluationResult): string {
    switch (r.decision) {
      case 'BLOCKED': return 'Held for review';
      case 'FLAGGED': return 'Posted and flagged';
      default: return 'Cleared';
    }
  }
}
