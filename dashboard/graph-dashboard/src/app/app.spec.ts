import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { App } from './app';

// Cytoscape renders to a canvas, which jsdom doesn't implement — mock it here
// the same way fraud_pipeline's case-dashboard mocks Chart.js for the same reason.
vi.mock('cytoscape', () => ({
  default: vi.fn(() => ({})),
}));

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render title', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('fraud-graph');
  });
});
