import { AfterViewInit, Component, ElementRef, viewChild } from '@angular/core';
import cytoscape from 'cytoscape';

// Phase 4 (MVP) will replace this hardcoded elements array with the ranked
// cluster/community data returned by the ring-detector API (community_id from
// Louvain as node color, pagerank_score as node size). This component's only
// job right now is proving the Cytoscape.js <-> Angular wiring works.
@Component({
  selector: 'app-cluster-graph',
  imports: [],
  templateUrl: './cluster-graph.html',
  styleUrl: './cluster-graph.scss',
})
export class ClusterGraph implements AfterViewInit {
  private readonly container = viewChild.required<ElementRef<HTMLDivElement>>('cyContainer');

  ngAfterViewInit(): void {
    cytoscape({
      container: this.container().nativeElement,
      elements: [
        { data: { id: 'acct_0401', label: 'Cash-Out Account' } },
        { data: { id: 'acct_0402', label: 'Mule 1' } },
        { data: { id: 'acct_0403', label: 'Mule 2' } },
        { data: { id: 'acct_0404', label: 'Mule 3' } },
        { data: { id: 'e1', source: 'acct_0402', target: 'acct_0401' } },
        { data: { id: 'e2', source: 'acct_0403', target: 'acct_0401' } },
        { data: { id: 'e3', source: 'acct_0404', target: 'acct_0401' } },
      ],
      style: [
        {
          selector: 'node',
          style: {
            label: 'data(label)',
            'background-color': '#3B82F6',
            'font-size': 10,
          },
        },
        {
          selector: 'edge',
          style: {
            'target-arrow-shape': 'triangle',
            'curve-style': 'bezier',
            width: 2,
            'line-color': '#94A3B8',
            'target-arrow-color': '#94A3B8',
          },
        },
      ],
      layout: { name: 'cose' },
    });
  }
}
