/*
 * This software Copyright by the RPTools.net development team, and
 * licensed under the Affero GPL Version 3 or, at your option, any later
 * version.
 *
 * MapTool Source Code is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public
 * License * along with this source Code.  If not, please visit
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.automation.tile;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.rptools.maptool.automation.tile.TileRegion.Point;
import net.rptools.maptool.automation.tile.TileRegion.Traversal;
import org.junit.jupiter.api.Test;

class TileRegionTest {
  private final TileRegion rectangle = TileRegion.rectangle("r", "Room", "map", 0, 0, 10, 10);

  @Test
  void rectangleContainsStrictInteriorAndExcludesBoundary() {
    assertTrue(rectangle.validationErrors().isEmpty());
    assertTrue(rectangle.contains(new Point(5, 5)));
    assertFalse(rectangle.contains(new Point(0, 5)));
    assertFalse(rectangle.contains(new Point(0, 0)));
    assertFalse(rectangle.contains(new Point(12, 5)));
  }

  @Test
  void tracesEntryExitPassThroughAndInsideMovement() {
    assertEquals(
        new Traversal(false, true, true, false, true),
        rectangle.trace(new Point(-5, 5), new Point(5, 5)));
    assertEquals(
        new Traversal(true, false, false, true, true),
        rectangle.trace(new Point(5, 5), new Point(15, 5)));
    assertEquals(
        new Traversal(false, false, true, true, true),
        rectangle.trace(new Point(-5, 5), new Point(15, 5)));
    assertEquals(
        new Traversal(true, true, false, false, true),
        rectangle.trace(new Point(2, 2), new Point(8, 8)));
  }

  @Test
  void boundaryTravelTangentAndOutsideMovementDoNotFire() {
    Traversal none = new Traversal(false, false, false, false, false);
    assertEquals(none, rectangle.trace(new Point(-5, 0), new Point(15, 0)));
    assertEquals(none, rectangle.trace(new Point(-5, 5), new Point(5, -5)));
    assertEquals(none, rectangle.trace(new Point(-5, -1), new Point(15, -1)));
    assertEquals(none, rectangle.trace(new Point(-5, 0), new Point(0, 0)));
  }

  @Test
  void boundaryToInteriorAndInteriorToBoundaryAreTransitions() {
    assertEquals(
        new Traversal(false, true, true, false, true),
        rectangle.trace(new Point(0, 5), new Point(5, 5)));
    assertEquals(
        new Traversal(true, false, false, true, true),
        rectangle.trace(new Point(5, 5), new Point(10, 5)));
  }

  @Test
  void zeroLengthSegmentNeverActivates() {
    assertEquals(
        new Traversal(true, true, false, false, false),
        rectangle.trace(new Point(5, 5), new Point(5, 5)));
    assertEquals(
        new Traversal(false, false, false, false, false),
        rectangle.trace(new Point(0, 5), new Point(0, 5)));
    assertEquals(
        new Traversal(false, false, false, false, false),
        rectangle.trace(new Point(-0.0, 0), new Point(0, 0)));
  }

  @Test
  void concaveRegionCanExitAndReenterBetweenInsideEndpoints() {
    TileRegion region =
        new TileRegion(
            "u",
            "U-shaped room",
            "map",
            List.of(
                new Point(0, 0),
                new Point(10, 0),
                new Point(10, 10),
                new Point(7, 10),
                new Point(7, 3),
                new Point(3, 3),
                new Point(3, 10),
                new Point(0, 10)));
    assertTrue(region.validationErrors().isEmpty());
    assertEquals(
        new Traversal(true, true, true, true, true),
        region.trace(new Point(1, 5), new Point(9, 5)));
    assertEquals(
        new Traversal(false, false, true, true, true),
        region.trace(new Point(-1, 5), new Point(11, 5)));
  }

  @Test
  void validatesMalformedGeometryWithoutPreventingInspection() {
    TileRegion bowtie =
        new TileRegion(
            "bad",
            "Crossed",
            "map",
            List.of(new Point(0, 0), new Point(10, 10), new Point(0, 10), new Point(10, 0)));
    assertFalse(bowtie.validationErrors().isEmpty());
    assertThrows(IllegalArgumentException.class, () -> bowtie.contains(new Point(1, 1)));
    assertThrows(
        IllegalArgumentException.class, () -> bowtie.trace(new Point(0, 0), new Point(1, 1)));
    TileRegion flat =
        new TileRegion(
            "flat", "Flat", "map", List.of(new Point(0, 0), new Point(1, 0), new Point(2, 0)));
    assertFalse(flat.validationErrors().isEmpty());
    assertFalse(new TileRegion(null, "", "", null).validationErrors().isEmpty());
    assertFalse(
        new TileRegion("null", "Null", "map", Arrays.asList(new Point(0, 0), null, null))
            .validationErrors()
            .isEmpty());
  }

  @Test
  void rejectsNonfiniteCoordinatesAndInvalidRectangles() {
    assertThrows(
        IllegalArgumentException.class, () -> TileRegion.rectangle("r", "R", "m", 0, 0, -1, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> TileRegion.rectangle("r", "R", "m", Double.NaN, 0, 1, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> rectangle.trace(new Point(Double.POSITIVE_INFINITY, 0), new Point(1, 1)));
    assertThrows(IllegalArgumentException.class, () -> rectangle.contains(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> rectangle.trace(new Point(-Double.MAX_VALUE, 1), new Point(Double.MAX_VALUE, 1)));
    TileRegion region =
        new TileRegion(
            "nan",
            "Invalid",
            "map",
            List.of(new Point(0, 0), new Point(1, Double.NaN), new Point(1, 1)));
    assertFalse(region.validationErrors().isEmpty());
  }

  @Test
  void acceptsExplicitClosingVertexAndRoundTripsWithGson() {
    List<Point> vertices = new ArrayList<>(rectangle.vertices());
    vertices.add(vertices.getFirst());
    TileRegion closed = new TileRegion("r", "Room", "map", vertices);
    vertices.clear();
    assertTrue(closed.validationErrors().isEmpty());
    assertTrue(closed.contains(new Point(5, 5)));
    assertThrows(UnsupportedOperationException.class, () -> closed.vertices().clear());
    Gson gson = new Gson();
    TileRegion restored = gson.fromJson(gson.toJson(closed), TileRegion.class);
    assertEquals(closed, restored);
    assertEquals(
        closed.trace(new Point(-1, 5), new Point(11, 5)),
        restored.trace(new Point(-1, 5), new Point(11, 5)));
  }
}
