import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { LabelPipe, errorMessage } from '../core/labels';
import { Page, Transaction, TransactionStatus } from '../core/models';
import { Pager } from '../shared/pager';

@Component({
  selector: 'app-transactions-list',
  imports: [FormsModule, RouterLink, DatePipe, CurrencyPipe, LabelPipe, Pager],
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Transactions</h1>
          <p>Every transaction is scored by the detection rules before it is posted. Held transactions wait for an analyst decision.</p>
        </div>
        <a class="btn btn--primary" routerLink="/transactions/new">Submit transaction</a>
      </header>

      <form class="filters" (submit)="$event.preventDefault(); load(0)">
        <label class="field grow">
          <span>Search</span>
          <input type="search" name="q" [(ngModel)]="q" placeholder="Reference, customer or counterparty" />
        </label>
        <label class="field">
          <span>Outcome</span>
          <select name="status" [ngModel]="statusFilter()" (ngModelChange)="statusFilter.set($event); load(0)">
            <option [ngValue]="null">Any</option>
            <option value="CLEARED">Cleared</option>
            <option value="FLAGGED">Flagged</option>
            <option value="BLOCKED">Held</option>
          </select>
        </label>
        <button class="btn" type="submit">Search</button>
      </form>

      @if (error()) { <div class="banner banner--error">{{ error() }}</div> }

      <section class="panel panel--flush">
        <div class="table-wrap">
          <table class="data">
            <thead>
              <tr><th>When</th><th>Reference</th><th>Customer</th><th>Type</th><th>Counterparty</th>
                <th class="num">Amount</th><th class="num">Score</th><th>Outcome</th></tr>
            </thead>
            <tbody>
              @for (t of page()?.content ?? []; track t.id) {
                <tr>
                  <td class="nowrap">{{ t.timestamp | date:'d MMM y, HH:mm' }}</td>
                  <td class="nowrap">{{ t.reference }}</td>
                  <td><a [routerLink]="['/customers', t.customerId]">{{ t.customerName }}</a></td>
                  <td class="nowrap">{{ t.type | label }}</td>
                  <td>{{ t.counterpartyName ?? '—' }} @if (t.counterpartyCountry) { <span class="faint">{{ t.counterpartyCountry }}</span> }</td>
                  <td class="num">{{ t.amount | currency:t.currency }}</td>
                  <td class="num"><span class="score" [class.s-low]="t.riskScore < 40" [class.s-med]="t.riskScore >= 40 && t.riskScore < 80" [class.s-high]="t.riskScore >= 80">{{ t.riskScore }}</span></td>
                  <td><span class="pill {{ t.status }}">{{ t.status === 'BLOCKED' ? 'Held' : (t.status | label) }}</span></td>
                </tr>
              } @empty {
                @if (page()) { <tr><td colspan="8" class="empty">No transactions match these filters.</td></tr> }
              }
            </tbody>
          </table>
        </div>
        @if (page(); as p) {
          <app-pager [page]="p.page" [size]="p.size" [total]="p.totalElements" [totalPages]="p.totalPages" (pageChange)="load($event)" />
        }
      </section>
    </div>
  `,
})
export class TransactionsListPage {
  private readonly api = inject(ApiService);

  /** From ?status= (dashboard links). */
  readonly status = input<TransactionStatus | undefined>();

  protected q = '';
  protected readonly statusFilter = signal<TransactionStatus | null>(null);
  protected readonly page = signal<Page<Transaction> | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const s = this.status();
      untracked(() => {
        this.statusFilter.set(s ?? null);
        this.load(0);
      });
    });
  }

  protected load(pageNo: number): void {
    this.api.transactions({ status: this.statusFilter(), q: this.q }, pageNo).subscribe({
      next: p => { this.page.set(p); this.error.set(null); },
      error: e => this.error.set(errorMessage(e)),
    });
  }
}
