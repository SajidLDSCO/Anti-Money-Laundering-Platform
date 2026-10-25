import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { ApiService } from '../core/api.service';
import { LabelPipe, errorMessage, label } from '../core/labels';
import { AlertDetail } from '../core/models';
import { EvidenceBar } from '../shared/evidence-bar';

type Decision = 'none' | 'fp' | 'sar';

@Component({
  selector: 'app-alert-detail',
  imports: [FormsModule, RouterLink, DatePipe, CurrencyPipe, LabelPipe, EvidenceBar],
  template: `
    <div class="page">
      @if (error()) { <div class="banner banner--error">{{ error() }}</div> }

      @if (d(); as d) {
        <header class="page-head">
          <div>
            <a class="back" routerLink="/alerts">Back to alerts</a>
            <h1>{{ d.alert.alertNumber }}</h1>
            <p>{{ d.alert.summary }}</p>
          </div>
          <div class="actions">
            <span class="pill {{ d.alert.severity }}">{{ d.alert.severity | label }} severity</span>
            <span class="pill {{ d.alert.status }}">{{ d.alert.status | label }}</span>
          </div>
        </header>

        <div class="grid grid--main-side">
          <div class="stack">
            <section class="panel">
              <h2>Why this alert was raised</h2>
              <app-evidence-bar [hits]="d.hits" [score]="d.alert.score" />
            </section>

            @if (d.transaction; as t) {
              <section class="panel">
                <h2>Transaction</h2>
                <dl class="facts">
                  <dt>Reference</dt><dd>{{ t.reference }}</dd>
                  <dt>When</dt><dd>{{ t.timestamp | date:'d MMM y, HH:mm' }}</dd>
                  <dt>Type</dt><dd>{{ t.type | label }} via {{ (t.channel ?? 'unknown') | label }}</dd>
                  <dt>Amount</dt><dd><strong>{{ t.amount | currency:t.currency }}</strong></dd>
                  <dt>Account</dt><dd>{{ t.accountNumber }}</dd>
                  <dt>Counterparty</dt>
                  <dd>{{ t.counterpartyName ?? '—' }}@if (t.counterpartyCountry) { ({{ t.counterpartyCountry }}) }</dd>
                  @if (t.description) { <dt>Description</dt><dd>{{ t.description }}</dd> }
                  <dt>Outcome</dt><dd><span class="pill {{ t.status }}">{{ t.status === 'BLOCKED' ? 'Held' : (t.status | label) }}</span></dd>
                </dl>
              </section>
            }

            <section class="panel">
              <h2>Investigation trail</h2>
              <ol class="trail">
                @for (n of d.notes; track n.id) {
                  <li [class.trail--system]="n.systemEntry">
                    <div class="trail__meta"><strong>{{ n.author }}</strong> <span class="faint">{{ n.createdAt | date:'d MMM y, HH:mm' }}</span></div>
                    <p>{{ n.text }}</p>
                  </li>
                }
              </ol>
              <form class="note-form" (submit)="$event.preventDefault(); addNote()">
                <label class="field">
                  <span>Add a note</span>
                  <textarea name="note" [(ngModel)]="note" placeholder="What you checked, who you spoke to, what you found" rows="3"></textarea>
                </label>
                <div class="actions"><button class="btn" type="submit" [disabled]="busy() || !note.trim()">Add note</button></div>
              </form>
            </section>
          </div>

          <div class="stack">
            <section class="panel decision">
              <h2>Decision</h2>
              @if (closed()) {
                @if (d.sar; as s) {
                  <p>SAR <strong>{{ s.sarNumber }}</strong> filed by {{ s.filedBy }} on {{ s.filedAt | date:'d MMM y' }}.</p>
                  <details><summary>Narrative</summary><p class="narrative">{{ s.narrative }}</p></details>
                } @else {
                  <p>Closed as a false positive by {{ d.alert.assignedTo }}. The rationale is in the trail.</p>
                }
              } @else {
                <p class="muted">
                  @if (d.alert.assignedTo) { Assigned to <strong>{{ d.alert.assignedTo }}</strong>. } @else { Not assigned yet. }
                </p>
                <div class="actions">
                  @if (d.alert.assignedTo !== auth.user()?.username) {
                    <button class="btn" type="button" (click)="assign()" [disabled]="busy()">Assign to me</button>
                  }
                  @if (d.alert.status !== 'ESCALATED') {
                    <button class="btn" type="button" (click)="escalate()" [disabled]="busy()">Escalate to MLRO</button>
                  }
                </div>
                <div class="actions" style="margin-top:8px">
                  <button class="btn" type="button" [class.btn--primary]="mode() === 'fp'" (click)="mode.set(mode() === 'fp' ? 'none' : 'fp')">Close as false positive</button>
                  @if (auth.can('MLRO')) {
                    <button class="btn btn--danger" type="button" (click)="openSar()">File SAR</button>
                  }
                </div>

                @if (mode() === 'fp') {
                  <form class="decision__form" (submit)="$event.preventDefault(); closeFalsePositive()">
                    <label class="field">
                      <span>Rationale</span>
                      <textarea name="rationale" [(ngModel)]="rationale" rows="4" required
                        placeholder="Why the activity is explained, and what evidence you relied on"></textarea>
                      @if (d.transaction?.status === 'BLOCKED') { <small>The held transaction will be released and posted.</small> }
                    </label>
                    <button class="btn btn--primary" type="submit" [disabled]="busy() || !rationale.trim()">Close alert</button>
                  </form>
                }
                @if (mode() === 'sar') {
                  <form class="decision__form" (submit)="$event.preventDefault(); fileSar()">
                    <label class="field">
                      <span>SAR narrative</span>
                      <textarea name="narrative" [(ngModel)]="narrative" rows="10" required></textarea>
                      <small>{{ narrative.trim().length }} characters, at least 50. Who, what, when, where, and why it is suspicious.</small>
                    </label>
                    <button class="btn btn--danger" type="submit" [disabled]="busy() || narrative.trim().length < 50">File SAR and close</button>
                  </form>
                }
              }
            </section>

            <section class="panel">
              <h2>Customer</h2>
              <p><a [routerLink]="['/customers', d.customer.id]"><strong>{{ d.customer.fullName }}</strong></a></p>
              <p class="faint">{{ d.customer.customerNumber }}, {{ d.customer.customerType | label }}</p>
              <dl class="facts" style="margin-top:12px">
                <dt>Risk rating</dt><dd><span class="pill {{ d.customer.riskLevel }}">{{ d.customer.riskLevel | label }} ({{ d.customer.riskScore }})</span></dd>
                <dt>KYC</dt><dd><span class="pill {{ d.customer.kycStatus }}">{{ d.customer.kycStatus | label }}</span></dd>
                <dt>PEP</dt><dd>{{ d.customer.pep ? 'Yes' : 'No' }}</dd>
                <dt>Occupation</dt><dd>{{ d.customer.occupation ?? '—' }}</dd>
                <dt>Residence</dt><dd>{{ d.customer.countryOfResidence ?? '—' }}</dd>
              </dl>
              @if (d.customer.riskFactors) {
                <ul class="factors">
                  @for (f of factors(); track f) { <li>{{ f }}</li> }
                </ul>
              }
            </section>
          </div>
        </div>
      } @else if (!error()) {
        <p class="muted">Loading…</p>
      }
    </div>
  `,
  styles: `
    .trail { list-style: none; margin: 0; padding: 0 0 0 16px; border-left: 2px solid var(--rule); }
    .trail li { position: relative; padding: 0 0 16px 4px; }
    .trail li::before {
      content: ''; position: absolute; left: -22px; top: 6px; width: 10px; height: 10px; border-radius: 50%;
      background: var(--action); border: 2px solid var(--surface);
    }
    .trail li.trail--system::before { background: var(--rule); }
    .trail li.trail--system p { color: var(--ink-soft); }
    .trail__meta { display: flex; flex-wrap: wrap; gap: 8px; align-items: baseline; font-size: var(--step--1); margin-bottom: 2px; }
    .note-form { margin-top: 8px; display: flex; flex-direction: column; gap: 8px; }
    .decision__form { display: flex; flex-direction: column; gap: 10px; margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--rule-soft); }
    .narrative { white-space: pre-wrap; margin-top: 8px; color: var(--ink-soft); }
    details summary { cursor: pointer; color: var(--action); margin-top: 8px; }
    .factors { margin: 12px 0 0; padding-left: 18px; color: var(--ink-soft); font-size: var(--step--1); }
  `,
})
export class AlertDetailPage {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  readonly id = input.required<string>();

  protected readonly d = signal<AlertDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly mode = signal<Decision>('none');

  protected note = '';
  protected rationale = '';
  protected narrative = '';

  protected readonly closed = computed(() => {
    const s = this.d()?.alert.status;
    return s === 'CLOSED_FALSE_POSITIVE' || s === 'CLOSED_SAR_FILED';
  });

  protected readonly factors = computed(() => (this.d()?.customer.riskFactors ?? '').split('; ').filter(Boolean));

  constructor() {
    effect(() => {
      const id = Number(this.id());
      this.api.alert(id).subscribe({
        next: d => this.d.set(d),
        error: e => this.error.set(errorMessage(e)),
      });
    });
  }

  protected assign(): void {
    this.run(this.api.assignAlert(this.alertId()));
  }

  protected escalate(): void {
    this.run(this.api.changeAlertStatus(this.alertId(), 'ESCALATED',
      'Escalated to the MLRO for a filing decision.'));
  }

  protected closeFalsePositive(): void {
    this.run(this.api.changeAlertStatus(this.alertId(), 'CLOSED_FALSE_POSITIVE', this.rationale),
      () => { this.rationale = ''; this.mode.set('none'); });
  }

  protected openSar(): void {
    if (this.mode() === 'sar') {
      this.mode.set('none');
      return;
    }
    if (!this.narrative.trim()) {
      this.narrative = this.draftNarrative();
    }
    this.mode.set('sar');
  }

  protected fileSar(): void {
    this.run(this.api.fileSar(this.alertId(), this.narrative), () => this.mode.set('none'));
  }

  protected addNote(): void {
    const text = this.note.trim();
    if (!text) {
      return;
    }
    this.busy.set(true);
    this.api.addNote(this.alertId(), text).subscribe({
      next: n => {
        const cur = this.d();
        if (cur) {
          this.d.set({ ...cur, notes: [...cur.notes, n] });
        }
        this.note = '';
        this.busy.set(false);
      },
      error: e => { this.error.set(errorMessage(e)); this.busy.set(false); },
    });
  }

  private alertId(): number {
    return Number(this.id());
  }

  private run(call: Observable<AlertDetail>, after?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    call.subscribe({
      next: d => { this.d.set(d); this.busy.set(false); after?.(); },
      error: e => { this.error.set(errorMessage(e)); this.busy.set(false); },
    });
  }

  /** A starting point built from the evidence, which the analyst must edit and complete. */
  private draftNarrative(): string {
    const d = this.d();
    if (!d) {
      return '';
    }
    const t = d.transaction;
    const lines = [
      `Subject: ${d.customer.fullName} (customer ${d.customer.customerNumber}).`,
      t ? `On ${new Date(t.timestamp).toLocaleString()} the subject made a ${label(t.type).toLowerCase()} of ` +
        `${t.amount} ${t.currency}${t.counterpartyName ? ` involving ${t.counterpartyName}` : ''}` +
        `${t.counterpartyCountry ? ` (${t.counterpartyCountry})` : ''}.` : '',
      'Monitoring findings:',
      ...d.hits.map(h => `- ${h.reason}`),
      '',
      'Investigation: [describe what was reviewed, customer contact, documents requested and received].',
      'Grounds for suspicion: [explain why the activity is inconsistent with the customer profile].',
    ];
    return lines.filter(l => l !== null).join('\n');
  }
}
