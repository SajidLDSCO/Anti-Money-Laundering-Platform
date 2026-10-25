import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/labels';
import { Role, User } from '../core/models';

@Component({
  selector: 'app-users',
  imports: [DatePipe],
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <h1>Users</h1>
          <p>Approve new sign-ups and set what each person may do.</p>
        </div>
        @if (pendingCount()) { <span class="pill PENDING">{{ pendingCount() }} waiting for approval</span> }
      </header>

      @if (error()) { <div class="banner banner--error">{{ error() }}</div> }
      @if (message()) { <div class="banner banner--ok" role="status">{{ message() }}</div> }

      <!-- Opened from the "waiting for approval" email: /users?review=<username> -->
      @if (review() && loaded() && !message()) {
        @if (reviewed(); as u) {
          <section class="panel review">
            <h2>Review sign-up</h2>
            <dl class="facts">
              <dt>Name</dt><dd><strong>{{ u.displayName }}</strong></dd>
              <dt>Username</dt><dd>{{ u.username }}</dd>
              <dt>Email</dt><dd>{{ u.email ?? '—' }} @if (u.emailVerified) { <span class="pill ACTIVE">Confirmed</span> } @else { <span class="pill">Not confirmed</span> }</dd>
              <dt>Signed up</dt><dd>{{ u.createdAt | date:'d MMM y, HH:mm' }}</dd>
              <dt>Role if approved</dt><dd>{{ roleName(u.role) }}</dd>
            </dl>
            @if (isPending(u)) {
              <div class="actions review__actions">
                <button class="btn btn--primary" type="button" (click)="setEnabled(u, true)">Approve</button>
                <button class="btn btn--danger" type="button" (click)="decline(u)">Decline</button>
              </div>
              @if (!u.emailVerified) {
                <p class="faint">This person has not confirmed their email yet. Approving now lets them sign in once they do.</p>
              }
            } @else {
              <p class="muted">Already {{ u.enabled ? 'approved and active' : 'reviewed (disabled)' }}. Nothing left to decide.</p>
            }
          </section>
        } @else {
          <div class="banner banner--error">No account called {{ review() }}. It may have been declined already.</div>
        }
      }

      <section class="panel panel--flush">
        <div class="table-wrap">
          <table class="data">
            <thead><tr><th>User</th><th>Email</th><th>Role</th><th>Status</th><th>Joined</th><th>Last sign-in</th><th></th></tr></thead>
            <tbody>
              @for (u of users(); track u.id) {
                <tr>
                  <td><strong>{{ u.displayName }}</strong><br><span class="faint">{{ u.username }}</span></td>
                  <td>{{ u.email ?? '—' }}</td>
                  <td>
                    @if (isMe(u)) {
                      {{ roleName(u.role) }} <span class="faint">(you)</span>
                    } @else {
                      <select class="role" [value]="u.role" (change)="setRole(u, $any($event.target).value)"
                              [attr.aria-label]="'Role for ' + u.displayName">
                        @for (r of roles; track r) { <option [value]="r">{{ roleName(r) }}</option> }
                      </select>
                    }
                  </td>
                  <td>
                    @if (!u.emailVerified) { <span class="pill" title="Has not opened the confirmation link yet">Email not confirmed</span> }
                    @else if (u.enabled) { <span class="pill ACTIVE">Active</span> }
                    @else if (!u.lastLoginAt) { <span class="pill PENDING">Waiting for approval</span> }
                    @else { <span class="pill">Disabled</span> }
                  </td>
                  <td class="nowrap">{{ u.createdAt | date:'d MMM y' }}</td>
                  <td class="nowrap">{{ u.lastLoginAt ? (u.lastLoginAt | date:'d MMM y, HH:mm') : '—' }}</td>
                  <td class="num">
                    @if (!isMe(u)) {
                      <div class="actions row-actions">
                        @if (u.enabled) {
                          <button class="btn btn--danger" type="button" (click)="setEnabled(u, false)">Disable</button>
                        } @else if (isPending(u)) {
                          <button class="btn btn--primary" type="button" (click)="setEnabled(u, true)">Approve</button>
                          <button class="btn btn--danger" type="button" (click)="decline(u)">Decline</button>
                        } @else {
                          <button class="btn btn--primary" type="button" (click)="setEnabled(u, true)">Enable</button>
                        }
                      </div>
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="7" class="empty">No users yet.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>

      <section class="panel" style="margin-top:14px">
        <h2>Roles</h2>
        <dl class="facts">
          <dt>Analyst</dt><dd>Works alerts: assign, notes, escalate, close as false positive. Submits transactions, onboards customers, screens names.</dd>
          <dt>MLRO</dt><dd>Everything an analyst can do, plus filing SARs and freezing accounts.</dd>
          <dt>Admin</dt><dd>Everything an MLRO can do, plus managing users, detection rules and the sanctions list.</dd>
        </dl>
      </section>
    </div>
  `,
  styles: `
    select.role { width: auto; min-height: 28px; padding: 2px 8px; font-size: var(--step--1); }
    .review { margin-bottom: 14px; max-width: 640px; }
    .review__actions { margin: 14px 0 6px; }
    .row-actions { justify-content: flex-end; flex-wrap: nowrap; }
  `,
})
export class UsersPage {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  /** From ?review=<username>, the link in the "waiting for approval" email. */
  readonly review = input<string | undefined>();

  protected readonly roles: Role[] = ['ANALYST', 'MLRO', 'ADMIN'];
  protected readonly users = signal<User[]>([]);
  protected readonly loaded = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly reviewed = computed(() =>
    this.users().find(u => u.username === this.review()?.trim().toLowerCase()) ?? null);
  protected readonly pendingCount = computed(() =>
    this.users().filter(u => u.emailVerified && !u.enabled && !u.lastLoginAt).length);

  constructor() {
    this.load();
  }

  protected isMe(u: User): boolean {
    return u.username === this.auth.user()?.username;
  }

  protected roleName(r: Role): string {
    return r === 'MLRO' ? 'MLRO' : r.charAt(0) + r.slice(1).toLowerCase();
  }

  protected setRole(u: User, role: Role): void {
    this.save(u, { role });
  }

  protected setEnabled(u: User, enabled: boolean): void {
    this.save(u, { enabled }, enabled && this.isPending(u)
      ? `Approved ${u.displayName}. They have been emailed that they can sign in.` : null);
  }

  /** Never approved and never signed in: can still be approved or declined. */
  protected isPending(u: User): boolean {
    return !u.enabled && !u.lastLoginAt;
  }

  protected decline(u: User): void {
    if (!confirm(`Decline ${u.displayName} (${u.username})? The account is deleted and they are told by email.`)) {
      return;
    }
    this.error.set(null);
    this.api.declineUser(u.id).subscribe({
      next: () => {
        this.users.update(list => list.filter(x => x.id !== u.id));
        this.message.set(`Declined ${u.displayName}. The account was removed and they have been emailed.`);
      },
      error: e => this.error.set(errorMessage(e)),
    });
  }

  private save(u: User, change: { role?: Role; enabled?: boolean }, success: string | null = null): void {
    this.error.set(null);
    this.message.set(null);
    this.api.updateUser(u.id, change).subscribe({
      next: updated => {
        this.users.update(list => list.map(x => (x.id === updated.id ? updated : x)));
        this.message.set(success);
      },
      error: e => {
        this.error.set(errorMessage(e));
        // Show the saved state again, e.g. put a role select back.
        this.users.set([]);
        this.load();
      },
    });
  }

  private load(): void {
    this.api.users().subscribe({
      next: u => { this.users.set(u); this.loaded.set(true); },
      error: e => this.error.set(errorMessage(e)),
    });
  }
}
