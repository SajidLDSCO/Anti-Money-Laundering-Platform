import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { LabelPipe, errorMessage } from '../core/labels';
import { ImportResult, SanctionsEntry, ScreeningResult } from '../core/models';

@Component({
  selector: 'app-screening',
  imports: [FormsModule, DecimalPipe, LabelPipe],
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Screening</h1>
          <p>Check a name against the sanctions list before onboarding a customer or paying a new beneficiary.
             Matching tolerates typos, transliteration and word order.</p>
        </div>
      </header>

      <section class="panel">
        <form class="filters" style="margin:0" (submit)="$event.preventDefault(); screen()">
          <label class="field grow">
            <span>Name to screen</span>
            <input type="search" name="name" [(ngModel)]="name" placeholder="Person or company name" autocomplete="off" />
          </label>
          <button class="btn btn--primary" type="submit" [disabled]="name.trim().length < 3">Screen name</button>
        </form>

        @if (result(); as r) {
          <div class="outcome" [class.outcome--hit]="r.hit">
            @if (r.hit) {
              <strong>Possible match.</strong> Do not proceed until the match is reviewed and discounted or confirmed.
            } @else if (r.matches.length) {
              <strong>No match above {{ r.threshold * 100 | number:'1.0-0' }}%.</strong> Near misses are listed for reference.
            } @else {
              <strong>No match.</strong> "{{ r.query }}" is not similar to any listed name.
            }
          </div>
          @if (r.matches.length) {
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>Listed name</th><th>Matched on</th><th>Type</th><th>Country</th><th>List and programme</th><th class="num">Similarity</th></tr></thead>
                <tbody>
                  @for (m of r.matches; track m.entryId) {
                    <tr>
                      <td><strong>{{ m.listedName }}</strong></td>
                      <td>{{ m.matchedOn }}</td>
                      <td>{{ m.entityType | label }}</td>
                      <td>{{ m.country ?? '—' }}</td>
                      <td>{{ m.listSource }}@if (m.program) {, {{ m.program }} }</td>
                      <td class="num"><span class="pill" [class.HIGH]="m.score >= r.threshold">{{ m.score * 100 | number:'1.0-0' }}%</span></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        }
      </section>

      <section class="panel">
        <div class="list-head">
          <div>
            <h2>Sanctions list</h2>
            <p class="muted">{{ entries().length }} entries. The demo list is fictional; load your regulator's lists before real use.</p>
          </div>
          @if (auth.can('ADMIN')) {
            <div class="actions">
              <label class="btn">
                Import CSV
                <input type="file" accept=".csv,text/csv" (change)="importFile($event)" hidden />
              </label>
              <button class="btn" type="button" (click)="adding.set(!adding())">Add entry</button>
            </div>
          }
        </div>

        @if (message()) { <div class="banner banner--ok">{{ message() }}</div> }
        @if (error()) { <div class="banner banner--error">{{ error() }}</div> }

        @if (adding() && auth.can('ADMIN')) {
          <form class="form-grid add-form" (submit)="$event.preventDefault(); add()">
            <label class="field"><span>Name</span><input type="text" name="n" [(ngModel)]="draft.name" required /></label>
            <label class="field"><span>Aliases</span><input type="text" name="al" [(ngModel)]="draft.aliases" /><small>Separate with |</small></label>
            <label class="field"><span>Type</span>
              <select name="t" [(ngModel)]="draft.entityType"><option value="INDIVIDUAL">Individual</option><option value="ENTITY">Entity</option><option value="VESSEL">Vessel</option></select>
            </label>
            <label class="field"><span>Country</span><input type="text" name="c" [(ngModel)]="draft.country" maxlength="2" /></label>
            <label class="field"><span>List</span><input type="text" name="s" [(ngModel)]="draft.listSource" placeholder="e.g. OFAC-SDN" /></label>
            <label class="field"><span>Programme</span><input type="text" name="p" [(ngModel)]="draft.program" /></label>
            <div class="span-2 actions"><button class="btn btn--primary" type="submit" [disabled]="!draft.name.trim()">Save entry</button></div>
          </form>
        }

        <label class="field" style="margin:16px 0 8px; max-width:320px">
          <span>Filter</span>
          <input type="search" [(ngModel)]="filterText" name="f" placeholder="Name or alias" />
        </label>
        <div class="table-wrap">
          <table class="data">
            <thead><tr><th>Name</th><th>Aliases</th><th>Type</th><th>Country</th><th>List</th><th>Programme</th><th></th></tr></thead>
            <tbody>
              @for (e of visible(); track e.id) {
                <tr>
                  <td><strong>{{ e.name }}</strong></td>
                  <td class="muted">{{ e.aliases?.split('|')?.join(', ') }}</td>
                  <td>{{ e.entityType | label }}</td>
                  <td>{{ e.country }}</td>
                  <td class="nowrap">{{ e.listSource }}</td>
                  <td>{{ e.program }}</td>
                  <td class="num">
                    @if (auth.can('ADMIN')) { <button type="button" class="btn btn--quiet" (click)="remove(e)">Remove</button> }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="7" class="empty">No entries. Import a CSV or add one.</td></tr>
              }
            </tbody>
          </table>
        </div>
        <p class="faint" style="margin-top:12px">CSV header: name,aliases,entityType,country,listSource,program</p>
      </section>
    </div>
  `,
  styles: `
    .outcome { margin: 18px 0 14px; padding: 12px 14px; border-radius: var(--radius-sm); background: var(--risk-low-tint); color: var(--risk-low); }
    .outcome--hit { background: var(--risk-high-tint); color: var(--risk-high); }
    .list-head { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; align-items: flex-start; margin-bottom: 12px; }
    .list-head h2 { margin-bottom: 4px; }
    .add-form { padding: 16px; background: var(--surface-sunk); border-radius: var(--radius-sm); }
  `,
})
export class ScreeningPage {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected name = '';
  protected readonly result = signal<ScreeningResult | null>(null);
  protected readonly entries = signal<SanctionsEntry[]>([]);
  protected readonly filter = signal('');
  protected readonly adding = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected draft = this.emptyDraft();

  protected get filterText(): string { return this.filter(); }
  protected set filterText(v: string) { this.filter.set(v); }

  protected readonly visible = computed(() => {
    const f = this.filter().trim().toLowerCase();
    return f ? this.entries().filter(e => `${e.name} ${e.aliases ?? ''}`.toLowerCase().includes(f)) : this.entries();
  });

  constructor() {
    this.loadList();
  }

  protected screen(): void {
    this.api.screen(this.name.trim()).subscribe({
      next: r => this.result.set(r),
      error: e => this.error.set(errorMessage(e)),
    });
  }

  protected add(): void {
    this.api.addSanction({ ...this.draft, country: this.draft.country?.toUpperCase() || null }).subscribe({
      next: () => { this.flash('Entry added.'); this.draft = this.emptyDraft(); this.adding.set(false); this.loadList(); },
      error: e => this.error.set(errorMessage(e)),
    });
  }

  protected remove(e: SanctionsEntry): void {
    if (e.id === null || !confirm(`Remove "${e.name}" from the sanctions list?`)) {
      return;
    }
    this.api.deleteSanction(e.id).subscribe({
      next: () => { this.flash('Entry removed.'); this.loadList(); },
      error: err => this.error.set(errorMessage(err)),
    });
  }

  protected importFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.api.importSanctions(file).subscribe({
      next: (r: ImportResult) => {
        this.flash(`Imported ${r.imported} entries` + (r.skipped ? `, skipped ${r.skipped}: ${r.errors.join('; ')}` : '.'));
        this.loadList();
        input.value = '';
      },
      error: e => { this.error.set(errorMessage(e)); input.value = ''; },
    });
  }

  private loadList(): void {
    this.api.sanctions().subscribe({
      next: l => this.entries.set(l),
      error: e => this.error.set(errorMessage(e)),
    });
  }

  private flash(text: string): void {
    this.error.set(null);
    this.message.set(text);
    setTimeout(() => this.message.set(null), 5000);
  }

  private emptyDraft(): SanctionsEntry {
    return { id: null, name: '', aliases: '', entityType: 'INDIVIDUAL', country: '', listSource: '', program: '' };
  }
}
