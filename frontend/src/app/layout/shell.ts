import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { LabelPipe } from '../core/labels';
import { Role } from '../core/models';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  exact: boolean;
  role?: Role;
}

/** Navbar, sidebar and content area around every signed-in page. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, DatePipe, LabelPipe],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Desktop: icon-only rail. */
  protected readonly collapsed = signal(false);
  /** Mobile: sidebar drawn over the content. */
  protected readonly mobileOpen = signal(false);
  protected readonly navExpanded = computed(() => !this.collapsed() || this.mobileOpen());
  protected readonly today = new Date();

  protected readonly initials = computed(() =>
    (this.auth.user()?.displayName ?? '').split(/\s+/).filter(Boolean).slice(0, 2)
      .map(w => w[0].toUpperCase()).join('') || '?');

  private readonly allNav: NavItem[] = [
    { path: '/', label: 'Overview', icon: 'overview', exact: true },
    { path: '/alerts', label: 'Alerts', icon: 'alerts', exact: false },
    { path: '/transactions', label: 'Transactions', icon: 'transactions', exact: false },
    { path: '/customers', label: 'Customers', icon: 'customers', exact: false },
    { path: '/screening', label: 'Screening', icon: 'screening', exact: false },
    { path: '/sars', label: 'SARs filed', icon: 'sars', exact: false },
    { path: '/rules', label: 'Detection rules', icon: 'rules', exact: false },
    { path: '/users', label: 'Users', icon: 'users', exact: false, role: 'ADMIN' },
  ];

  protected readonly nav = computed(() => {
    this.auth.user();
    return this.allNav.filter(i => !i.role || this.auth.can(i.role));
  });

  protected toggleNav(): void {
    if (window.matchMedia('(max-width: 767px)').matches) {
      this.mobileOpen.set(!this.mobileOpen());
    } else {
      this.collapsed.set(!this.collapsed());
    }
  }

  protected logout(): void {
    this.auth.logout().subscribe(() => this.router.navigate(['/login']));
  }
}
