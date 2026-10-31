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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.valid.IsValidOp;

/**
 * A named simple polygon in map coordinates. The closing vertex is optional in serialized data.
 *
 * <p>Occupancy uses the strict interior: an edge or vertex is outside. Consequently touching a
 * region or travelling along its edge does not activate it. A committed segment that passes through
 * its interior can enter and exit even when both endpoints are outside. No mutable JTS types are
 * exposed or retained, so these records can be serialized directly and shared by callers.
 */
public record TileRegion(String id, String name, String mapId, List<Point> vertices) {
  private static final GeometryFactory GEOMETRY = new GeometryFactory();

  public TileRegion {
    // Keep invalid definitions inspectable by validationErrors(), including null vertex entries.
    vertices =
        vertices == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(vertices));
  }

  /** A JSON-friendly coordinate, independent of any rendering or geometry library. */
  public record Point(double x, double y) {}

  /**
   * Summary of one committed segment. Enter/exit may both be true for a pass-through or a concave
   * region. Crossed means a positive-length part of the segment traversed the strict interior; a
   * zero-length move never crosses, enters, or exits.
   */
  public record Traversal(
      boolean startsInside, boolean endsInside, boolean entered, boolean exited, boolean crossed) {}

  public static TileRegion rectangle(
      String id, String name, String mapId, double x, double y, double width, double height) {
    if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Region width and height must be finite and positive");
    }
    TileRegion region =
        new TileRegion(
            id,
            name,
            mapId,
            List.of(
                new Point(x, y),
                new Point(x + width, y),
                new Point(x + width, y + height),
                new Point(x, y + height)));
    region.requireValid();
    return region;
  }

  /** Reports authoring errors without throwing, allowing disabled/invalid tiles to be inspected. */
  public List<String> validationErrors() {
    List<String> errors = new ArrayList<>();
    if (id == null || id.isBlank()) errors.add("Region id is required");
    if (name == null || name.isBlank()) errors.add("Region name is required");
    if (mapId == null || mapId.isBlank()) errors.add("Region mapId is required");
    if (vertices.size() < 3) errors.add("Region requires at least three vertices");
    for (Point vertex : vertices) {
      if (!finite(vertex)) {
        errors.add("Region vertices must have finite coordinates");
        return List.copyOf(errors);
      }
    }
    if (vertices.size() >= 3) {
      try {
        Polygon polygon = polygon();
        var error = new IsValidOp(polygon).getValidationError();
        if (error != null) errors.add("Invalid region polygon: " + error.getMessage());
        if (!(polygon.getArea() > 0) || !Double.isFinite(polygon.getArea())) {
          errors.add("Region must have finite, positive area");
        }
      } catch (IllegalArgumentException exception) {
        errors.add("Invalid region polygon: " + exception.getMessage());
      }
    }
    return List.copyOf(errors);
  }

  public boolean contains(Point point) {
    requireValid();
    requireFinite(point);
    return contains(polygon(), point);
  }

  /** Determines the transitions along a committed straight segment, not merely at its endpoints. */
  public Traversal trace(Point from, Point to) {
    requireValid();
    requireFinite(from);
    requireFinite(to);
    Polygon polygon = polygon();
    boolean startsInside = contains(polygon, from);
    boolean endsInside = contains(polygon, to);
    double dx = to.x - from.x;
    double dy = to.y - from.y;
    if (!Double.isFinite(dx) || !Double.isFinite(dy)) {
      throw new IllegalArgumentException("Segment delta must be finite");
    }
    if (dx == 0 && dy == 0) {
      return new Traversal(startsInside, endsInside, false, false, false);
    }

    var segment = GEOMETRY.createLineString(new Coordinate[] {coordinate(from), coordinate(to)});
    // Split at every boundary intersection, including overlap endpoints. The midpoint of each
    // resulting open interval is either strictly inside or outside. This handles concave regions
    // and segments that enter and leave multiple times without counting tangencies as entries.
    TreeSet<Double> parameters = new TreeSet<>();
    parameters.add(0.0);
    parameters.add(1.0);
    boolean useX = Math.abs(dx) >= Math.abs(dy);
    double delta = useX ? dx : dy;
    for (Coordinate intersection : segment.intersection(polygon.getBoundary()).getCoordinates()) {
      double parameter =
          useX ? (intersection.x - from.x) / delta : (intersection.y - from.y) / delta;
      parameters.add(Math.clamp(parameter, 0, 1));
    }
    List<Double> cuts = new ArrayList<>(parameters);
    boolean entered = false;
    boolean exited = false;
    boolean crossed = false;
    boolean previousInside = startsInside;
    for (int i = 1; i < cuts.size(); i++) {
      double middle = cuts.get(i - 1) + (cuts.get(i) - cuts.get(i - 1)) / 2;
      Point point = new Point(from.x + (to.x - from.x) * middle, from.y + (to.y - from.y) * middle);
      boolean inside = contains(polygon, point);
      entered |= !previousInside && inside;
      exited |= previousInside && !inside;
      crossed |= inside;
      previousInside = inside;
    }
    entered |= !previousInside && endsInside;
    exited |= previousInside && !endsInside;
    return new Traversal(startsInside, endsInside, entered, exited, crossed);
  }

  private Polygon polygon() {
    boolean closed = vertices.getFirst().equals(vertices.getLast());
    Coordinate[] coordinates = new Coordinate[vertices.size() + (closed ? 0 : 1)];
    for (int i = 0; i < vertices.size(); i++) coordinates[i] = coordinate(vertices.get(i));
    if (!closed) coordinates[coordinates.length - 1] = coordinate(vertices.getFirst());
    return GEOMETRY.createPolygon(coordinates);
  }

  private static boolean contains(Polygon polygon, Point point) {
    return polygon.contains(GEOMETRY.createPoint(coordinate(point)));
  }

  private void requireValid() {
    List<String> errors = validationErrors();
    if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
  }

  private static Coordinate coordinate(Point point) {
    return new Coordinate(point.x, point.y);
  }

  private static boolean finite(Point point) {
    return point != null && Double.isFinite(point.x) && Double.isFinite(point.y);
  }

  private static void requireFinite(Point point) {
    if (!finite(point)) throw new IllegalArgumentException("Point must have finite coordinates");
  }
}
