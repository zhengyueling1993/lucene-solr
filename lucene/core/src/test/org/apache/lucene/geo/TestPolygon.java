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
package org.apache.lucene.geo;

import org.apache.lucene.geo.Polygon;
import org.apache.lucene.index.PointValues.Relation;
import org.apache.lucene.util.LuceneTestCase;

import static org.apache.lucene.geo.GeoTestUtil.nextLatitude;
import static org.apache.lucene.geo.GeoTestUtil.nextLatitudeAround;
import static org.apache.lucene.geo.GeoTestUtil.nextLongitude;
import static org.apache.lucene.geo.GeoTestUtil.nextLongitudeAround;
import static org.apache.lucene.geo.GeoTestUtil.nextPolygon;

public class TestPolygon extends LuceneTestCase {
  
  /** null polyLats not allowed */
  public void testPolygonNullPolyLats() {
    IllegalArgumentException expected = expectThrows(IllegalArgumentException.class, () -> {
      new Polygon(null, new double[] { -66, -65, -65, -66, -66 });
    });
    assertTrue(expected.getMessage().contains("polyLats must not be null"));
  }
  
  /** null polyLons not allowed */
  public void testPolygonNullPolyLons() {
    IllegalArgumentException expected = expectThrows(IllegalArgumentException.class, () -> {
      new Polygon(new double[] { 18, 18, 19, 19, 18 }, null);
    });
    assertTrue(expected.getMessage().contains("polyLons must not be null"));
  }
  
  /** polygon needs at least 3 vertices */
  public void testPolygonLine() {
    IllegalArgumentException expected = expectThrows(IllegalArgumentException.class, () -> {
      new Polygon(new double[] { 18, 18, 18 }, new double[] { -66, -65, -66 });
    });
    assertTrue(expected.getMessage().contains("at least 4 polygon points required"));
  }
  
  /** polygon needs same number of latitudes as longitudes */
  public void testPolygonBogus() {
    IllegalArgumentException expected = expectThrows(IllegalArgumentException.class, () -> {
      new Polygon(new double[] { 18, 18, 19, 19 }, new double[] { -66, -65, -65, -66, -66 });
    });
    assertTrue(expected.getMessage().contains("must be equal length"));
  }
  
  /** polygon must be closed */
  public void testPolygonNotClosed() {
    IllegalArgumentException expected = expectThrows(IllegalArgumentException.class, () -> {
      new Polygon(new double[] { 18, 18, 19, 19, 19 }, new double[] { -66, -65, -65, -66, -67 });
    });
    assertTrue(expected.getMessage(), expected.getMessage().contains("it must close itself"));
  }
  
  /** Three boxes, an island inside a hole inside a shape */
  public void testMultiPolygon() {
    Polygon hole = new Polygon(new double[] { -10, -10, 10, 10, -10 }, new double[] { -10, 10, 10, -10, -10 });
    Polygon outer = new Polygon(new double[] { -50, -50, 50, 50, -50 }, new double[] { -50, 50, 50, -50, -50 }, hole);
    Polygon island = new Polygon(new double[] { -5, -5, 5, 5, -5 }, new double[] { -5, 5, 5, -5, -5 } );
    Polygon polygons[] = new Polygon[] { outer, island };
    
    // contains(point)
    assertTrue(Polygon.contains(polygons, -2, 2)); // on the island
    assertFalse(Polygon.contains(polygons, -6, 6)); // in the hole
    assertTrue(Polygon.contains(polygons, -25, 25)); // on the mainland
    assertFalse(Polygon.contains(polygons, -51, 51)); // in the ocean
    
    // relate(box): this can conservatively return CELL_CROSSES_QUERY
    assertEquals(Relation.CELL_INSIDE_QUERY, Polygon.relate(polygons, -2, 2, -2, 2)); // on the island
    assertEquals(Relation.CELL_OUTSIDE_QUERY, Polygon.relate(polygons, 6, 7, 6, 7)); // in the hole
    assertEquals(Relation.CELL_INSIDE_QUERY, Polygon.relate(polygons, 24, 25, 24, 25)); // on the mainland
    assertEquals(Relation.CELL_OUTSIDE_QUERY, Polygon.relate(polygons, 51, 52, 51, 52)); // in the ocean
    assertEquals(Relation.CELL_CROSSES_QUERY, Polygon.relate(polygons, -60, 60, -60, 60)); // enclosing us completely
    assertEquals(Relation.CELL_CROSSES_QUERY, Polygon.relate(polygons, 49, 51, 49, 51)); // overlapping the mainland
    assertEquals(Relation.CELL_CROSSES_QUERY, Polygon.relate(polygons, 9, 11, 9, 11)); // overlapping the hole
    assertEquals(Relation.CELL_CROSSES_QUERY, Polygon.relate(polygons, 5, 6, 5, 6)); // overlapping the island
  }
  
  public void testPacMan() throws Exception {
    // pacman
    double[] px = {0, 10, 10, 0, -8, -10, -8, 0, 10, 10, 0};
    double[] py = {0, 5, 9, 10, 9, 0, -9, -10, -9, -5, 0};

    // candidate crosses cell
    double xMin = 2;//-5;
    double xMax = 11;//0.000001;
    double yMin = -1;//0;
    double yMax = 1;//5;

    // test cell crossing poly
    Polygon polygon = new Polygon(py, px);
    assertEquals(Relation.CELL_CROSSES_QUERY, polygon.relate(yMin, yMax, xMin, xMax));
  }
  
  public void testBoundingBox() throws Exception {
    for (int i = 0; i < 100; i++) {
      Polygon polygon = nextPolygon();
      
      for (int j = 0; j < 100; j++) {
        double latitude = nextLatitude();
        double longitude = nextLongitude();
        // if the point is within poly, then it should be in our bounding box
        if (polygon.contains(latitude, longitude)) {
          assertTrue(latitude >= polygon.minLat && latitude <= polygon.maxLat);
          assertTrue(longitude >= polygon.minLon && longitude <= polygon.maxLon);
        }
      }
    }
  }
  
  public void testBoundingBoxEdgeCases() throws Exception {
    for (int i = 0; i < 100; i++) {
      Polygon polygon = nextPolygon();
      
      for (int j = 0; j < 100; j++) {
        double latitude = nextLatitudeAround(polygon.minLat, polygon.maxLat);
        double longitude = nextLongitudeAround(polygon.minLon, polygon.maxLon);
        // if the point is within poly, then it should be in our bounding box
        if (polygon.contains(latitude, longitude)) {
          assertTrue(latitude >= polygon.minLat && latitude <= polygon.maxLat);
          assertTrue(longitude >= polygon.minLon && longitude <= polygon.maxLon);
        }
      }
    }
  }
  
  /** If polygon.contains(box) returns true, then any point in that box should return true as well */
  public void testContainsRandom() throws Exception {
    for (int i = 0; i < 1000; i++) {
      Polygon polygon = nextPolygon();
      
      for (int j = 0; j < 100; j++) {
        Rectangle rectangle = GeoTestUtil.nextSimpleBox();
        // allowed to conservatively return false
        if (polygon.relate(rectangle.minLat, rectangle.maxLat, rectangle.minLon, rectangle.maxLon) == Relation.CELL_INSIDE_QUERY) {
          for (int k = 0; k < 1000; k++) {
            // this tests in our range but sometimes outside! so we have to double-check its really in other box
            double latitude = nextLatitudeAround(rectangle.minLat, rectangle.maxLat);
            double longitude = nextLongitudeAround(rectangle.minLon, rectangle.maxLon);
            // check for sure its in our box
            if (latitude >= rectangle.minLat && latitude <= rectangle.maxLat && longitude >= rectangle.minLon && longitude <= rectangle.maxLon) {
              assertTrue(polygon.contains(latitude, longitude));
            }
          }
        }
      }
    }
  }
  
  /** If polygon.contains(box) returns true, then any point in that box should return true as well */
  // different from testContainsRandom in that its not a purely random test. we iterate the vertices of the polygon
  // and generate boxes near each one of those to try to be more efficient.
  public void testContainsEdgeCases() throws Exception {
    for (int i = 0; i < 1000; i++) {
      Polygon polygon = nextPolygon();
      
      double polyLats[] = polygon.getPolyLats();
      double polyLons[] = polygon.getPolyLons();
      
      for (int vertex = 0; vertex < polyLats.length; vertex++) {
        for (int j = 0; j < 10; j++) {
          Rectangle rectangle = GeoTestUtil.nextSimpleBoxNear(polyLats[vertex], polyLons[vertex]);
          // allowed to conservatively return false
          if (polygon.relate(rectangle.minLat, rectangle.maxLat, rectangle.minLon, rectangle.maxLon) == Relation.CELL_INSIDE_QUERY) {
            for (int k = 0; k < 100; k++) {
              // this tests in our range but sometimes outside! so we have to double-check its really in other box
              double latitude = nextLatitudeAround(rectangle.minLat, rectangle.maxLat);
              double longitude = nextLongitudeAround(rectangle.minLon, rectangle.maxLon);
              // check for sure its in our box
              if (latitude >= rectangle.minLat && latitude <= rectangle.maxLat && longitude >= rectangle.minLon && longitude <= rectangle.maxLon) {
                assertTrue(polygon.contains(latitude, longitude));
              }
            }
          }
        }
      }
    }
  }
  
  /** If polygon.intersects(box) returns false, then any point in that box should return false as well */
  public void testIntersectRandom() {
    for (int i = 0; i < 100; i++) {
      Polygon polygon = nextPolygon();
      
      for (int j = 0; j < 100; j++) {
        Rectangle rectangle = GeoTestUtil.nextSimpleBox();
        // allowed to conservatively return true.
        if (polygon.relate(rectangle.minLat, rectangle.maxLat, rectangle.minLon, rectangle.maxLon) == Relation.CELL_OUTSIDE_QUERY) {
          for (int k = 0; k < 1000; k++) {
            // this tests in our range but sometimes outside! so we have to double-check its really in other box
            double latitude = nextLatitudeAround(rectangle.minLat, rectangle.maxLat);
            double longitude = nextLongitudeAround(rectangle.minLon, rectangle.maxLon);
            // check for sure its in our box
            if (latitude >= rectangle.minLat && latitude <= rectangle.maxLat && longitude >= rectangle.minLon && longitude <= rectangle.maxLon) {
              assertFalse(polygon.contains(latitude, longitude));
            }
          }
        }
      }
    }
  }
  
  /** If polygon.intersects(box) returns false, then any point in that box should return false as well */
  // different from testIntersectsRandom in that its not a purely random test. we iterate the vertices of the polygon
  // and generate boxes near each one of those to try to be more efficient.
  public void testIntersectEdgeCases() {
    for (int i = 0; i < 100; i++) {
      Polygon polygon = nextPolygon();

      double polyLats[] = polygon.getPolyLats();
      double polyLons[] = polygon.getPolyLons();

      for (int vertex = 0; vertex < polyLats.length; vertex++) {
        for (int j = 0; j < 10; j++) {
          Rectangle rectangle = GeoTestUtil.nextSimpleBoxNear(polyLats[vertex], polyLons[vertex]);
          // allowed to conservatively return true.
          if (polygon.relate(rectangle.minLat, rectangle.maxLat, rectangle.minLon, rectangle.maxLon) == Relation.CELL_OUTSIDE_QUERY) {
            for (int k = 0; k < 100; k++) {
              // this tests in our range but sometimes outside! so we have to double-check its really in other box
              double latitude = nextLatitudeAround(rectangle.minLat, rectangle.maxLat);
              double longitude = nextLongitudeAround(rectangle.minLon, rectangle.maxLon);
              // check for sure its in our box
              if (latitude >= rectangle.minLat && latitude <= rectangle.maxLat && longitude >= rectangle.minLon && longitude <= rectangle.maxLon) {
                assertFalse(polygon.contains(latitude, longitude));
              }
            }
          }
        }
      }
    }
  }
}
