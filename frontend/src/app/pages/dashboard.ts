import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { LabelPipe, errorMessage, label } from '../core/labels';
import { Dashboard, Severity } from '../core/models';

const SEVERITIES: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe, DecimalPipe, CurrencyPipe, LabelPipe],
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Overview</h1>
          <p>Monitoring results across all customers for the last 30 days.</p>
        </div>
        <a class="btn btn--primary" routerLink="/transactions/new">Submit transaction</a>
      </header>

      @if (error()) { <div class="banner banner--error">{{ error() }}</div> }

      @if (data(); as d) {
        <section class="ledger" aria-label="Key figures">
          <a routerLink="/alerts" class="ledger__cell">
            <strong>{{ d.openAlerts }}</strong>
            <span>open alerts</span>
            @if (d.criticalOpenAlerts) { <em>{{ d.criticalOpenAlerts }} critical</em> }
          </a>
          <a routerLink="/transactions" [queryParams]="{ status: 'BLOCKED' }" class="ledger__cell">
            <strong>{{ d.blockedTransactions }}</strong><span>transactions held</span>
          </a>
          <a routerLink="/transactions" [queryParams]="{ status: 'FLAGGED' }" class="ledger__cell">
            <strong>{{ d.flaggedTransactions }}</strong><span>flagged</span>
          </a>
          <a routerLink="/customers" [queryParams]="{ risk: 'HIGH' }" class="ledger__cell">
            <strong>{{ d.highRiskCustomers }}</strong><span>high-risk customers</span>
          </a>
          <a routerLink="/sars" class="ledger__cell">
            <strong>{{ d.sarsFiled }}</strong><span>SARs filed</span>
          </a>
          <div class="ledger__cell">
            <strong>{{ d.volumeLast30Days | currency:'USD':'symbol':'1.0-0' }}</strong>
            <span>volume monitored, {{ d.totalTransactions | number }} transactions in total</span>
          </div>
        </section>

        <div class="grid grid--main-side" style="margin-top:14px">
          <section class="panel">
            <h2>Daily activity</h2>
            <svg class="chart" [attr.viewBox]="'0 0 ' + chartW + ' ' + (chartH + 22)" role="img"
                 [attr.aria-label]="'Transactions per day for the last 30 days, peak ' + chartMax() + ' per day'">
              <line x1="0" [attr.x2]="chartW" [attr.y1]="0.5" [attr.y2]="0.5" class="chart__grid" />
              <text x="0" y="12" class="chart__axis">{{ chartMax() }} / day</text>
              <line x1="0" [attr.x2]="chartW" [attr.y1]="chartH - 0.5" [attr.y2]="chartH - 0.5" class="chart__base" />
              @for (b of bars(); track b.date) {
                <g>
                  <title>{{ b.date | date:'d MMM' }}: {{ b.total }} total, {{ b.flagged }} flagged, {{ b.blocked }} held</title>
                  <rect [attr.x]="b.x" [attr.width]="b.w" [attr.y]="b.yClear" [attr.height]="b.hClear" class="chart__clear" />
                  <rect [attr.x]="b.x" [attr.width]="b.w" [attr.y]="b.yFlag" [attr.height]="b.hFlag" class="chart__flag" />
                  <rect [attr.x]="b.x" [attr.width]="b.w" [attr.y]="b.yBlock" [attr.height]="b.hBlock" class="chart__block" />
                </g>
              }
              @if (bars().length) {
                <text x="0" [attr.y]="chartH + 18" class="chart__axis">{{ bars()[0].date | date:'d MMM' }}</text>
                <text [attr.x]="chartW" [attr.y]="chartH + 18" class="chart__axis" text-anchor="end">Today</text>
              }
            </svg>
            <ul class="key">
              <li><span class="chart__clear"></span>Cleared</li>
              <li><span class="chart__flag"></span>Flagged</li>
              <li><span class="chart__block"></span>Held</li>
            </ul>
          </section>

          <section class="panel">
            <h2>Open alerts by severity</h2>
            <ul class="bars">
              @for (s of severities; track s) {
                <li>
                  <a routerLink="/alerts" [queryParams]="{ severity: s }">{{ s | label }}</a>
                  <span class="bars__track"><span class="bars__fill sev-{{ s }}" [style.width.%]="pct(d.openAlertsBySeverity[s], maxSeverity())"></span></span>
                  <span class="num">{{ d.openAlertsBySeverity[s] }}</span>
                </li>
              }
            </ul>
            <h2 style="margin-top:24px">Customers by risk rating</h2>
            <ul class="bars">
              @for (r of riskLevels; track r) {
                <li>
                  <a routerLink="/customers" [queryParams]="{ risk: r }">{{ r | label }}</a>
                  <span class="bars__track"><span class="bars__fill risk-{{ r }}" [style.width.%]="pct(d.customersByRisk[r], maxRisk())"></span></span>
                  <span class="num">{{ d.customersByRisk[r] }}</span>
                </li>
              }
            </ul>
          </section>
        </div>

        <div class="grid grid--main-side" style="margin-top:14px">
          <section class="panel panel--flush">
            <h2>Latest alerts</h2>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>Alert</th><th>Customer</th><th>Finding</th><th class="num">Score</th><th>Status</th></tr></thead>
                <tbody>
                  @for (a of d.recentAlerts; track a.id) {
                    <tr class="link" (click)="router.navigate(['/alerts', a.id])">
                      <td class="nowrap"><a [routerLink]="['/alerts', a.id]">{{ a.alertNumber }}</a><br><span class="faint">{{ a.createdAt | date:'d MMM, HH:mm' }}</span></td>
                      <td>{{ a.customerName }}</td>
                      <td>{{ a.summary }}</td>
                      <td class="num"><span class="pill {{ a.severity }}">{{ a.score }}</span></td>
                      <td><span class="pill {{ a.status }}">{{ a.status | label }}</span></td>
                    </tr>
                  } @empty {
                    <tr><td colspan="5" class="empty">No alerts yet. Submit a transaction to see the rules at work.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </section>

          <section class="panel">
            <h2>Alerts raised by rule</h2>
            <ul class="bars bars--wide">
              @for (r of ruleRows(); track r.code) {
                <li>
                  <span>{{ r.label }}</span>
                  <span class="bars__track"><span class="bars__fill" [style.width.%]="pct(r.count, ruleMax())"></span></span>
                  <span class="num">{{ r.count }}</span>
                </li>
              } @empty {
                <li class="muted">No rule has raised an alert yet.</li>
              }
            </ul>
          </section>
        </div>
      } @else if (!error()) {
        <p class="muted">Loading…</p>
      }
    </div>
  `,
  styles: `
    .ledger { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 14px; }
    .ledger__cell {
      position: relative; display: flex; flex-direction: column; gap: 4px; padding: 14px 16px 14px 19px;
      background: var(--surface); border: 1px solid var(--card-border); border-radius: var(--radius);
      box-shadow: var(--shadow-card); color: var(--ink); text-decoration: none; overflow: hidden;
      transition: box-shadow 0.2s ease, transform 0.2s ease;
    }
    .ledger__cell::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 3px; background: var(--theme-primary); }
    a.ledger__cell:hover { text-decoration: none; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1); transform: translateY(-2px); }
    .ledger strong { order: 2; font-size: var(--step-2); font-weight: 500; line-height: 1.1; color: #1f2937; }
    .ledger span { order: 1; color: #4b5563; font-size: var(--step--1); font-weight: 500; }
    .ledger em { order: 3; font-style: normal; font-size: 0.75rem; font-weight: 600; color: var(--risk-critical); }
    @media (max-width: 1100px) { .ledger { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
    @media (max-width: 560px) { .ledger { grid-template-columns: repeat(2, minmax(0, 1fr)); } }

    .chart { width: 100%; height: auto; display: block; }
    .chart__grid { stroke: #e5e7eb; stroke-dasharray: 3 3; }
    .chart__base { stroke: var(--rule); }
    .chart__axis { fill: var(--ink-faint); font-size: 11px; }
    .chart__clear { fill: #9ca3af; background: #9ca3af; }
    .chart__flag { fill: #f59e0b; background: #f59e0b; }
    .chart__block { fill: #ef4444; background: #ef4444; }
    .key { display: flex; gap: 16px; list-style: none; padding: 0; margin: 10px 0 0; font-size: var(--step--1); color: var(--ink-soft); }
    .key span { display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 6px; }

    .bars { list-style: none; margin: 0; padding: 0; }
    .bars li { display: grid; grid-template-columns: 76px 1fr 32px; gap: 10px; align-items: center; padding: 5px 0; font-size: var(--step--1); }
    .bars--wide li { grid-template-columns: minmax(0, 1.4fr) 1fr 32px; }
    .bars__track { height: 8px; background: #f1f5f9; border-radius: 4px; overflow: hidden; }
    .bars__fill { display: block; height: 100%; background: var(--action); border-radius: 4px; }
    .sev-CRITICAL { background: var(--risk-critical); }
    .sev-HIGH, .risk-HIGH { background: var(--risk-high); }
    .sev-MEDIUM, .risk-MEDIUM { background: var(--risk-medium); }
    .sev-LOW, .risk-LOW { background: var(--risk-low); }
  `,
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  protected readonly router = inject(Router);

  protected readonly data = signal<Dashboard | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly severities = SEVERITIES;
  protected readonly riskLevels = ['HIGH', 'MEDIUM', 'LOW'] as const;

  protected readonly chartW = 600;
  protected readonly chartH = 160;

  protected readonly chartMax = computed(() => Math.max(1, ...(this.data()?.daily ?? []).map(d => d.total)));

  protected readonly bars = computed(() => {
    const days = this.data()?.daily ?? [];
    const slot = this.chartW / Math.max(1, days.length);
    const w = Math.max(2, slot * 0.68);
    const scale = (this.chartH - 18) / this.chartMax();
    return days.map((d, i) => {
      const cleared = d.total - d.flagged - d.blocked;
      const hBlock = d.blocked * scale;
      const hFlag = d.flagged * scale;
      const hClear = cleared * scale;
      const yBlock = this.chartH - hBlock;
      const yFlag = yBlock - hFlag;
      const yClear = yFlag - hClear;
      return { ...d, x: i * slot + (slot - w) / 2, w, hBlock, hFlag, hClear, yBlock, yFlag, yClear };
    });
  });

  protected readonly maxSeverity = computed(() =>
    Math.max(1, ...Object.values(this.data()?.openAlertsBySeverity ?? {})));
  protected readonly maxRisk = computed(() =>
    Math.max(1, ...Object.values(this.data()?.customersByRisk ?? {})));

  protected readonly ruleRows = computed(() =>
    Object.entries(this.data()?.alertsByRule ?? {})
      .map(([code, count]) => ({ code, count, label: label(code) }))
      .sort((a, b) => b.count - a.count));
  protected readonly ruleMax = computed(() => Math.max(1, ...this.ruleRows().map(r => r.count)));

  constructor() {
    this.api.dashboard().subscribe({
      next: d => this.data.set(d),
      error: e => this.error.set(errorMessage(e)),
    });
  }

  protected pct(value: number | undefined, max: number): number {
    return Math.round(((value ?? 0) / max) * 100);
  }
}
