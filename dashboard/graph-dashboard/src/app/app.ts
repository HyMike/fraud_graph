import { Component, signal } from '@angular/core';
import { ClusterGraph } from './cluster-graph/cluster-graph';

@Component({
  selector: 'app-root',
  imports: [ClusterGraph],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('graph-dashboard');
}
