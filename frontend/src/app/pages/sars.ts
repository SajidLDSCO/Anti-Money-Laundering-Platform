import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/labels';
import { Sar } from '../core/models';

@Component({
  selector: 'app-sars',
  imports: [RouterLink, DatePipe],
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>SARs filed</h1>
          <p>Suspicious activity reports raised from alerts. File them with your financial intelligence unit through its own portal.</p>
        </div>
      </header>

      @if (error()) { <div class="banner banner--error">{{ error() }}</div> }

      @for (s of sars(); track s.id) {
        <article class="panel">
          <div class="sar-head">
            <div>
              <h2>{{ s.sarNumber }}</h2>
              <p class="muted">
                <a [routerLink]="['/customers', s.customerId]">{{ s.customerName }}</a>, from alert
                <a [routerLink]="['/alerts', s.alertId]">{{ s.alertNumber }}</a>
              </p>
            </div>
            <p class="faint">Filed by {{ s.filedBy }}<br>{{ s.filedAt | date:'d MMM y, HH:mm' }}</p>
          </div>
          <p class="narrative">{{ s.narrative }}</p>
        </article>
      } @empty {
        @if (loaded()) { <section class="panel empty">No SARs filed yet. File one from an alert when activity is suspicious.</section> }
      }
    </div>
  `,
  styles: `
    .sar-head { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
    .sar-head h2 { margin-bottom: 2px; }
    .sar-head .faint { text-align: right; }
    .narrative { white-space: pre-wrap; max-width: 80ch; }
  `,
})
export class SarsPage {
  private readonly api = inject(ApiService);
  protected readonly sars = signal<Sar[]>([]);
  protected readonly loaded = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.api.sars().subscribe({
      next: s => { this.sars.set(s); this.loaded.set(true); },
      error: e => this.error.set(errorMessage(e)),
    });
  }
}
