#!/usr/bin/env python3
"""
Generate an interactive HTML visualization of class predicates analysis.
Reads class_predicates_analysis.json and creates an interactive network graph.
"""

import json
import sys
from pathlib import Path
from datetime import datetime


def load_analysis_data(json_path):
    """Load the class predicates analysis JSON file."""
    with open(json_path, 'r') as f:
        return json.load(f)


def generate_html(data, output_path):
    """Generate the interactive HTML page."""

    # Embed the data as JavaScript
    data_json = json.dumps(data, indent=2)

    html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Class Predicates Analysis - Interactive Visualization</title>

    <!-- Bootstrap CSS -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">

    <!-- Custom CSS -->
    <style>
        body {{
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            overflow: hidden;
        }}

        #app {{
            display: flex;
            height: 100vh;
        }}

        #sidebar {{
            width: 300px;
            background: #f8f9fa;
            border-right: 1px solid #dee2e6;
            overflow-y: auto;
            padding: 15px;
        }}

        #main {{
            flex: 1;
            display: flex;
            flex-direction: column;
        }}

        #graph {{
            flex: 1;
            background: #ffffff;
            position: relative;
        }}

        #details {{
            width: 350px;
            background: #f8f9fa;
            border-left: 1px solid #dee2e6;
            overflow-y: auto;
            padding: 15px;
        }}

        .stat-card {{
            background: white;
            border-radius: 8px;
            padding: 12px;
            margin-bottom: 12px;
            border: 1px solid #dee2e6;
        }}

        .stat-label {{
            font-size: 0.85rem;
            color: #6c757d;
            margin-bottom: 4px;
        }}

        .stat-value {{
            font-size: 1.5rem;
            font-weight: 600;
            color: #212529;
        }}

        .filter-section {{
            margin-bottom: 20px;
        }}

        .filter-section h6 {{
            font-size: 0.9rem;
            font-weight: 600;
            margin-bottom: 10px;
            color: #495057;
        }}

        .node {{
            cursor: pointer;
            stroke: #fff;
            stroke-width: 2px;
        }}

        .node:hover {{
            stroke: #007bff;
            stroke-width: 3px;
        }}

        .node.selected {{
            stroke: #dc3545;
            stroke-width: 4px;
        }}

        .link {{
            stroke: #999;
            stroke-opacity: 0.4;
            fill: none;
        }}

        .link.highlighted {{
            stroke: #007bff;
            stroke-opacity: 0.8;
            stroke-width: 2px;
        }}

        .node-label {{
            font-size: 10px;
            pointer-events: none;
            text-anchor: middle;
            fill: #333;
        }}

        .predicate-table {{
            font-size: 0.85rem;
        }}

        .predicate-table th {{
            background: #e9ecef;
            font-weight: 600;
            font-size: 0.8rem;
        }}

        .predicate-row {{
            cursor: pointer;
        }}

        .predicate-row:hover {{
            background: #e9ecef;
        }}

        .reference-list {{
            max-height: 200px;
            overflow-y: auto;
            font-size: 0.85rem;
        }}

        .reference-item {{
            cursor: pointer;
            padding: 4px 8px;
            border-radius: 4px;
            margin-bottom: 4px;
            background: white;
            border: 1px solid #dee2e6;
        }}

        .reference-item:hover {{
            background: #e9ecef;
        }}

        .badge {{
            font-size: 0.75rem;
        }}

        .legend {{
            position: absolute;
            top: 10px;
            right: 10px;
            background: white;
            padding: 10px;
            border-radius: 8px;
            border: 1px solid #dee2e6;
            font-size: 0.85rem;
        }}

        .legend-item {{
            margin-bottom: 5px;
            display: flex;
            align-items: center;
        }}

        .legend-color {{
            width: 20px;
            height: 20px;
            border-radius: 50%;
            margin-right: 8px;
            border: 2px solid white;
        }}

        #zoom-controls {{
            position: absolute;
            bottom: 20px;
            right: 20px;
            background: white;
            padding: 10px;
            border-radius: 8px;
            border: 1px solid #dee2e6;
        }}

        .zoom-btn {{
            display: block;
            width: 40px;
            height: 40px;
            margin-bottom: 5px;
            border: 1px solid #dee2e6;
            background: white;
            border-radius: 4px;
            cursor: pointer;
            font-size: 1.2rem;
        }}

        .zoom-btn:hover {{
            background: #e9ecef;
        }}

        .namespace-checkbox {{
            margin-bottom: 5px;
        }}

        .top-classes-list {{
            font-size: 0.85rem;
        }}

        .top-class-item {{
            padding: 6px;
            margin-bottom: 4px;
            background: white;
            border-radius: 4px;
            border: 1px solid #dee2e6;
            cursor: pointer;
        }}

        .top-class-item:hover {{
            background: #e9ecef;
        }}

        .scrollable {{
            max-height: 300px;
            overflow-y: auto;
        }}
    </style>
</head>
<body>
    <div id="app">
        <!-- Left Sidebar -->
        <div id="sidebar">
            <h5 class="mb-3">Class Analysis Viewer</h5>

            <!-- Search -->
            <div class="filter-section">
                <h6>Search</h6>
                <input type="text" id="search-input" class="form-control form-control-sm"
                       placeholder="Search classes or predicates...">
            </div>

            <!-- Statistics -->
            <div class="filter-section">
                <h6>Statistics</h6>
                <div class="stat-card">
                    <div class="stat-label">Total Classes</div>
                    <div class="stat-value" id="total-classes">0</div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">Visible Classes</div>
                    <div class="stat-value" id="visible-classes">0</div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">Total Instances</div>
                    <div class="stat-value" id="total-instances">0</div>
                </div>
            </div>

            <!-- Filters -->
            <div class="filter-section">
                <h6>Filters</h6>
                <label class="form-label" style="font-size: 0.85rem;">
                    Min Instance Count: <span id="instance-count-value">0</span>
                </label>
                <input type="range" class="form-range" id="instance-count-filter"
                       min="0" max="100000" value="0" step="100">

                <label class="form-label mt-2" style="font-size: 0.85rem;">
                    Min Coverage %: <span id="coverage-value">0</span>
                </label>
                <input type="range" class="form-range" id="coverage-filter"
                       min="0" max="100" value="0" step="5">
            </div>

            <!-- Namespaces -->
            <div class="filter-section">
                <h6>Namespaces</h6>
                <div id="namespace-filters" class="scrollable"></div>
            </div>

            <!-- Top Classes -->
            <div class="filter-section">
                <h6>Top Classes</h6>
                <div id="top-classes" class="scrollable"></div>
            </div>
        </div>

        <!-- Main Graph Area -->
        <div id="main">
            <div id="graph">
                <svg id="graph-svg"></svg>

                <!-- Legend -->
                <div class="legend">
                    <strong>Node Size</strong>
                    <div style="font-size: 0.8rem; color: #6c757d;">
                        Proportional to instance count
                    </div>
                    <div style="margin-top: 8px;">
                        <strong>Edge Thickness</strong>
                        <div style="font-size: 0.8rem; color: #6c757d;">
                            Proportional to reference count
                        </div>
                    </div>
                </div>

                <!-- Zoom Controls -->
                <div id="zoom-controls">
                    <button class="zoom-btn" id="zoom-in">+</button>
                    <button class="zoom-btn" id="zoom-out">−</button>
                    <button class="zoom-btn" id="zoom-reset" style="font-size: 0.9rem;">⟲</button>
                </div>
            </div>
        </div>

        <!-- Right Details Panel -->
        <div id="details">
            <div id="details-content">
                <div class="text-center text-muted" style="padding-top: 50px;">
                    <p>Click on a class node to view details</p>
                </div>
            </div>
        </div>
    </div>

    <!-- D3.js -->
    <script src="https://d3js.org/d3.v7.min.js"></script>

    <!-- Application Data -->
    <script>
        const analysisData = {data_json};
    </script>

    <!-- Application Code -->
    <script>
        // Global state
        let simulation;
        let nodes = [];
        let links = [];
        let filteredNodes = [];
        let filteredLinks = [];
        let selectedNode = null;
        let namespaceColors = {{}};
        let namespaceFilter = new Set();

        // Initialize the application
        function init() {{
            processData();
            setupFilters();
            updateStatistics();
            renderGraph();
        }}

        // Process data into nodes and links
        function processData() {{
            const classes = analysisData.classes || [];

            // Create nodes from classes
            nodes = classes.map(cls => ({{
                id: cls.class_name,
                uri: cls.class_uri,
                name: cls.class_name,
                instanceCount: cls.instance_count,
                predicates: cls.predicates || [],
                referencedBy: cls.referenced_by || [],
                referencesTo: cls.references_to || [],
                namespace: cls.class_name.split(':')[0]
            }}));

            // Extract namespaces and assign colors
            const namespaces = [...new Set(nodes.map(n => n.namespace))];
            const colorScale = d3.scaleOrdinal(d3.schemeCategory10);
            namespaces.forEach((ns, i) => {{
                namespaceColors[ns] = colorScale(i);
                namespaceFilter.add(ns);
            }});

            // Create links from references
            links = [];
            nodes.forEach(node => {{
                const classData = classes.find(c => c.class_name === node.id);
                if (classData && classData.references_to) {{
                    classData.references_to.forEach(ref => {{
                        const target = nodes.find(n => n.name === ref.class_name);
                        if (target) {{
                            links.push({{
                                source: node.id,
                                target: target.id,
                                count: ref.reference_count,
                                predicates: ref.predicates || []
                            }});
                        }}
                    }});
                }}
            }});

            filteredNodes = [...nodes];
            filteredLinks = [...links];
        }}

        // Setup filter controls
        function setupFilters() {{
            // Search input
            document.getElementById('search-input').addEventListener('input', applyFilters);

            // Instance count filter
            const instanceFilter = document.getElementById('instance-count-filter');
            const maxInstances = Math.max(...nodes.map(n => n.instanceCount));
            instanceFilter.max = maxInstances;
            instanceFilter.addEventListener('input', (e) => {{
                document.getElementById('instance-count-value').textContent =
                    parseInt(e.target.value).toLocaleString();
                applyFilters();
            }});

            // Coverage filter
            document.getElementById('coverage-filter').addEventListener('input', (e) => {{
                document.getElementById('coverage-value').textContent = e.target.value;
                applyFilters();
            }});

            // Namespace filters
            const namespaceDiv = document.getElementById('namespace-filters');
            Object.keys(namespaceColors).sort().forEach(ns => {{
                const div = document.createElement('div');
                div.className = 'namespace-checkbox form-check';
                div.innerHTML = `
                    <input class="form-check-input" type="checkbox" checked
                           id="ns-${{ns}}" value="${{ns}}">
                    <label class="form-check-label" for="ns-${{ns}}" style="font-size: 0.85rem;">
                        <span style="display: inline-block; width: 12px; height: 12px;
                                     background: ${{namespaceColors[ns]}}; border-radius: 50%;
                                     margin-right: 6px;"></span>
                        ${{ns}}
                    </label>
                `;
                namespaceDiv.appendChild(div);

                div.querySelector('input').addEventListener('change', (e) => {{
                    if (e.target.checked) {{
                        namespaceFilter.add(ns);
                    }} else {{
                        namespaceFilter.delete(ns);
                    }}
                    applyFilters();
                }});
            }});

            // Top classes
            const topClassesDiv = document.getElementById('top-classes');
            nodes.slice(0, 10).forEach((node, i) => {{
                const div = document.createElement('div');
                div.className = 'top-class-item';
                div.innerHTML = `
                    <div style="font-weight: 600;">${{i + 1}}. ${{node.name}}</div>
                    <div style="font-size: 0.75rem; color: #6c757d;">
                        ${{node.instanceCount.toLocaleString()}} instances
                    </div>
                `;
                div.addEventListener('click', () => selectNode(node));
                topClassesDiv.appendChild(div);
            }});
        }}

        // Apply filters
        function applyFilters() {{
            const searchTerm = document.getElementById('search-input').value.toLowerCase();
            const minInstances = parseInt(document.getElementById('instance-count-filter').value);
            const minCoverage = parseInt(document.getElementById('coverage-filter').value);

            filteredNodes = nodes.filter(node => {{
                // Search filter
                if (searchTerm) {{
                    const matchesName = node.name.toLowerCase().includes(searchTerm);
                    const matchesPredicate = node.predicates.some(p =>
                        p.predicate_short.toLowerCase().includes(searchTerm)
                    );
                    if (!matchesName && !matchesPredicate) return false;
                }}

                // Instance count filter
                if (node.instanceCount < minInstances) return false;

                // Namespace filter
                if (!namespaceFilter.has(node.namespace)) return false;

                // Coverage filter (at least one predicate with min coverage)
                if (minCoverage > 0) {{
                    const hasHighCoverage = node.predicates.some(p =>
                        p.coverage_percentage >= minCoverage
                    );
                    if (!hasHighCoverage) return false;
                }}

                return true;
            }});

            // Filter links to only include filtered nodes
            const filteredNodeIds = new Set(filteredNodes.map(n => n.id));
            filteredLinks = links.filter(link =>
                filteredNodeIds.has(link.source.id || link.source) &&
                filteredNodeIds.has(link.target.id || link.target)
            );

            updateStatistics();
            renderGraph();
        }}

        // Update statistics
        function updateStatistics() {{
            document.getElementById('total-classes').textContent = nodes.length;
            document.getElementById('visible-classes').textContent = filteredNodes.length;

            const totalInstances = nodes.reduce((sum, n) => sum + n.instanceCount, 0);
            document.getElementById('total-instances').textContent =
                totalInstances.toLocaleString();
        }}

        // Render the graph
        function renderGraph() {{
            const svg = d3.select('#graph-svg');
            svg.selectAll('*').remove();

            const container = document.getElementById('graph');
            const width = container.clientWidth;
            const height = container.clientHeight;

            svg.attr('width', width).attr('height', height);

            // Create zoom behavior
            const g = svg.append('g');
            const zoom = d3.zoom()
                .scaleExtent([0.1, 4])
                .on('zoom', (event) => {{
                    g.attr('transform', event.transform);
                }});

            svg.call(zoom);

            // Setup zoom controls
            d3.select('#zoom-in').on('click', () => {{
                svg.transition().call(zoom.scaleBy, 1.3);
            }});
            d3.select('#zoom-out').on('click', () => {{
                svg.transition().call(zoom.scaleBy, 0.7);
            }});
            d3.select('#zoom-reset').on('click', () => {{
                svg.transition().call(zoom.transform, d3.zoomIdentity);
            }});

            // Create arrow marker
            svg.append('defs').append('marker')
                .attr('id', 'arrowhead')
                .attr('viewBox', '-0 -5 10 10')
                .attr('refX', 20)
                .attr('refY', 0)
                .attr('orient', 'auto')
                .attr('markerWidth', 6)
                .attr('markerHeight', 6)
                .append('path')
                .attr('d', 'M 0,-5 L 10,0 L 0,5')
                .attr('fill', '#999');

            // Scale for node size
            const maxInstances = d3.max(filteredNodes, d => d.instanceCount) || 1;
            const nodeScale = d3.scaleSqrt()
                .domain([0, maxInstances])
                .range([5, 30]);

            // Scale for link width
            const maxLinkCount = d3.max(filteredLinks, l => l.count) || 1;
            const linkScale = d3.scaleLinear()
                .domain([0, maxLinkCount])
                .range([1, 5]);

            // Create force simulation
            simulation = d3.forceSimulation(filteredNodes)
                .force('link', d3.forceLink(filteredLinks)
                    .id(d => d.id)
                    .distance(100))
                .force('charge', d3.forceManyBody().strength(-300))
                .force('center', d3.forceCenter(width / 2, height / 2))
                .force('collision', d3.forceCollide().radius(d => nodeScale(d.instanceCount) + 5));

            // Create links
            const link = g.append('g')
                .selectAll('line')
                .data(filteredLinks)
                .join('line')
                .attr('class', 'link')
                .attr('stroke-width', d => linkScale(d.count))
                .attr('marker-end', 'url(#arrowhead)');

            // Create nodes
            const node = g.append('g')
                .selectAll('circle')
                .data(filteredNodes)
                .join('circle')
                .attr('class', 'node')
                .attr('r', d => nodeScale(d.instanceCount))
                .attr('fill', d => namespaceColors[d.namespace])
                .on('click', (event, d) => {{
                    event.stopPropagation();
                    selectNode(d);
                }})
                .call(d3.drag()
                    .on('start', dragstarted)
                    .on('drag', dragged)
                    .on('end', dragended));

            // Add labels
            const labels = g.append('g')
                .selectAll('text')
                .data(filteredNodes)
                .join('text')
                .attr('class', 'node-label')
                .text(d => d.name.split(':')[1] || d.name)
                .attr('dy', d => nodeScale(d.instanceCount) + 12);

            // Update positions on tick
            simulation.on('tick', () => {{
                link
                    .attr('x1', d => d.source.x)
                    .attr('y1', d => d.source.y)
                    .attr('x2', d => d.target.x)
                    .attr('y2', d => d.target.y);

                node
                    .attr('cx', d => d.x)
                    .attr('cy', d => d.y);

                labels
                    .attr('x', d => d.x)
                    .attr('y', d => d.y);
            }});

            // Click on background to deselect
            svg.on('click', () => selectNode(null));
        }}

        // Drag functions
        function dragstarted(event, d) {{
            if (!event.active) simulation.alphaTarget(0.3).restart();
            d.fx = d.x;
            d.fy = d.y;
        }}

        function dragged(event, d) {{
            d.fx = event.x;
            d.fy = event.y;
        }}

        function dragended(event, d) {{
            if (!event.active) simulation.alphaTarget(0);
            d.fx = null;
            d.fy = null;
        }}

        // Select a node and show details
        function selectNode(node) {{
            selectedNode = node;

            // Update node styling
            d3.selectAll('.node').classed('selected', false);
            d3.selectAll('.link').classed('highlighted', false);

            if (node) {{
                // Highlight selected node
                d3.selectAll('.node')
                    .filter(d => d.id === node.id)
                    .classed('selected', true);

                // Highlight connected links
                d3.selectAll('.link')
                    .filter(d =>
                        (d.source.id || d.source) === node.id ||
                        (d.target.id || d.target) === node.id
                    )
                    .classed('highlighted', true);

                showDetails(node);
            }} else {{
                document.getElementById('details-content').innerHTML = `
                    <div class="text-center text-muted" style="padding-top: 50px;">
                        <p>Click on a class node to view details</p>
                    </div>
                `;
            }}
        }}

        // Show node details
        function showDetails(node) {{
            const predicatesHtml = node.predicates.length > 0 ? `
                <table class="table table-sm predicate-table">
                    <thead>
                        <tr>
                            <th>Predicate</th>
                            <th>Coverage</th>
                            <th>Count</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${{node.predicates.map(p => `
                            <tr class="predicate-row">
                                <td style="font-size: 0.75rem;" title="${{p.predicate_uri}}">
                                    ${{p.predicate_short}}
                                </td>
                                <td>
                                    <span class="badge bg-primary">
                                        ${{p.coverage_percentage.toFixed(1)}}%
                                    </span>
                                </td>
                                <td>${{p.usage_count.toLocaleString()}}</td>
                            </tr>
                        `).join('')}}
                    </tbody>
                </table>
            ` : '<p class="text-muted">No predicates</p>';

            const referencedByHtml = node.referencedBy.length > 0 ? `
                <div class="reference-list">
                    ${{node.referencedBy.map(ref => `
                        <div class="reference-item" onclick="selectNodeByName('${{ref.class_name}}')">
                            <div style="font-weight: 600;">${{ref.class_name}}</div>
                            <div style="font-size: 0.75rem; color: #6c757d;">
                                ${{ref.reference_count.toLocaleString()}} references
                            </div>
                        </div>
                    `).join('')}}
                </div>
            ` : '<p class="text-muted">None</p>';

            const referencesToHtml = node.referencesTo.length > 0 ? `
                <div class="reference-list">
                    ${{node.referencesTo.map(ref => `
                        <div class="reference-item" onclick="selectNodeByName('${{ref.class_name}}')">
                            <div style="font-weight: 600;">${{ref.class_name}}</div>
                            <div style="font-size: 0.75rem; color: #6c757d;">
                                ${{ref.reference_count.toLocaleString()}} references
                            </div>
                        </div>
                    `).join('')}}
                </div>
            ` : '<p class="text-muted">None</p>';

            document.getElementById('details-content').innerHTML = `
                <h5>${{node.name}}</h5>
                <p style="font-size: 0.75rem; color: #6c757d; word-break: break-all;">
                    ${{node.uri}}
                </p>

                <div class="stat-card">
                    <div class="stat-label">Instance Count</div>
                    <div class="stat-value">${{node.instanceCount.toLocaleString()}}</div>
                </div>

                <div class="stat-card">
                    <div class="stat-label">Predicates</div>
                    <div class="stat-value">${{node.predicates.length}}</div>
                </div>

                <h6 class="mt-3">Predicates</h6>
                ${{predicatesHtml}}

                <h6 class="mt-3">Referenced By (${{node.referencedBy.length}})</h6>
                ${{referencedByHtml}}

                <h6 class="mt-3">References To (${{node.referencesTo.length}})</h6>
                ${{referencesToHtml}}
            `;
        }}

        // Select node by name (global function for onclick)
        window.selectNodeByName = function(name) {{
            const node = filteredNodes.find(n => n.name === name);
            if (node) {{
                selectNode(node);
            }}
        }};

        // Initialize on load
        document.addEventListener('DOMContentLoaded', init);

        // Handle window resize
        window.addEventListener('resize', () => {{
            if (simulation) {{
                renderGraph();
            }}
        }});
    </script>
</body>
</html>"""

    with open(output_path, 'w') as f:
        f.write(html_content)


def main():
    script_dir = Path(__file__).parent
    input_file = script_dir / 'class_predicates_analysis.json'
    output_file = script_dir / 'class_predicates_viz.html'

    if not input_file.exists():
        print(f"Error: {input_file} not found!")
        print(f"Please ensure the analysis file exists in: {script_dir}")
        sys.exit(1)

    print(f"Loading analysis data from: {input_file}")
    data = load_analysis_data(input_file)

    print(f"Generating interactive HTML visualization...")
    generate_html(data, output_file)

    print(f"✓ Successfully generated: {output_file}")
    print(f"\nOpen the file in your browser to view the visualization:")
    print(f"  file://{output_file.absolute()}")


if __name__ == '__main__':
    main()
