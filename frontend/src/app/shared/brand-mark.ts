import { Component } from '@angular/core';

/** The shield logo, white on the dark bar. */
@Component({
  selector: 'app-brand-mark',
  template: `
    <svg viewBox="0 0 24 24" width="28" height="28" aria-hidden="true">
      <path d="M12 2 3 6v6c0 5 3.8 9.3 9 10 5.2-.7 9-5 9-10V6l-9-4Z" fill="none" stroke="currentColor" stroke-width="1.8"/>
      <path d="M7.5 12.5h3l1.5-3 2 6 1.5-3h1" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>
  `,
  styles: ':host { display: inline-flex; }',
})
export class BrandMark {}
