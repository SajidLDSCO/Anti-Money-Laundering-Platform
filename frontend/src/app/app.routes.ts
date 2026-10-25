import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth.guard';
import { Shell } from './layout/shell';

export const routes: Routes = [
  { path: 'login', title: 'Sign in – AML Monitor', canActivate: [guestGuard], loadComponent: () => import('./pages/login').then(m => m.LoginPage) },
  { path: 'signup', title: 'Create account – AML Monitor', canActivate: [guestGuard], loadComponent: () => import('./pages/signup').then(m => m.SignupPage) },
  { path: 'verify-email', title: 'Confirm email – AML Monitor', loadComponent: () => import('./pages/verify-email').then(m => m.VerifyEmailPage) },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', title: 'Overview – AML Monitor', loadComponent: () => import('./pages/dashboard').then(m => m.DashboardPage) },
      { path: 'alerts', title: 'Alerts – AML Monitor', loadComponent: () => import('./pages/alerts-list').then(m => m.AlertsListPage) },
      { path: 'alerts/:id', title: 'Alert – AML Monitor', loadComponent: () => import('./pages/alert-detail').then(m => m.AlertDetailPage) },
      { path: 'transactions', title: 'Transactions – AML Monitor', loadComponent: () => import('./pages/transactions-list').then(m => m.TransactionsListPage) },
      { path: 'transactions/new', title: 'Submit transaction – AML Monitor', loadComponent: () => import('./pages/transaction-new').then(m => m.TransactionNewPage) },
      { path: 'customers', title: 'Customers – AML Monitor', loadComponent: () => import('./pages/customers-list').then(m => m.CustomersListPage) },
      { path: 'customers/new', title: 'Onboard customer – AML Monitor', loadComponent: () => import('./pages/customer-form').then(m => m.CustomerFormPage) },
      { path: 'customers/:id/edit', title: 'Edit customer – AML Monitor', loadComponent: () => import('./pages/customer-form').then(m => m.CustomerFormPage) },
      { path: 'customers/:id', title: 'Customer – AML Monitor', loadComponent: () => import('./pages/customer-detail').then(m => m.CustomerDetailPage) },
      { path: 'screening', title: 'Screening – AML Monitor', loadComponent: () => import('./pages/screening').then(m => m.ScreeningPage) },
      { path: 'rules', title: 'Rules – AML Monitor', loadComponent: () => import('./pages/rules').then(m => m.RulesPage) },
      { path: 'sars', title: 'SARs – AML Monitor', loadComponent: () => import('./pages/sars').then(m => m.SarsPage) },
      { path: 'users', title: 'Users – AML Monitor', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./pages/users').then(m => m.UsersPage) },
    ],
  },
  { path: '**', redirectTo: '' },
];
