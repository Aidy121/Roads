// Author: Aidan Fisher

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;

public class Connection extends RoadSegment {
	// Because of the way Connections are formed, (they are simply a bunch of perfect curves for each lane,) the lane size can vary within the connection.
	// This has the benefit that the connection "is" a perfect curve.

	public static int standardSize = 200;

	public static int STRAIGHT = 0;
	public static int RIGHT = 1;
	public static int LEFT = 2;

	public RoadSegment r1, r2;
	public int s1, s2;
	public Intersection intersection = null;
	public int type = STRAIGHT; // Types are for intersections

	public Connection(RoadSegment r1, boolean side1, RoadSegment r2, boolean side2, boolean exists) {
		this.r1 = r1;
		this.r2 = r2;
		this.exists = exists;
		this.arc = true;
		int s1 = 1;
		if (side1)
			s1 = 0;
		int s2 = 1;
		if (side2)
			s2 = 0;
		this.s1 = s1;
		this.s2 = s2;

		if (exists) {
			RoadAdding.setSides(side1, side2, r1, r2, this);
		}

	}

	public Connection(RoadSegment r1, boolean side1, RoadSegment r2, boolean side2, Intersection intersection, boolean exists) {
		this.intersection = intersection;
		this.r1 = r1;
		this.r2 = r2;
		this.exists = exists;
		this.arc = true; // All connections are curves (arcs for purposes as well)
		int s1 = 1;
		if (side1)
			s1 = 0;
		int s2 = 1;
		if (side2)
			s2 = 0;
		this.s1 = s1;
		this.s2 = s2;

		if (exists) {
			RoadAdding.setSides(side1, side2, r1, r2, this);
		}

	}

	public void drawLine(Graphics2D g, int i, int j) {

		if (i == r1.yellowLine && j == r2.yellowLine) {
			g.setStroke(new BasicStroke((float) (lineSize * Component.zoomLevel), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
			g.setColor(new Color(255, 255, 0));
		} else if ((i == 0 || i == r1.numPaths) && (j == 0 || j == r2.numPaths)) {
			g.setStroke(new BasicStroke((float) (lineSize * Component.zoomLevel), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
			g.setColor(new Color(255, 255, 255));
		} else {
			float dash[] = { (float) (lineLength * Component.zoomLevel) };
			g.setStroke(new BasicStroke((float) (lineSize * Component.zoomLevel), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL, (float) (lineLength * Component.zoomLevel), dash, 0.0f));
			g.setColor(new Color(255, 255, 255));
		}
		Point.Double a = r1.getPoint(s1, i);
		Point.Double b = r2.getPoint(s2, j);

		Shape curve = getCurve(r1.direction[s1] + s1 * Math.PI, r2.direction[s2] + s2 * Math.PI, a, b);

		g.draw(curve);
	}

	public void deleteRoad() {
		Component.connections.remove(this);
		this.exists = false;

		// Connections removes cars on it:
		for (int i = 0; i < paths.size(); i++) {
			removeCars(i);

			for (int j = 0; j < Component.roads.size(); j++) {
				for (int k = 0; k < Component.roads.get(j).paths.size(); k++) {
					Component.roads.get(j).paths.get(k).nextPath.remove(paths.get(i));
					Component.roads.get(j).paths.get(k).beforePath.remove(paths.get(i));
					Component.roads.get(j).paths.get(k).yieldTo.remove(paths.get(i));
				}
			}

		}
	}

	public void setPaths(RoadSegment r1, RoadSegment r2) {
		// Lanes are 2 apart.
		for (int i = 0; i < numPaths; i++) {
			if (i < yellowLine) {
				paths.add(new Path(this, r1, r1.direction[s1], r2, r2.direction[s2]));
			} else {
				paths.add(new Path(this, r2, r2.direction[s2], r1, r1.direction[s1]));
			}
		}
	}

	// Tells whether or not the intersection p is in the direction d from linePoint.
	public static boolean isOnLine(double d, Point.Double p, Point.Double linePoint) {
		if (Math.cos(d) <= 0.2 && Math.cos(d) >= -0.2) {
			return Math.sin(d) > 0 && p.y < linePoint.y || Math.sin(d) < 0 && p.y > linePoint.y;
		} else {
			return Math.cos(d) > 0 && p.x < linePoint.x || Math.cos(d) < 0 && p.x > linePoint.x;
		}
	}

	// Gets the real curve between the two points using their directions.
	public static Shape getCurve(double dirA, double dirB, Point.Double a, Point.Double b) {
		// Curve SHOULD be under 90 degrees
		Point.Double inter = RoadAdding.getPointOfIntersection(dirA, dirB, a, b, false);
		Shape curve;
		boolean onLineA = isOnLine(dirA, inter, a);
		boolean onLineB = isOnLine(dirB, inter, b);
		if (!onLineA && onLineB) {
			Point.Double inter2 = RoadAdding.getPointOfIntersection(dirA, dirA + Math.PI / 2, a, b, false);
			double distance = Path.distance(inter2, a) / 2;

			curve = new CubicCurve2D.Double(a.x, a.y, a.x - Math.cos(dirA) * distance, a.y - Math.sin(dirA) * distance, b.x - Math.cos(dirB) * distance, b.y - Math.sin(dirB) * distance, b.x, b.y);
		} else if (!onLineB && onLineA) {
			Point.Double inter2 = RoadAdding.getPointOfIntersection(dirB, dirB + Math.PI / 2, b, a, false);
			double distance = Path.distance(inter2, b) / 2;

			curve = new CubicCurve2D.Double(a.x, a.y, a.x - Math.cos(dirA) * distance, a.y - Math.sin(dirA) * distance, b.x - Math.cos(dirB) * distance, b.y - Math.sin(dirB) * distance, b.x, b.y);
		} else if (!onLineA && !onLineB) {
			// Error!
			curve = new QuadCurve2D.Double(a.x, a.y, inter.x, inter.y, b.x, b.y);
		} else {
			curve = new QuadCurve2D.Double(a.x, a.y, inter.x, inter.y, b.x, b.y);
		}
		return curve;
	}

	// The rendering is done by converting the object into a polygon essentially. Neglible performance loss.
	public void renderRoad(Graphics2D g) {
		g.setColor(getRoadColor());

		Point.Double a, b;
		a = r1.getPoint(s1, 0);
		if (s1 == s2) {
			b = r2.getPoint(s2, r2.numPaths);
		} else {
			b = r2.getPoint(s2, 0);
		}
		Shape curve1 = Connection.getCurve(r1.direction[s1] + s1 * Math.PI, r2.direction[s2] + s2 * Math.PI, a, b);
		a = r1.getPoint(s1, r1.numPaths);
		if (s1 == s2) {
			b = r2.getPoint(s2, 0);
		} else {
			b = r2.getPoint(s2, r2.numPaths);
		}
		Shape curve2 = Connection.getCurve(r1.direction[s1] + s1 * Math.PI, r2.direction[s2] + s2 * Math.PI, a, b);

		// Turn the curve into a polygon so it can be rendered within the polygon. (As the width is constantly changing)
		Polygon2D p = new Polygon2D();

		PathIterator pit = curve1.getPathIterator(null, 1);
		double[] coords = new double[2];
		while (!pit.isDone()) {
			pit.currentSegment(coords);
			p.addPoint(coords[0], coords[1]);
			pit.next();
		}

		pit = curve2.getPathIterator(null, 1);

		ArrayList<Point.Double> addPoints = new ArrayList<Point.Double>();

		coords = new double[2];
		while (!pit.isDone()) {
			pit.currentSegment(coords);
			addPoints.add(0, new Point.Double(coords[0], coords[1]));
			pit.next();
		}

		for (int i = 0; i < addPoints.size(); i++) {
			p.addPoint(addPoints.get(i).x, addPoints.get(i).y);
		}

		g.fill(p);
		g.setStroke(new ExtendedStroke(p, (int) RoadSegment.shoulderWidth + 2));
		g.draw(p);

		highlighted = false;
	}

	public void renderLines(Graphics2D g) {
		for (Path path : paths) {
			int index1, index2;
			if (r1.paths.contains(path.beforePath.get(0))) {
				index1 = r1.paths.indexOf(path.beforePath.get(0));
				index2 = r2.paths.indexOf(path.nextPath.get(0));
			} else {
				index1 = r1.paths.indexOf(path.nextPath.get(0));
				index2 = r2.paths.indexOf(path.beforePath.get(0));
			}
			if (index1 >= r1.yellowLine) {
				index1++;
			}
			if (index2 >= r2.yellowLine) {
				index2++;
			}
			drawLine(g, index1, index2);
		}
		drawLine(g, r1.yellowLine, r2.yellowLine);
	}
}
