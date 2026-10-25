import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { LabelPipe, errorMessage, label } from '../core/labels';
import { AlertStatus, AlertSummary, Page, Severity } from '../core/models';
import { Pager } from '../shared/pager';

type View = 'OPEN' | 'ALL' | AlertStatus;

@Component({
  selector: 'app-alerts-list',
  imports: [FormsModule, RouterLink, DatePipe, CurrencyPipe, LabelPipe, Pager],
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Alerts</h1>
          <p>Work the queue from the top: newest first. Open an alert to see the evidence and record a decision.</p>
        </div>
      </header>

      <div class="filters">
        <label class="field">
          <span>Show</span>
          <select [ngModel]="view()" (ngModelChange)="view.set($event); load(0)">
            <option value="OPEN">Open work (open, in review, escalated)</option>
            <option value="ALL">All alerts</option>
            <option value="OPEN_ONLY">Not yet picked up</option>
            <option value="ESCALATED">Escalated</option>
            <option value="CLOSED_SAR_FILED">Closed – SAR filed</option>
            <option value="CLOSED_FALSE_POSITIVE">Closed – false positive</option>
          </select>
        </label>
        <label class="field">
          <span>Severity</span>
          <select [ngModel]="sev()" (ngModelChange)="sev.set($event); load(0)">
            <option [ngValue]="null">Any</option>
            <option value="CRITICAL">Critical</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>
        </label>
      </div>

      @if (error()) { <div class="banner banner--error">{{ error() }}</div> }

      <section class="panel panel--flush">
        <div class="table-wrap">
          <table class="data">
            <thead>
              <tr><th>Alert</th><th>Customer</th><th>Finding</th><th class="num">Amount</th>
                <th class="num">Score</th><th>Status</th><th>Assigned to</th></tr>
            </thead>
            <tbody>
              @for (a of page()?.content ?? []; track a.id) {
                <tr class="link" (click)="router.navigate(['/alerts', a.id])">
                  <td class="nowrap">
                    <a [routerLink]="['/alerts', a.id]">{{ a.alertNumber }}</a><br>
                    <span class="faint">{{ a.createdAt | date:'d MMM y, HH:mm' }}</span>
                  </td>
                  <td>{{ a.customerName }}</td>
                  <td>
                    {{ a.summary }}<br>
                    <span class="faint">{{ rulesText(a) }}</span>
                  </td>
                  <td class="num">
                    @if (a.amount !== null) { {{ a.amount | currency:(a.currency ?? 'USD') }} } @else { <span class="faint">{{ a.type | label }}</span> }
                  </td>
                  <td class="num"><span class="pill {{ a.severity }}" [title]="a.severity | label">{{ a.score }}</span></td>
                  <td><span class="pill {{ a.status }}">{{ a.status | label }}</span></td>
                  <td>{{ a.assignedTo ?? '—' }}</td>
                </tr>
              } @empty {
                @if (page()) { <tr><td colspan="7" class="empty">No alerts match these filters.</td></tr> }
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
export class AlertsListPage {
  private readonly api = inject(ApiService);
  protected readonly router = inject(Router);

  /** From the ?severity= query parameter (dashboard links). */
  readonly severity = input<Severity | undefined>();

  protected readonly view = signal<View | 'OPEN_ONLY'>('OPEN');
  protected readonly sev = signal<Severity | null>(null);
  protected readonly page = signal<Page<AlertSummary> | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const s = this.severity();
      untracked(() => {
        this.sev.set(s ?? null);
        this.load(0);
      });
    });
  }

  protected load(pageNo: number): void {
    const v = this.view();
    const filter = {
      openOnly: v === 'OPEN',
      status: v === 'OPEN' || v === 'ALL' ? null : v === 'OPEN_ONLY' ? 'OPEN' as AlertStatus : v,
      severity: this.sev(),
    };
    this.api.alerts(filter, pageNo).subscribe({
      next: p => { this.page.set(p); this.error.set(null); },
      error: e => this.error.set(errorMessage(e)),
    });
  }

  protected rulesText(a: AlertSummary): string {
    return a.ruleCodes.map(c => label(c)).join(', ');
  }
}
