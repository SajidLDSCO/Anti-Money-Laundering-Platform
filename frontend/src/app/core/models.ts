// Mirrors the DTOs in com.example.aml.api.dto

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';
export type CustomerType = 'INDIVIDUAL' | 'BUSINESS';
export type KycStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';
export type TransactionType =
  | 'CASH_DEPOSIT' | 'CASH_WITHDRAWAL' | 'WIRE_IN' | 'WIRE_OUT'
  | 'TRANSFER_IN' | 'TRANSFER_OUT' | 'CARD_PAYMENT';
export type TransactionStatus = 'CLEARED' | 'FLAGGED' | 'BLOCKED';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type AlertStatus = 'OPEN' | 'IN_REVIEW' | 'ESCALATED' | 'CLOSED_FALSE_POSITIVE' | 'CLOSED_SAR_FILED';
export type AlertType = 'TRANSACTION' | 'CUSTOMER_SCREENING';

export const TRANSACTION_TYPES: TransactionType[] = [
  'CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'WIRE_IN', 'WIRE_OUT', 'TRANSFER_IN', 'TRANSFER_OUT', 'CARD_PAYMENT',
];
export const OPEN_ALERT_STATUSES: AlertStatus[] = ['OPEN', 'IN_REVIEW', 'ESCALATED'];

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Customer {
  id: number;
  customerNumber: string;
  fullName: string;
  customerType: CustomerType;
  dateOfBirth: string | null;
  nationality: string | null;
  countryOfResidence: string | null;
  occupation: string | null;
  email: string | null;
  pep: boolean;
  kycStatus: KycStatus;
  riskScore: number;
  riskLevel: RiskLevel;
  riskFactors: string | null;
  createdAt: string;
}

export interface CustomerRequest {
  fullName: string;
  customerType: CustomerType;
  dateOfBirth: string | null;
  nationality: string | null;
  countryOfResidence: string | null;
  occupation: string | null;
  email: string | null;
  pep: boolean;
  kycStatus: KycStatus;
}

export interface Account {
  id: number;
  accountNumber: string;
  customerId: number;
  customerName: string;
  accountType: string;
  currency: string;
  balance: number;
  status: AccountStatus;
  openedAt: string;
}

export interface CustomerDetail {
  customer: Customer;
  accounts: Account[];
  recentTransactions: Transaction[];
  alerts: AlertSummary[];
}

export interface Transaction {
  id: number;
  reference: string;
  accountId: number;
  accountNumber: string;
  customerId: number;
  customerName: string;
  type: TransactionType;
  amount: number;
  currency: string;
  counterpartyName: string | null;
  counterpartyAccount: string | null;
  counterpartyCountry: string | null;
  channel: string | null;
  description: string | null;
  timestamp: string;
  riskScore: number;
  status: TransactionStatus;
}

export interface TransactionRequest {
  accountId: number;
  type: TransactionType;
  amount: number;
  currency?: string | null;
  counterpartyName?: string | null;
  counterpartyAccount?: string | null;
  counterpartyCountry?: string | null;
  channel?: string | null;
  description?: string | null;
}

export interface RuleHit {
  ruleCode: string;
  ruleName: string;
  score: number;
  reason: string;
}

export interface EvaluationResult {
  transaction: Transaction;
  score: number;
  decision: TransactionStatus;
  hits: RuleHit[];
  alertId: number | null;
  alertNumber: string | null;
}

export interface AlertSummary {
  id: number;
  alertNumber: string;
  type: AlertType;
  customerId: number;
  customerName: string;
  transactionId: number | null;
  transactionReference: string | null;
  amount: number | null;
  currency: string | null;
  score: number;
  severity: Severity;
  status: AlertStatus;
  assignedTo: string | null;
  summary: string;
  ruleCodes: string[];
  createdAt: string;
  updatedAt: string;
}

export interface Note {
  id: number;
  author: string;
  text: string;
  systemEntry: boolean;
  createdAt: string;
}

export interface Sar {
  id: number;
  sarNumber: string;
  alertId: number;
  alertNumber: string;
  customerId: number;
  customerName: string;
  narrative: string;
  filedBy: string;
  filedAt: string;
}

export interface AlertDetail {
  alert: AlertSummary;
  hits: RuleHit[];
  transaction: Transaction | null;
  customer: Customer;
  notes: Note[];
  sar: Sar | null;
}

export interface SanctionsEntry {
  id: number | null;
  name: string;
  aliases: string | null;
  entityType: string | null;
  country: string | null;
  listSource: string | null;
  program: string | null;
}

export interface ScreeningMatch {
  entryId: number;
  listedName: string;
  matchedOn: string;
  score: number;
  entityType: string | null;
  country: string | null;
  listSource: string | null;
  program: string | null;
}

export interface ScreeningResult {
  query: string;
  threshold: number;
  hit: boolean;
  matches: ScreeningMatch[];
}

export interface ImportResult {
  imported: number;
  skipped: number;
  errors: string[];
}

export interface Rule {
  code: string;
  name: string;
  category: string;
  description: string;
  parameters: string;
  baseScore: number;
  enabled: boolean;
}

export interface DailyPoint {
  date: string;
  total: number;
  flagged: number;
  blocked: number;
}

export interface Dashboard {
  totalTransactions: number;
  flaggedTransactions: number;
  blockedTransactions: number;
  volumeLast30Days: number;
  openAlerts: number;
  criticalOpenAlerts: number;
  highRiskCustomers: number;
  sarsFiled: number;
  openAlertsBySeverity: Record<Severity, number>;
  alertsByRule: Record<string, number>;
  customersByRisk: Record<RiskLevel, number>;
  daily: DailyPoint[];
  recentAlerts: AlertSummary[];
}

export type Role = 'ANALYST' | 'MLRO' | 'ADMIN';

export interface User {
  id: number;
  username: string;
  displayName: string;
  email: string | null;
  emailVerified: boolean;
  role: Role;
  enabled: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface SignupRequest {
  username: string;
  displayName: string;
  email: string;
  password: string;
}
