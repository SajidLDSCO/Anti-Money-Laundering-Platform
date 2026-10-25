import { Component, computed, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/labels';
import { Rule } from '../core/models';

@Component({
  selector: 'app-rules',
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Detection rules</h1>
          <p>Each rule adds points to a transaction's risk score. At 40 an alert is raised; at 80 the transaction is held.
             A sanctions match always holds. Thresholds are set in application.yml.</p>
        </div>
      </header>

      @if (error()) { <div class="banner banner--error">{{ error() }}</div> }

      @for (group of groups(); track group.category) {
        <section class="panel">
          <h2>{{ group.category }}</h2>
          <ul class="rules">
            @for (r of group.rules; track r.code) {
              <li [class.off]="!r.enabled">
                <div class="rules__text">
                  <h3>{{ r.name }} <span class="pts">+{{ r.baseScore }}</span></h3>
                  <p>{{ r.description }}</p>
                  <p class="faint">{{ r.parameters }}</p>
                </div>
                <label class="switch">
                  <input type="checkbox" role="switch" [checked]="r.enabled" [disabled]="!auth.can('ADMIN')"
                         [title]="auth.can('ADMIN') ? '' : 'Only an administrator can switch rules'"
                         (change)="toggle(r, $any($event.target).checked)"
                         [attr.aria-label]="(r.enabled ? 'Turn off ' : 'Turn on ') + r.name" />
                  <span>{{ r.enabled ? 'On' : 'Off' }}</span>
                </label>
              </li>
            }
          </ul>
        </section>
      }
    </div>
  `,
  styles: `
    .rules { list-style: none; margin: 0; padding: 0; }
    .rules li { display: flex; justify-content: space-between; gap: 24px; padding: 14px 0; border-top: 1px solid var(--rule-soft); }
    .rules li:first-child { border-top: 0; padding-top: 0; }
    .rules li.off .rules__text { opacity: 0.55; }
    .rules__text { max-width: 72ch; }
    .rules__text p { color: var(--ink-soft); margin-top: 4px; }
    .pts { font-weight: 600; color: var(--action); margin-left: 6px; font-size: var(--step--1); }
    .switch { display: flex; align-items: center; gap: 8px; cursor: pointer; font-size: var(--step--1); font-weight: 600; white-space: nowrap; }
    .switch input { width: 18px; height: 18px; accent-color: var(--action); }
    .switch:has(input:disabled) { cursor: not-allowed; color: var(--ink-soft); }
  `,
})
export class RulesPage {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly rules = signal<Rule[]>([]);
  protected readonly error = signal<string | null>(null);

  protected readonly groups = computed(() => {
    const map = new Map<string, Rule[]>();
    for (const r of this.rules()) {
      map.set(r.category, [...(map.get(r.category) ?? []), r]);
    }
    return [...map.entries()].map(([category, rules]) => ({ category, rules }));
  });

  constructor() {
    this.api.rules().subscribe({
      next: r => this.rules.set(r),
      error: e => this.error.set(errorMessage(e)),
    });
  }

  protected toggle(rule: Rule, enabled: boolean): void {
    this.api.toggleRule(rule.code, enabled).subscribe({
      next: updated => this.rules.update(list => list.map(r => (r.code === updated.code ? updated : r))),
      error: e => this.error.set(errorMessage(e)),
    });
  }
}
