import { HttpErrorResponse } from '@angular/common/http';
import { Pipe, PipeTransform } from '@angular/core';

const LABELS: Record<string, string> = {
  CASH_DEPOSIT: 'Cash deposit',
  CASH_WITHDRAWAL: 'Cash withdrawal',
  WIRE_IN: 'Incoming wire',
  WIRE_OUT: 'Outgoing wire',
  TRANSFER_IN: 'Incoming transfer',
  TRANSFER_OUT: 'Outgoing transfer',
  CARD_PAYMENT: 'Card payment',
  IN_REVIEW: 'In review',
  CLOSED_FALSE_POSITIVE: 'Closed – false positive',
  CLOSED_SAR_FILED: 'Closed – SAR filed',
  CUSTOMER_SCREENING: 'Name screening',
  // Detection rule codes (match AmlRule.name() on the backend)
  LARGE_CASH: 'Large cash transaction',
  STRUCTURING: 'Possible structuring',
  HIGH_VELOCITY: 'High transaction velocity',
  HIGH_RISK_JURISDICTION: 'High-risk jurisdiction',
  SANCTIONS_MATCH: 'Sanctions list match',
  PEP_TRANSACTION: 'PEP transaction',
  RAPID_MOVEMENT: 'Rapid movement of funds',
  ROUND_AMOUNT: 'Large round amount',
  UNUSUAL_AMOUNT: 'Out of profile amount',
  HIGH_RISK_CUSTOMER: 'High-risk customer',
  CUSTOMER_SANCTIONS_SCREENING: 'Onboarding name screening',
};

/** Turns API enum values into readable labels: HIGH_RISK_JURISDICTION -> High risk jurisdiction. */
export function label(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  if (LABELS[value]) {
    return LABELS[value];
  }
  const s = value.toLowerCase().replace(/_/g, ' ');
  return s.charAt(0).toUpperCase() + s.slice(1);
}

@Pipe({ name: 'label' })
export class LabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return label(value);
  }
}

/** Extracts the server's problem-detail message, or a plain fallback. */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'Cannot reach the API. Check that the backend is running on port 8080.';
    }
    const body = err.error as { detail?: string; errors?: unknown } | null;
    if (body?.detail) {
      return body.detail;
    }
    return `Request failed (${err.status}).`;
  }
  return 'Something went wrong.';
}
