import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-pager',
  template: `
    @if (total() > 0) {
      <div class="pager">
        <span>Showing {{ from() }} to {{ to() }} of {{ total() }}</span>
        <div class="actions">
          <button type="button" class="btn" [disabled]="page() === 0" (click)="pageChange.emit(page() - 1)">Previous</button>
          <button type="button" class="btn" [disabled]="page() + 1 >= totalPages()" (click)="pageChange.emit(page() + 1)">Next</button>
        </div>
      </div>
    }
  `,
})
export class Pager {
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly total = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();

  protected from(): number {
    return this.page() * this.size() + 1;
  }

  protected to(): number {
    return Math.min(this.total(), (this.page() + 1) * this.size());
  }
}
