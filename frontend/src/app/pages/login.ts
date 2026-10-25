import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/labels';
import { BrandMark } from '../shared/brand-mark';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, BrandMark],
  template: `
    <main class="card">
      <div class="card__brand"><app-brand-mark /> AML Monitor</div>
      <div class="card__body">
        <h1>Sign in</h1>
        @if (safeReturnUrl() !== '/') {
          <div class="banner banner--info" role="status">Sign in to continue. You will go straight to the page you opened.</div>
        } @else {
          <p>Use the account your administrator approved.</p>
        }

        @if (error()) {
          <div class="banner banner--error" role="alert">
            {{ error() }}
            @if (unverified()) {
              @if (resent()) {
                <span class="resend">A new link is on its way.</span>
              } @else {
                <button class="resend linkish" type="button" (click)="resend()">Send the link again</button>
              }
            }
          </div>
        }

        <form [formGroup]="form" (ngSubmit)="submit()">
          <label class="field">
            <span>Username or email</span>
            <input type="text" formControlName="username" autocomplete="username" autocapitalize="none" spellcheck="false" />
          </label>
          <label class="field">
            <span>Password</span>
            <input type="password" formControlName="password" autocomplete="current-password" />
          </label>
          <button class="btn btn--primary" type="submit" [disabled]="form.invalid || busy()">
            {{ busy() ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>
      </div>
      <div class="card__foot">New here? <a routerLink="/signup">Create an account</a></div>
    </main>
  `,
  styleUrl: '../shared/auth-card.scss',
  styles: `
    .resend { display: block; margin-top: 6px; }
    .banner--info { background: var(--surface-sunk); color: var(--ink); border-color: var(--rule); }
    .linkish { background: none; border: 0; padding: 0; font: inherit; font-weight: 600; color: inherit; text-decoration: underline; cursor: pointer; }
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** From ?returnUrl=, set by the auth guard. */
  readonly returnUrl = input<string | undefined>();

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** The password was right but the email is not confirmed yet: offer a new link. */
  protected readonly unverified = signal(false);
  protected readonly resent = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  protected submit(): void {
    const { username, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.unverified.set(false);
    this.resent.set(false);
    this.auth.login(username, password).subscribe({
      next: () => this.router.navigateByUrl(this.safeReturnUrl()),
      error: e => {
        this.error.set(errorMessage(e));
        this.unverified.set(e instanceof HttpErrorResponse && e.error?.code === 'EMAIL_NOT_VERIFIED');
        this.form.controls.password.reset();
        this.busy.set(false);
      },
    });
  }

  protected resend(): void {
    this.auth.resendVerification(this.form.controls.username.value).subscribe({
      next: () => this.resent.set(true),
      error: e => this.error.set(errorMessage(e)),
    });
  }

  /** Only paths inside this app, never another site. */
  protected safeReturnUrl(): string {
    const url = this.returnUrl();
    return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login') ? url : '/';
  }
}
