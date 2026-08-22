import { AfterViewInit, Component, ElementRef, effect, input, viewChild } from '@angular/core';
import cytoscape, { Core, ElementDefinition } from 'cytoscape';
import { ClusterEdge, ClusterNode } from '../ring-detector-api';

@Component({
  selector: 'app-cluster-graph',
  imports: [],
  templateUrl: './cluster-graph.html',
  styleUrl: './cluster-graph.scss',
})
export class ClusterGraph implements AfterViewInit {
  private readonly container = viewChild.required<ElementRef<HTMLDivElement>>('cyContainer');

  readonly nodes = input<ClusterNode[]>([]);
  readonly edges = input<ClusterEdge[]>([]);

  private cy: Core | undefined;
  private viewReady = false;

  constructor() {
    // Re-renders whenever the selected cluster's data changes; destroy/recreate
    // is simple and fine at this graph size (rings top out around ~30 nodes).
    effect(() => {
      const nodes = this.nodes();
      const edges = this.edges();
      if (this.viewReady) {
        this.render(nodes, edges);
      }
    });
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.render(this.nodes(), this.edges());
  }

  private render(nodes: ClusterNode[], edges: ClusterEdge[]): void {
    this.cy?.destroy();

    const elements: ElementDefinition[] = [
      ...nodes.map((n) => ({
        data: {
          id: n.id,
          label: n.type === 'Device' ? 'Device' : (n.label ?? n.id),
          type: n.type,
          pagerank: n.pagerank,
        },
      })),
      ...edges.map((e, i) => ({
        data: {
          id: `e${i}`,
          source: e.source,
          target: e.target,
          type: e.relationshipType,
        },
      })),
    ];

    this.cy = cytoscape({
      container: this.container().nativeElement,
      elements,
      style: [
        {
          selector: 'node[type = "Account"]',
          style: {
            label: 'data(label)',
            'background-color': '#3B82F6',
            'font-size': 10,
            width: 'mapData(pagerank, 0, 8, 20, 60)',
            height: 'mapData(pagerank, 0, 8, 20, 60)',
          },
        },
        {
          selector: 'node[type = "Device"]',
          style: {
            label: 'data(label)',
            'background-color': '#F59E0B',
            shape: 'diamond',
            'font-size': 10,
          },
        },
        {
          selector: 'edge[type = "TRANSFER"]',
          style: {
            'target-arrow-shape': 'triangle',
            'curve-style': 'bezier',
            width: 2,
            'line-color': '#94A3B8',
            'target-arrow-color': '#94A3B8',
          },
        },
        {
          selector: 'edge[type = "USED"]',
          style: {
            'curve-style': 'bezier',
            width: 1,
            'line-color': '#FCD34D',
            'line-style': 'dashed',
            'target-arrow-shape': 'none',
          },
        },
      ],
      layout: { name: 'cose' },
    });
  }
}
