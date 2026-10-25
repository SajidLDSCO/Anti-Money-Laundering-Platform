import { Component, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/labels';
import { User } from '../core/models';
import { BrandMark } from '../shared/brand-mark';

/** Opened from the link in the confirmation email: /verify-email?token=... */
@Component({
  selector: 'app-verify-email',
  imports: [RouterLink, BrandMark],
  template: `
    <main class="card">
      <div class="card__brand"><app-brand-mark /> AML Monitor</div>
      <div class="card__body">
        @if (user(); as u) {
          <h1>Email confirmed</h1>
          @if (u.enabled) {
            <div class="banner banner--ok" role="status">Thanks, {{ u.displayName }}. You can sign in now.</div>
          } @else {
            <div class="banner banner--ok" role="status">
              Thanks, {{ u.displayName }}. An administrator will now review your account. We will email
              <strong>{{ u.email }}</strong> when you can sign in.
            </div>
          }
          <a class="btn btn--primary" routerLink="/login">Go to sign in</a>
        } @else if (error()) {
          <h1>Link not valid</h1>
          <div class="banner banner--error" role="alert">{{ error() }}</div>
          <a class="btn btn--primary" routerLink="/login">Go to sign in</a>
        } @else {
          <h1>Confirming your email…</h1>
        }
      </div>
    </main>
  `,
  styleUrl: '../shared/auth-card.scss',
})
export class VerifyEmailPage implements OnInit {
  private readonly auth = inject(AuthService);

  /** From ?token= in the link. */
  readonly token = input<string | undefined>();

  protected readonly user = signal<User | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const token = this.token();
    if (!token) {
      this.error.set('This link is missing its confirmation code. Open the link from the email again.');
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: u => this.user.set(u),
      error: e => this.error.set(errorMessage(e)),
    });
  }
}
