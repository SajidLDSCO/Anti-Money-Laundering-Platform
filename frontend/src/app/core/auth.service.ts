import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, firstValueFrom, map, of, tap, timeout } from 'rxjs';
import { Role, SignupRequest, User } from './models';

/** Each role includes the rights of the ones before it (matches the backend's SecurityConfig). */
const RANK: Record<Role, number> = { ANALYST: 0, MLRO: 1, ADMIN: 2 };

/**
 * The signed-in user. The session lives in an HttpOnly cookie set by the API; the
 * XSRF-TOKEN cookie it also sets is sent back by HttpClient on every mutating request.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly user = signal<User | null>(null);
  readonly signedIn = computed(() => this.user() !== null);

  /**
   * Called once at startup. Also fetches the first CSRF cookie, which sign-in needs.
   * Gives up after a few seconds so an unreachable API shows the sign-in page, not a blank screen.
   */
  load(): Promise<void> {
    return firstValueFrom(this.http.get<User>('/api/auth/me').pipe(
      timeout(5000),
      tap(u => this.user.set(u)),
      map(() => undefined),
      catchError(() => { this.user.set(null); return of(undefined); }),
    ));
  }

  login(username: string, password: string): Observable<User> {
    const body = new HttpParams().set('username', username.trim()).set('password', password);
    return this.http.post<User>('/api/auth/login', body).pipe(tap(u => this.user.set(u)));
  }

  signup(req: SignupRequest): Observable<User> {
    return this.http.post<User>('/api/auth/signup', req);
  }

  /** Confirms the email address with the token from the link in the confirmation email. */
  verifyEmail(token: string): Observable<User> {
    return this.http.post<User>('/api/auth/verify-email', { token });
  }

  /** Asks for a new confirmation link. The API answers the same whether or not the account exists. */
  resendVerification(usernameOrEmail: string): Observable<void> {
    return this.http.post<void>('/api/auth/resend-verification', { usernameOrEmail: usernameOrEmail.trim() });
  }

  logout(): Observable<void> {
    // Sign out locally even if the server call fails (e.g. the session had already expired).
    return this.http.post<void>('/api/auth/logout', null).pipe(
      catchError(() => of(undefined)),
      tap(() => this.user.set(null)),
    );
  }

  /** Forget the user locally, e.g. when the server says the session has expired. */
  clear(): void {
    this.user.set(null);
  }

  /** True if the signed-in user has this role or a higher one. */
  can(role: Role): boolean {
    const u = this.user();
    return !!u && RANK[u.role] >= RANK[role];
  }
}
