# Bug: Graph Viewer 3D Rendering Broken for Large Datasets

**Priority:** High
**Labels:** graph-viewer, ux, 3d-rendering
**Status:** Open — needs Jira ticket (create when server available)

## Problem

Graph Viewer 3D rendering cho large datasets (89+ nodes) hiển thị không đúng:

1. Camera zoom quá gần vào 1 Epic node khi không có zoomToFit
2. Camera zoom quá xa khi có zoomToFit (toàn bộ graph thành 1 chấm nhỏ)
3. Node labels không hiển thị cho các nodes nhỏ
4. 3D perspective làm nodes xa camera trở thành dots nhỏ

## Context

- LDM (30 nodes) hiển thị đẹp
- MTO (89 nodes, 110 edges) hiển thị sai
- Minimap hiển thị graph đúng — vấn đề là 3D camera

## Proposed Fix

- Option A: `graph.numDimensions(2)` — flatten to 2D (recommended)
- Option B: Custom camera positioning based on x/y bounding box only
- Option C: Reduce z-axis spread in force simulation

## AC

- AC1: MTO (89 nodes) hiển thị đầy đủ nodes với labels visible
- AC2: Camera auto-fit show toàn bộ graph
- AC3: LDM (30 nodes) vẫn hiển thị đẹp
