import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/labels';
import { BrandMark } from '../shared/brand-mark';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  return group.get('password')?.value === group.get('confirm')?.value ? null : { mismatch: true };
}

@Component({
  selector: 'app-signup',
  imports: [ReactiveFormsModule, RouterLink, BrandMark],
  template: `
    <main class="card">
      <div class="card__brand"><app-brand-mark /> AML Monitor</div>
      <div class="card__body">
        @if (sentTo(); as address) {
          <h1>Check your email</h1>
          <div class="banner banner--ok" role="status">
            We sent a confirmation link to <strong>{{ address }}</strong>. Open it to confirm your address.
          </div>
          <p class="next">
            The link expires in 24 hours. After you confirm, an administrator reviews your account and you get
            another email when you can sign in.
          </p>
          <button class="btn" type="button" (click)="resend()" [disabled]="resent()">
            {{ resent() ? 'Link sent again' : 'Send the link again' }}
          </button>
          <a class="btn btn--primary" routerLink="/login">Back to sign in</a>
        } @else {
          <h1>Create an account</h1>
          <p>New accounts start with the analyst role.</p>

          @if (error()) { <div class="banner banner--error" role="alert">{{ error() }}</div> }

          <form [formGroup]="form" (ngSubmit)="submit()">
            <label class="field">
              <span>Full name</span>
              <input type="text" formControlName="displayName" autocomplete="name" />
            </label>
            <label class="field">
              <span>Username</span>
              <input type="text" formControlName="username" autocomplete="username" autocapitalize="none" spellcheck="false" />
              <small>3–40 letters, digits, dots, dashes or underscores. Shown on your notes and decisions.</small>
            </label>
            <label class="field">
              <span>Email</span>
              <input type="email" formControlName="email" autocomplete="email" autocapitalize="none" spellcheck="false" />
              @if (form.controls.email.invalid && form.controls.email.touched) {
                <small class="error-text">Enter a valid email address.</small>
              } @else {
                <small>You can sign in with this or your username.</small>
              }
            </label>
            <label class="field">
              <span>Password</span>
              <input type="password" formControlName="password" autocomplete="new-password" />
              <small>At least 10 characters.</small>
            </label>
            <label class="field">
              <span>Confirm password</span>
              <input type="password" formControlName="confirm" autocomplete="new-password" />
              @if (form.hasError('mismatch') && form.controls.confirm.touched) {
                <small class="error-text">The passwords do not match.</small>
              }
            </label>
            <button class="btn btn--primary" type="submit" [disabled]="form.invalid || busy()">
              {{ busy() ? 'Creating account…' : 'Create account' }}
            </button>
          </form>
        }
      </div>
      <div class="card__foot">Already have an account? <a routerLink="/login">Sign in</a></div>
    </main>
  `,
  styleUrl: '../shared/auth-card.scss',
  styles: `
    .error-text { color: var(--risk-high) !important; }
    .next { color: var(--ink-soft); font-size: var(--step--1); }
  `,
})
export class SignupPage {
  private readonly auth = inject(AuthService);

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Email address the confirmation link went to, once the account is created. */
  protected readonly sentTo = signal<string | null>(null);
  protected readonly resent = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    displayName: ['', [Validators.required, Validators.maxLength(100)]],
    username: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{3,40}$/)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(72)]],
    confirm: ['', Validators.required],
  }, { validators: passwordsMatch });

  protected submit(): void {
    const { displayName, username, email, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth.signup({ displayName, username, email: email.trim(), password }).subscribe({
      next: user => {
        this.sentTo.set(user.email);
        this.busy.set(false);
      },
      error: e => { this.error.set(errorMessage(e)); this.busy.set(false); },
    });
  }

  protected resend(): void {
    const address = this.sentTo();
    if (address) {
      this.auth.resendVerification(address).subscribe({
        next: () => this.resent.set(true),
        error: e => this.error.set(errorMessage(e)),
      });
    }
  }
}
