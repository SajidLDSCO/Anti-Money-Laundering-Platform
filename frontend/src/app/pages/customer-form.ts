import { Component, effect, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/labels';
import { CustomerRequest, CustomerType, KycStatus } from '../core/models';

const COUNTRY = Validators.pattern(/^[A-Za-z]{2}$/);

@Component({
  selector: 'app-customer-form',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="page">
      <header class="page-head">
        <div>
          <a class="back" [routerLink]="id() ? ['/customers', id()] : '/customers'">Back</a>
          <h1>{{ id() ? 'Edit customer' : 'Onboard customer' }}</h1>
          <p>The name is screened against the sanctions list when you save, and the customer is given a risk rating.</p>
        </div>
      </header>

      @if (error()) { <div class="banner banner--error">{{ error() }}</div> }

      <section class="panel" style="max-width:760px">
        <form [formGroup]="form" (ngSubmit)="save()" class="form-grid">
          <label class="field span-2">
            <span>Full legal name</span>
            <input type="text" formControlName="fullName" autocomplete="off" />
          </label>
          <label class="field">
            <span>Customer type</span>
            <select formControlName="customerType">
              <option value="INDIVIDUAL">Individual</option>
              <option value="BUSINESS">Business</option>
            </select>
          </label>
          <label class="field">
            <span>{{ form.value.customerType === 'BUSINESS' ? 'Date of incorporation' : 'Date of birth' }}</span>
            <input type="date" formControlName="dateOfBirth" />
          </label>
          <label class="field">
            <span>Nationality</span>
            <input type="text" formControlName="nationality" maxlength="2" placeholder="e.g. BD" />
            <small>Two-letter ISO code</small>
          </label>
          <label class="field">
            <span>Country of residence</span>
            <input type="text" formControlName="countryOfResidence" maxlength="2" placeholder="e.g. BD" />
            <small>Two-letter ISO code</small>
          </label>
          <label class="field">
            <span>Occupation or industry</span>
            <input type="text" formControlName="occupation" />
          </label>
          <label class="field">
            <span>Email</span>
            <input type="email" formControlName="email" />
          </label>
          <label class="field">
            <span>KYC status</span>
            <select formControlName="kycStatus">
              <option value="PENDING">Pending</option>
              <option value="VERIFIED">Verified</option>
              <option value="REJECTED">Rejected</option>
            </select>
          </label>
          <label class="field field--check" style="align-self:end">
            <input type="checkbox" formControlName="pep" />
            <span>Politically exposed person</span>
          </label>
          <div class="span-2 actions">
            <button class="btn btn--primary" type="submit" [disabled]="form.invalid || busy()">{{ id() ? 'Save changes' : 'Onboard customer' }}</button>
          </div>
        </form>
      </section>
    </div>
  `,
})
export class CustomerFormPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly id = input<string | undefined>();

  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(200)]],
    customerType: ['INDIVIDUAL' as CustomerType],
    dateOfBirth: [''],
    nationality: ['', COUNTRY],
    countryOfResidence: ['', COUNTRY],
    occupation: [''],
    email: ['', Validators.email],
    pep: [false],
    kycStatus: ['PENDING' as KycStatus],
  });

  constructor() {
    effect(() => {
      const id = this.id();
      if (!id) {
        return;
      }
      this.api.customer(Number(id)).subscribe({
        next: d => {
          const c = d.customer;
          this.form.setValue({
            fullName: c.fullName, customerType: c.customerType, dateOfBirth: c.dateOfBirth ?? '',
            nationality: c.nationality ?? '', countryOfResidence: c.countryOfResidence ?? '',
            occupation: c.occupation ?? '', email: c.email ?? '', pep: c.pep, kycStatus: c.kycStatus,
          });
        },
        error: e => this.error.set(errorMessage(e)),
      });
    });
  }

  protected save(): void {
    const v = this.form.getRawValue();
    const body: CustomerRequest = {
      ...v,
      dateOfBirth: v.dateOfBirth || null,
      nationality: v.nationality ? v.nationality.toUpperCase() : null,
      countryOfResidence: v.countryOfResidence ? v.countryOfResidence.toUpperCase() : null,
      occupation: v.occupation || null,
      email: v.email || null,
    };
    this.busy.set(true);
    this.error.set(null);
    const id = this.id();
    const call = id ? this.api.updateCustomer(Number(id), body) : this.api.createCustomer(body);
    call.subscribe({
      next: c => this.router.navigate(['/customers', c.id]),
      error: e => { this.error.set(errorMessage(e)); this.busy.set(false); },
    });
  }
}
