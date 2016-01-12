// Author: Aidan Fisher

import java.awt.*;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.PathIterator;
import java.awt.geom.QuadCurve2D;
import java.util.*;

public class Path {

	public final static int GO = 0; // Only found at non-intersections (and always)

	// The following are found at connections and intersections only.
	public final static int YIELD = 1; // Official yield
	public final static int STOP = 2; // Stop
	public final static int LIGHT_GO = 3; // Straight at lights
	public final static int LIGHT_YIELD = 4; // Left turns at lights
	public final static int GO_YIELD = 5; // When cars get stuck in intersection, this is required so the cars that don't have to yield still have to yield to cars who are stopped in intersection

	public int laneType = GO;
	public boolean lightGreen = false;

	public boolean isCurve = false;
	public RoadSegment segment;
	public ArrayList<Path> nextPath = new ArrayList<Path>();
	public int nextPathJoin, lastPathLeave;
	public ArrayList<Path> beforePath = new ArrayList<Path>(); // is null if no path before is "GO"
	public double[] x = new double[2];
	public double[] y = new double[2];
	public double arcX, arcY;
	public double distance;
	public double distFromArc;
	public double[] direction = new double[2];
	public double xAdd, yAdd;
	public ArrayList<Point.Double> path = new ArrayList<Point.Double>();
	public ArrayList<Double> rotPath = new ArrayList<Double>();
	public ArrayList<Car> cars = new ArrayList<Car>();
	public double[] arcAngle = new double[2];
	public ArrayList<Path> yieldTo = new ArrayList<Path>();
	public ArrayList<Path> sameLanePaths = null; // Merging lanes, and expanding roads. Also intersections where start or end location is the same.

	public Path(RoadSegment r, int offCenter, boolean forward) {
		setPathVariables(r, offCenter, forward);
		generatePath();
	}

	public Path(Connection c, RoadSegment r1, double dir1, RoadSegment r2, double dir2) {
		this.segment = c;
		this.direction[0] = dir1;
		this.direction[1] = dir2;
		isCurve = true; // All connections are curves.
	}

	// For changing lanes:
	public Path(Path nextPath, int nextPathJoin, int lastPathLeave, Car car, double x1, double y1, double x2, double y2) {
		this.nextPath.add(nextPath);
		this.nextPathJoin = nextPathJoin;
		this.lastPathLeave = lastPathLeave;
		cars.add(car);
		createPath(x1, y1, x2, y2);
	}

	public void createPath(double x1, double y1, double x2, double y2) {
		this.x[0] = x1;
		this.y[0] = y1;
		this.x[1] = x2;
		this.y[1] = y2;
		this.distance = distance(this.x[0], this.y[0], this.x[1], this.y[1]);
		generatePathManually();
	}

	public void createPath(RoadSegment r1, double x1, double y1, RoadSegment r2, double x2, double y2) {
		this.x[0] = x1;
		this.y[0] = y1;
		this.x[1] = x2;
		this.y[1] = y2;

		int toSwitchA = 1;
		int toSwitchB = 1;
		if (((Connection) segment).intersection != null) {
			Intersection intersection = (Intersection) ((Connection) segment).intersection;
			toSwitchA = Math.abs(intersection.roadSides.get(intersection.roads.indexOf(beforePath.get(0).segment)) - 1);
			toSwitchB = Math.abs(intersection.roadSides.get(intersection.roads.indexOf(nextPath.get(0).segment)) - 1);
		} else {
			if (!beforePath.get(0).segment.arc) {
				if (((Connection) segment).r1 == beforePath.get(0).segment) {
					toSwitchA = ((Connection) segment).s1;
				} else {
					toSwitchA = ((Connection) segment).s2;
				}
			}
			if (!nextPath.get(0).segment.arc) {
				if (((Connection) segment).r1 == nextPath.get(0).segment) {
					toSwitchB = ((Connection) segment).s1;
				} else {
					toSwitchB = ((Connection) segment).s2;
				}
			}
		}
		Shape curve = Connection.getCurve(direction[0] + toSwitchA * Math.PI, direction[1] + toSwitchB * Math.PI, new Point.Double(x1, y1), new Point.Double(x2, y2));
		generatePath(curve);
	}

	// As per definition, all must be declared again.
	public void setPathVariables(RoadSegment r, int offCenter, boolean forward) {
		this.segment = r;
		if (!segment.arc) {
			this.x[0] = segment.x[0] + segment.yC[0] * RoadSegment.laneSize * (offCenter / 2.0);
			this.y[0] = segment.y[0] + segment.xC[0] * RoadSegment.laneSize * (offCenter / 2.0);
			this.x[1] = segment.x[1] + segment.yC[0] * RoadSegment.laneSize * (offCenter / 2.0);
			this.y[1] = segment.y[1] + segment.xC[0] * RoadSegment.laneSize * (offCenter / 2.0);
			this.distance = distance(x[0], y[0], x[1], y[1]);
			this.direction[0] = segment.direction[0];
			this.xAdd = segment.xC[0];
			this.yAdd = segment.yC[0];
		} else {
			this.distFromArc = segment.arcDist + RoadSegment.laneSize * (offCenter / 2.0);
			this.arcAngle[0] = segment.arcAngle[0];
			this.arcAngle[1] = segment.arcAngle[1];
			this.x[0] = segment.arcX + Math.cos(-arcAngle[0]) * distFromArc;
			this.y[0] = segment.arcY + Math.sin(-arcAngle[0]) * distFromArc;
			this.x[1] = segment.arcX + Math.cos(-arcAngle[0] - arcAngle[1]) * distFromArc;
			this.y[1] = segment.arcY + Math.sin(-arcAngle[0] - arcAngle[1]) * distFromArc;
			this.distance = Math.abs(distFromArc * arcAngle[1]);
		}

		if (!forward) {
			double saveX = this.x[0];
			double saveY = this.y[0];
			this.x[0] = this.x[1];
			this.y[0] = this.y[1];
			this.x[1] = saveX;
			this.y[1] = saveY;
			if (segment.arc) {
				arcAngle[0] += arcAngle[1];
				arcAngle[1] = -arcAngle[1];
			} else {
				this.direction[0] = segment.direction[0] + Math.PI;
				this.xAdd = -segment.xC[0];
				this.yAdd = -segment.yC[0];
			}
		}
	}

	public double getX(boolean end) {
		if (end) {
			return x[1];
		} else {
			return x[0];
		}
	}

	public double getY(boolean end) {
		if (end) {
			return y[1];
		} else {
			return y[0];
		}
	}

	public void connectTo(Path path) {
		setNextPath(path);
		path.setBeforePath(this);
	}

	public void setNextPath(Path path) {
		if (!this.nextPath.contains(path)) {
			this.nextPath.add(path);
		}
		this.nextPathJoin = 0;
	}

	public void setBeforePath(Path path) {
		if (!this.beforePath.contains(path)) {
			this.beforePath.add(path);
		}
		this.lastPathLeave = path.path.size() - 1;
	}

	public static double distance(double x1, double y1, double x2, double y2) {
		return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
	}

	public static double distance(Point.Double a, Point.Double b) {
		return Math.sqrt(Math.pow(b.x - a.x, 2) + Math.pow(b.y - a.y, 2));
	}

	public boolean isYield() {
		if (laneType == YIELD) {
			return true;
		} else if (laneType == LIGHT_YIELD) {
			return lightGreen;
		} else {
			return false;
		}
	}

	public boolean isGoYield() {
		if (laneType == GO_YIELD) {
			return true;
		} else if (laneType == LIGHT_GO) {
			return lightGreen;
		} else {
			return false;
		}
	}

	public boolean isStopped() {
		if (laneType == LIGHT_YIELD || laneType == LIGHT_GO) {
			return !lightGreen;
		} else {
			return false;
		}
	}

	// Used for changing lanes
	public void generatePathManually() {
		this.direction[0] = Math.atan2(y[1] - y[0], x[1] - x[0]);
		this.xAdd = -Math.cos(direction[0]);
		this.yAdd = Math.sin(direction[0]);
		for (double nDist = 0; nDist < distance; nDist += RoadSegment.pointDistance) {
			path.add(new Point.Double(x[0] - xAdd * nDist, y[0] + yAdd * nDist));
		}
	}

	// Used for Road Segments
	public void generatePath() {
		// Reset the paths
		path = new ArrayList<Point.Double>();
		rotPath = new ArrayList<Double>();

		if (!segment.arc) {
			for (double nDist = 0; nDist < distance; nDist += RoadSegment.pointDistance) {
				path.add(new Point.Double(x[0] - xAdd * nDist, y[0] + yAdd * nDist));
			}
		} else {
			for (double nDist = 0; nDist < distance; nDist += RoadSegment.pointDistance) {
				path.add(new Point.Double(segment.arcX + Math.cos(-arcAngle[0] - (arcAngle[1] * (nDist / distance))) * distFromArc, segment.arcY
						+ Math.sin(-arcAngle[0] - (arcAngle[1] * (nDist / distance))) * distFromArc));
				if (arcAngle[1] > 0) {
					rotPath.add(-arcAngle[0] + Math.PI / 2 - arcAngle[1] * (nDist / distance));
				} else {
					rotPath.add(-arcAngle[0] - Math.PI / 2 - arcAngle[1] * (nDist / distance));
				}
			}
		}
	}

	// Used for connections and intersections
	public void generatePath(Shape curve) {
		PathIterator pit = curve.getPathIterator(null);
		pit = new FlatteningPathIterator(pit, 0.0000001, 100); // Raise limit from 10
		double[] coords = new double[2];
		double[] lastCoords = new double[2];
		double distanceLeft = RoadSegment.pointDistance;
		pit.currentSegment(coords);
		lastCoords[0] = coords[0];
		lastCoords[1] = coords[1];
		pit.next();
		while (!pit.isDone()) {
			pit.currentSegment(coords);

			distanceLeft -= Path.distance(lastCoords[0], lastCoords[1], coords[0], coords[1]);
			while (distanceLeft < 0) {
				distanceLeft += RoadSegment.pointDistance;
				path.add(new Point.Double(coords[0], coords[1]));
				rotPath.add(Math.atan2(coords[1] - lastCoords[1], coords[0] - lastCoords[0]));
			}

			lastCoords[0] = coords[0];
			lastCoords[1] = coords[1];
			pit.next();
		}
		this.distance = path.size() * RoadSegment.pointDistance;
	}

	public void render(Graphics2D g) {
		g.setStroke(new BasicStroke(3));
		g.setPaint(new GradientPaint((float) Component.toSX(x[0]), (float) Component.toSY(y[0]), new Color(20, 255, 50, 50), (float) Component.toSX(x[1]), (float) Component.toSY(y[1]), new Color(150,
				0, 0, 50)));
		g.drawLine((int) Component.toSX(x[0]), (int) Component.toSY(y[0]), (int) Component.toSX(x[1]), (int) Component.toSY(y[1]));
	}
}
