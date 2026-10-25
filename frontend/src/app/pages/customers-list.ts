import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { LabelPipe, errorMessage } from '../core/labels';
import { Customer, Page, RiskLevel } from '../core/models';
import { Pager } from '../shared/pager';

@Component({
  selector: 'app-customers-list',
  imports: [FormsModule, RouterLink, LabelPipe, Pager],
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Customers</h1>
          <p>Sorted by risk score. Ratings update automatically when alerts open or close and when a SAR is filed.</p>
        </div>
        <a class="btn btn--primary" routerLink="/customers/new">Onboard customer</a>
      </header>

      <form class="filters" (submit)="$event.preventDefault(); load(0)">
        <label class="field grow">
          <span>Search</span>
          <input type="search" name="q" [(ngModel)]="q" placeholder="Name or customer number" />
        </label>
        <label class="field">
          <span>Risk rating</span>
          <select name="risk" [ngModel]="riskFilter()" (ngModelChange)="riskFilter.set($event); load(0)">
            <option [ngValue]="null">Any</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>
        </label>
        <button class="btn" type="submit">Search</button>
      </form>

      @if (error()) { <div class="banner banner--error">{{ error() }}</div> }

      <section class="panel panel--flush">
        <div class="table-wrap">
          <table class="data">
            <thead><tr><th>Customer</th><th>Type</th><th>Residence</th><th>Occupation</th><th>KYC</th><th>PEP</th><th>Risk</th></tr></thead>
            <tbody>
              @for (c of page()?.content ?? []; track c.id) {
                <tr class="link" (click)="router.navigate(['/customers', c.id])">
                  <td><a [routerLink]="['/customers', c.id]">{{ c.fullName }}</a><br><span class="faint">{{ c.customerNumber }}</span></td>
                  <td>{{ c.customerType | label }}</td>
                  <td>{{ c.countryOfResidence ?? '—' }}</td>
                  <td>{{ c.occupation ?? '—' }}</td>
                  <td><span class="pill {{ c.kycStatus }}">{{ c.kycStatus | label }}</span></td>
                  <td>{{ c.pep ? 'Yes' : '' }}</td>
                  <td class="nowrap"><span class="pill {{ c.riskLevel }}">{{ c.riskLevel | label }} {{ c.riskScore }}</span></td>
                </tr>
              } @empty {
                @if (page()) { <tr><td colspan="7" class="empty">No customers match. Try another search, or onboard a new customer.</td></tr> }
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
export class CustomersListPage {
  private readonly api = inject(ApiService);
  protected readonly router = inject(Router);

  /** From ?risk= (dashboard links). */
  readonly risk = input<RiskLevel | undefined>();

  protected q = '';
  protected readonly riskFilter = signal<RiskLevel | null>(null);
  protected readonly page = signal<Page<Customer> | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const r = this.risk();
      untracked(() => {
        this.riskFilter.set(r ?? null);
        this.load(0);
      });
    });
  }

  protected load(pageNo: number): void {
    this.api.customers(this.q, this.riskFilter(), pageNo).subscribe({
      next: p => { this.page.set(p); this.error.set(null); },
      error: e => this.error.set(errorMessage(e)),
    });
  }
}
