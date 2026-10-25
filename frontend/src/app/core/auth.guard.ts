import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';

/** Pages behind sign-in. Remembers where the user was going. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.signedIn() || inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Sign-in and sign-up: a signed-in user goes straight to the app. */
export const guestGuard: CanActivateFn = () =>
  !inject(AuthService).signedIn() || inject(Router).createUrlTree(['/']);

/** Pages for a minimum role. The API enforces the same rule; this only hides what would fail. */
export function roleGuard(role: Role): CanActivateFn {
  return () => inject(AuthService).can(role) || inject(Router).createUrlTree(['/']);
}
