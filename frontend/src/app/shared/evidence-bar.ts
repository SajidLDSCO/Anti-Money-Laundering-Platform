import { Component, computed, input } from '@angular/core';
import { RuleHit } from '../core/models';

interface Segment {
  hit: RuleHit;
  start: number;
  width: number;
  shade: number;
}

/**
 * The risk score as a 0-100 scale, built from one segment per rule that fired.
 * The flag and block thresholds are marked, so an analyst sees at a glance which
 * findings pushed the transaction over which line.
 */
@Component({
  selector: 'app-evidence-bar',
  template: `
    <figure class="evidence">
      <div class="evidence__head">
        <span class="evidence__score">{{ score() }}</span>
        <span class="evidence__of">/ 100 risk score</span>
      </div>
      <div class="evidence__track" role="img" [attr.aria-label]="ariaLabel()">
        @for (s of segments(); track s.hit.ruleCode) {
          <span class="evidence__seg shade-{{ s.shade }}" [class.is-sanctions]="isSanctions(s.hit)"
                [style.left.%]="s.start" [style.width.%]="s.width"
                [title]="s.hit.ruleName + ' +' + s.hit.score"></span>
        }
        <span class="evidence__mark" [style.left.%]="flagAt()"><span>Flag {{ flagAt() }}</span></span>
        <span class="evidence__mark evidence__mark--block" [style.left.%]="blockAt()"><span>Block {{ blockAt() }}</span></span>
      </div>
      @if (hits().length) {
        <figcaption>
          <ul class="evidence__legend">
            @for (s of segments(); track s.hit.ruleCode) {
              <li>
                <span class="evidence__swatch shade-{{ s.shade }}" [class.is-sanctions]="isSanctions(s.hit)"></span>
                <span class="evidence__rule">{{ s.hit.ruleName }}</span>
                <span class="evidence__pts">+{{ s.hit.score }}</span>
                <p>{{ s.hit.reason }}</p>
              </li>
            }
          </ul>
          @if (overflow() > 0) {
            <p class="evidence__note">Findings add up to {{ rawTotal() }}; the score is capped at 100.</p>
          }
        </figcaption>
      } @else {
        <figcaption class="evidence__note">No rule fired.</figcaption>
      }
    </figure>
  `,
})
export class EvidenceBar {
  readonly hits = input.required<RuleHit[]>();
  readonly score = input.required<number>();
  readonly flagAt = input(40);
  readonly blockAt = input(80);

  /** Sanctions findings always hold the transaction, so they get the strongest colour. */
  protected isSanctions(h: RuleHit): boolean {
    return h.ruleCode === 'SANCTIONS_MATCH' || h.ruleCode === 'CUSTOMER_SANCTIONS_SCREENING';
  }

  protected readonly rawTotal = computed(() => this.hits().reduce((sum, h) => sum + h.score, 0));
  protected readonly overflow = computed(() => this.rawTotal() - 100);

  protected readonly segments = computed<Segment[]>(() => {
    let cursor = 0;
    return this.hits().map((hit, i) => {
      const width = Math.max(0, Math.min(hit.score, 100 - cursor));
      const seg = { hit, start: cursor, width, shade: i % 6 };
      cursor += width;
      return seg;
    });
  });

  protected readonly ariaLabel = computed(() =>
    `Risk score ${this.score()} of 100. ` +
    this.hits().map(h => `${h.ruleName} plus ${h.score}`).join(', '));
}
