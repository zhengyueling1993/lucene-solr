/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.spatial3d.geom;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * GeoConvexPolygon objects are generic building blocks of more complex structures.
 * The only restrictions on these objects are: (1) they must be convex; (2) they must have
 * a maximum extent no larger than PI.  Violating either one of these limits will
 * cause the logic to fail.
 *
 * @lucene.internal
 */
class GeoConvexPolygon extends GeoBasePolygon {
  /** The list of polygon points */
  protected final List<GeoPoint> points;
  /** A bitset describing, for each edge, whether it is internal or not */
  protected final BitSet isInternalEdges;
  /** The list of holes.  If a point is in the hole, it is *not* in the polygon */
  protected final List<GeoPolygon> holes;

  /** A list of edges */
  protected SidedPlane[] edges = null;
  /** The set of notable points for each edge */
  protected GeoPoint[][] notableEdgePoints = null;
  /** A point which is on the boundary of the polygon */
  protected GeoPoint[] edgePoints = null;
  /** Set to true when the polygon is complete */
  protected boolean isDone = false;
  /** A bounds object for each sided plane */
  protected Map<SidedPlane, Membership> eitherBounds = null;
  
  /**
   * Create a convex polygon from a list of points.  The first point must be on the
   * external edge.
   *@param planetModel is the planet model.
   *@param pointList is the list of points to create the polygon from.
   */
  public GeoConvexPolygon(final PlanetModel planetModel, final List<GeoPoint> pointList) {
    this(planetModel, pointList, null);
  }
  
  /**
   * Create a convex polygon from a list of points.  The first point must be on the
   * external edge.
   *@param planetModel is the planet model.
   *@param pointList is the list of points to create the polygon from.
   *@param holes is the list of GeoPolygon objects that describe holes in the complex polygon.  Null == no holes.
   */
  public GeoConvexPolygon(final PlanetModel planetModel, final List<GeoPoint> pointList, final List<GeoPolygon> holes) {
    super(planetModel);
    this.points = pointList;
    this.holes = holes;
    this.isInternalEdges = new BitSet();
    done(false);
  }

  /**
   * Create a convex polygon from a list of points, keeping track of which boundaries
   * are internal.  This is used when creating a polygon as a building block for another shape.
   *@param planetModel is the planet model.
   *@param pointList is the set of points to create the polygon from.
   *@param internalEdgeFlags is a bitset describing whether each edge is internal or not.
   *@param returnEdgeInternal is true when the final return edge is an internal one.
   */
  public GeoConvexPolygon(final PlanetModel planetModel,
    final List<GeoPoint> pointList,
    final BitSet internalEdgeFlags,
    final boolean returnEdgeInternal) {
    this(planetModel, pointList, null, internalEdgeFlags, returnEdgeInternal);
  }

  /**
   * Create a convex polygon from a list of points, keeping track of which boundaries
   * are internal.  This is used when creating a polygon as a building block for another shape.
   *@param planetModel is the planet model.
   *@param pointList is the set of points to create the polygon from.
   *@param holes is the list of GeoPolygon objects that describe holes in the complex polygon.  Null == no holes.
   *@param internalEdgeFlags is a bitset describing whether each edge is internal or not.
   *@param returnEdgeInternal is true when the final return edge is an internal one.
   */
  public GeoConvexPolygon(final PlanetModel planetModel,
    final List<GeoPoint> pointList,
    final List<GeoPolygon> holes,
    final BitSet internalEdgeFlags,
    final boolean returnEdgeInternal) {
    super(planetModel);
    this.points = pointList;
    this.holes = holes;
    this.isInternalEdges = internalEdgeFlags;
    done(returnEdgeInternal);
  }

  /**
   * Create a convex polygon, with a starting latitude and longitude.
   * Accepts only values in the following ranges: lat: {@code -PI/2 -> PI/2}, lon: {@code -PI -> PI}
   *@param planetModel is the planet model.
   *@param startLatitude is the latitude of the first point.
   *@param startLongitude is the longitude of the first point.
   */
  public GeoConvexPolygon(final PlanetModel planetModel,
    final double startLatitude,
    final double startLongitude) {
    this(planetModel, startLatitude, startLongitude, null);
  }
  
  /**
   * Create a convex polygon, with a starting latitude and longitude.
   * Accepts only values in the following ranges: lat: {@code -PI/2 -> PI/2}, lon: {@code -PI -> PI}
   *@param planetModel is the planet model.
   *@param startLatitude is the latitude of the first point.
   *@param startLongitude is the longitude of the first point.
   *@param holes is the list of GeoPolygon objects that describe holes in the complex polygon.  Null == no holes.
   */
  public GeoConvexPolygon(final PlanetModel planetModel,
    final double startLatitude,
    final double startLongitude,
    final List<GeoPolygon> holes) {
    super(planetModel);
    points = new ArrayList<>();
    this.holes = holes;
    isInternalEdges = new BitSet();
    points.add(new GeoPoint(planetModel, startLatitude, startLongitude));
  }

  /**
   * Add a point to the polygon.
   * Accepts only values in the following ranges: lat: {@code -PI/2 -> PI/2}, lon: {@code -PI -> PI}
   *
   * @param latitude       is the latitude of the next point.
   * @param longitude      is the longitude of the next point.
   * @param isInternalEdge is true if the edge just added with this point should be considered "internal", and not
   *                       intersected as part of the intersects() operation.
   */
  public void addPoint(final double latitude, final double longitude, final boolean isInternalEdge) {
    if (isDone)
      throw new IllegalStateException("Can't call addPoint() if done() already called");
    if (isInternalEdge)
      isInternalEdges.set(points.size() - 1);
    points.add(new GeoPoint(planetModel, latitude, longitude));
  }

  /**
   * Finish the polygon, by connecting the last added point with the starting point.
   *@param isInternalReturnEdge is true if the return edge (back to start) is an internal one.
   */
  public void done(final boolean isInternalReturnEdge) {
    if (isDone)
      throw new IllegalStateException("Can't call done() more than once");
    // If fewer than 3 points, can't do it.
    if (points.size() < 3)
      throw new IllegalArgumentException("Polygon needs at least three points.");

    if (isInternalReturnEdge)
      isInternalEdges.set(points.size() - 1);

    isDone = true;
    
    // Time to construct the planes.  If the polygon is truly convex, then any adjacent point
    // to a segment can provide an interior measurement.
    edges = new SidedPlane[points.size()];
    notableEdgePoints = new GeoPoint[points.size()][];

    for (int i = 0; i < points.size(); i++) {
      final GeoPoint start = points.get(i);
      final GeoPoint end = points.get(legalIndex(i + 1));
      // We have to find the next point that is not on the plane between start and end.
      // If there is no such point, it's an error.
      final Plane planeToFind = new Plane(start, end);
      int endPointIndex = -1;
      for (int j = 0; j < points.size(); j++) {
        final int index = legalIndex(j + i + 2);
        if (!planeToFind.evaluateIsZero(points.get(index))) {
          endPointIndex = index;
          break;
        }
      }
      if (endPointIndex == -1) {
        throw new IllegalArgumentException("Polygon points are all coplanar");
      }
      final GeoPoint check = points.get(endPointIndex);
      final SidedPlane sp = new SidedPlane(check, start, end);
      //System.out.println("Created edge "+sp+" using start="+start+" end="+end+" check="+check);
      edges[i] = sp;
      notableEdgePoints[i] = new GeoPoint[]{start, end};
    }
    /* Disabled since GeoPolygonFactory does the checking too.
    // In order to naively confirm that the polygon is convex, I would need to
    // check every edge, and verify that every point (other than the edge endpoints)
    // is within the edge's sided plane.  This is an order n^2 operation.  That's still
    // not wrong, though, because everything else about polygons has a similar cost.
    for (int edgeIndex = 0; edgeIndex < edges.length; edgeIndex++) {
      final SidedPlane edge = edges[edgeIndex];
      for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
        if (pointIndex != edgeIndex && pointIndex != legalIndex(edgeIndex + 1)) {
          if (!edge.isWithin(points.get(pointIndex)))
            throw new IllegalArgumentException("Polygon is not convex: Point " + points.get(pointIndex) + " Edge " + edge);
        }
      }
    }
    */
    
    // For each edge, create a bounds object.
    eitherBounds = new HashMap<>(edges.length);
    for (int edgeIndex = 0; edgeIndex < edges.length; edgeIndex++) {
      final SidedPlane edge = edges[edgeIndex];
      int bound1Index = legalIndex(edgeIndex+1);
      while (edges[legalIndex(bound1Index)].isNumericallyIdentical(edge)) {
        bound1Index++;
      }
      int bound2Index = legalIndex(edgeIndex-1);
      while (edges[legalIndex(bound2Index)].isNumericallyIdentical(edge)) {
        bound2Index--;
      }
      eitherBounds.put(edge, new EitherBound(edges[legalIndex(bound1Index)], edges[legalIndex(bound2Index)]));
    }
    
    // Pick an edge point arbitrarily
    edgePoints = new GeoPoint[]{points.get(0)};
  }

  /** Compute a legal point index from a possibly illegal one, that may have wrapped.
   *@param index is the index.
   *@return the normalized index.
   */
  protected int legalIndex(int index) {
    while (index >= points.size())
      index -= points.size();
    while (index < 0) {
      index += points.size();
    }
    return index;
  }

  @Override
  public boolean isWithin(final double x, final double y, final double z) {
    for (final SidedPlane edge : edges) {
      if (!edge.isWithin(x, y, z))
        return false;
    }
    if (holes != null) {
      for (final GeoPolygon polygon : holes) {
        if (polygon.isWithin(x, y, z)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public GeoPoint[] getEdgePoints() {
    return edgePoints;
  }

  @Override
  public boolean intersects(final Plane p, final GeoPoint[] notablePoints, final Membership... bounds) {
    //System.err.println("Checking for polygon intersection with plane "+p+"...");
    for (int edgeIndex = 0; edgeIndex < edges.length; edgeIndex++) {
      final SidedPlane edge = edges[edgeIndex];
      final GeoPoint[] points = this.notableEdgePoints[edgeIndex];
      if (!isInternalEdges.get(edgeIndex)) {
        //System.err.println("Checking convex edge "+edge+" for intersection against plane "+p);
        if (edge.intersects(planetModel, p, notablePoints, points, bounds, eitherBounds.get(edge))) {
          //System.err.println(" intersects!");
          return true;
        }
      }
    }
    if (holes != null) {
      // Each hole needs to be looked at for intersection too, since a shape can be entirely within the hole
      for (final GeoPolygon hole : holes) {
        if (hole.intersects(p, notablePoints, bounds)) {
          return true;
        }
      }
    }
    //System.err.println(" no intersection");
    return false;
  }

  /** A membership implementation representing polygon edges that must apply.
   */
  protected class EitherBound implements Membership {
    
    protected final SidedPlane sideBound1;
    protected final SidedPlane sideBound2;
    
    /** Constructor.
      * @param sideBound1 is the first side bound.
      * @param sideBound2 is the second side bound.
      */
    public EitherBound(final SidedPlane sideBound1, final SidedPlane sideBound2) {
      this.sideBound1 = sideBound1;
      this.sideBound2 = sideBound2;
    }

    @Override
    public boolean isWithin(final Vector v) {
      return sideBound1.isWithin(v) && sideBound2.isWithin(v);
    }

    @Override
    public boolean isWithin(final double x, final double y, final double z) {
      return sideBound1.isWithin(x,y,z) && sideBound2.isWithin(x,y,z);
    }
  }


  @Override
  public void getBounds(Bounds bounds) {
    super.getBounds(bounds);

    // Add all the points
    for (final GeoPoint point : points) {
      bounds.addPoint(point);
    }

    // Add planes with membership.
    for (final SidedPlane edge : edges) {
      bounds.addPlane(planetModel, edge, eitherBounds.get(edge));
    }
  }

  @Override
  protected double outsideDistance(final DistanceStyle distanceStyle, final double x, final double y, final double z) {
    double minimumDistance = Double.MAX_VALUE;
    for (final GeoPoint edgePoint : points) {
      final double newDist = distanceStyle.computeDistance(edgePoint, x,y,z);
      if (newDist < minimumDistance) {
        minimumDistance = newDist;
      }
    }
    for (final SidedPlane edgePlane : edges) {
      final double newDist = distanceStyle.computeDistance(planetModel, edgePlane, x, y, z, eitherBounds.get(edgePlane));
      if (newDist < minimumDistance) {
        minimumDistance = newDist;
      }
    }
    return minimumDistance;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof GeoConvexPolygon))
      return false;
    GeoConvexPolygon other = (GeoConvexPolygon) o;
    if (!super.equals(other))
      return false;
    if (!other.isInternalEdges.equals(isInternalEdges))
      return false;
    if (other.holes != null || holes != null) {
      if (other.holes == null || holes == null) {
        return false;
      }
      if (!other.holes.equals(holes)) {
        return false;
      }
    }
    return (other.points.equals(points));
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + points.hashCode();
    if (holes != null) {
      result = 31 * result + holes.hashCode();
    }
    return result;
  }

  @Override
  public String toString() {
    return "GeoConvexPolygon: {planetmodel=" + planetModel + ", points=" + points + ", internalEdges="+isInternalEdges+((holes== null)?"":", holes=" + holes) + "}";
  }
}
  
