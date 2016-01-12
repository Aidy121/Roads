// Author: Aidan Fisher

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;

public class Intersection extends RoadSegment {

	public static int STOP_SIGN = 0; // Everyone has to stop. Then a car can go, and any that don't yield to that car can go as well.
	public static int STOP_LIGHT = 1;
	public static int YIELD_GO = 2;
	public static int STOP_GO = 3;

	public int intersectionType = YIELD_GO;
	public Connection priorityConnection = null; // For Yield / Stop & Go

	public static int defaultRadius = 200;

	public ArrayList<RoadSegment> roads = new ArrayList<RoadSegment>();
	public ArrayList<Integer> roadSides = new ArrayList<Integer>();
	public ArrayList<Double> directions = new ArrayList<Double>();

	public ArrayList<Connection> connections = new ArrayList<Connection>(); // For example, (3 roads): 1 - 2, then 1 - 3.. then 2 - 3
	public int numRoads;
	public double x, y;

	public int lightTimer = 0;
	public int roadSetGreen = 0;
	public int[] roadOrder = null;
	public int totalLightTime = 600;

	public ArrayList<Shape> lastRenderCurve = null;

	public double radius;

	public Intersection(double x, double y, boolean exists) {
		this.x = x;
		this.y = y;
		this.radius = defaultRadius;
		numRoads = 0;
		this.exists = exists;
	}

	public void tick() {
		if (intersectionType == STOP_LIGHT) {
			lightTimer++;
			if (lightTimer >= totalLightTime) {
				lightTimer = 0;

				for (int i = 0; i < connections.size(); i++) {
					for (int j = 0; j < connections.get(i).numPaths; j++) {
						Path path = connections.get(i).paths.get(j);
						if (path.beforePath.get(0).segment == roads.get(roadOrder[roadSetGreen]) || roadSetGreen + 1 < numRoads
								&& path.beforePath.get(0).segment == roads.get(roadOrder[roadSetGreen + 1])) {
							path.lightGreen = false;
						}
					}
				}

				roadSetGreen += 2;
				if (roadSetGreen >= numRoads) {
					roadSetGreen = 0;
				}

				for (int i = 0; i < connections.size(); i++) {
					for (int j = 0; j < connections.get(i).numPaths; j++) {
						Path path = connections.get(i).paths.get(j);
						if (path.beforePath.get(0).segment == roads.get(roadOrder[roadSetGreen]) || roadSetGreen + 1 < numRoads
								&& path.beforePath.get(0).segment == roads.get(roadOrder[roadSetGreen + 1])) {
							path.lightGreen = true;
						}
					}
				}

			}
		}
	}

	public void addRoad(RoadSegment r, double direction, boolean side) {
		int addIndex = getAddIndex(direction);
		roads.add(addIndex, r);
		int s = 0;
		if (side)
			s = 1;
		roadSides.add(addIndex, s);
		directions.add(addIndex, direction);

		numRoads++;

		clearConnectionPaths();

		createConnectionPaths();

	}

	public void removeRoad(RoadSegment r) {

		int index = roads.indexOf(r);

		roads.remove(index);
		roadSides.remove(index);
		directions.remove(index);

		numRoads--;

		clearConnectionPaths();

		createConnectionPaths();
	}

	public int getAddIndex(double direction) {
		for (int i = 0; i < roads.size(); i++) {
			if (direction < directions.get(i)) {
				return i;
			}
		}
		return roads.size();
	}

	public void createConnections() {
		roadOrder = new int[numRoads];
		int currIndex = 0;

		boolean isOdd = numRoads / 2 * 2 != numRoads;
		Connection bestDir = null; // No straight for 5 / 7 ways yet.
		double bestDirection = 0;
		for (int i = 0; i < numRoads - 1; i++) {
			// Connect'em up
			for (int j = i + 1; j < numRoads; j++) {
				Connection c = new Connection(roads.get(i), roadSides.get(i) == 1, roads.get(j), roadSides.get(j) == 1, this, true);
				connections.add(c);
				// j > i

				if (j * 2 < i * 2 + numRoads) {
					c.type = Connection.RIGHT;
				} else if (j * 2 > i * 2 + numRoads) {
					c.type = Connection.LEFT;
				} else {
					// Equal to:
					c.type = Connection.STRAIGHT;

					roadOrder[currIndex++] = i;
					roadOrder[currIndex++] = j;
				}

				if (isOdd) {
					if (bestDir == null) {
						bestDir = c;
						bestDirection = Math.abs(Math.abs(directions.get(i) - directions.get(j)) - Math.PI);
					} else {
						if (Math.abs(Math.abs(directions.get(i) - directions.get(j)) - Math.PI) < bestDirection) {
							bestDir = c;
							bestDirection = Math.abs(Math.abs(directions.get(i) - directions.get(j)) - Math.PI);
						}
					}
				}
			}
		}

		if (isOdd && numRoads >= 3) {
			bestDir.type = Connection.STRAIGHT;
			roadOrder[currIndex++] = roads.indexOf(bestDir.r1);
			roadOrder[currIndex++] = roads.indexOf(bestDir.r2);

			for (int i = 0; i < numRoads; i++) {
				if (roads.get(i) != bestDir.r1 && roads.get(i) != bestDir.r2) {
					roadOrder[currIndex] = i;
				}
			}
		}
	}

	public void setYields() {
		for (int i = 0; i < connections.size(); i++) {
			for (int j = 0; j < connections.get(i).paths.size(); j++) {
				Path path = connections.get(i).paths.get(j);
				if (path.sameLanePaths == null) {
					path.sameLanePaths = new ArrayList<Path>();
					path.sameLanePaths.add(path);
				}

				for (int k = 0; k < connections.size(); k++) {
					for (int l = 0; l < connections.get(k).paths.size(); l++) {
						Path otherPath = connections.get(k).paths.get(l);

						yieldRules((Connection) path.segment, path, (Connection) otherPath.segment, otherPath);

					}
				}
			}
		}
	}

	public void createConnectionPaths() {
		connections = new ArrayList<Connection>(); // Recreate the connections.

		createConnections();
		setRightAndLeftTurnLanes();
		setYields();

	}

	public void setPathTypes(Connection c, Path p) {
		if (intersectionType == STOP_SIGN) {
			p.laneType = Path.STOP;
		} else if (intersectionType == STOP_LIGHT) {
			if (c.type == Connection.RIGHT) {
				p.laneType = Path.GO_YIELD;
			} else if (c.type == Connection.STRAIGHT) {
				p.laneType = Path.LIGHT_YIELD;
			} else if (c.type == Connection.LEFT) {
				p.laneType = Path.LIGHT_YIELD;
			}
		} else if (intersectionType == YIELD_GO) {
			p.laneType = Path.YIELD;
		}
	}

	public void setRightAndLeftTurnLanes() {
		int connectionIndex = 0;
		for (int i = 0; i < numRoads - 1; i++) {
			for (int j = i + 1; j < numRoads; j++) {
				Connection c = connections.get(connectionIndex);
				int pathsUsed = 0;
				if (numRoads >= 3) {
					if (c.type == Connection.RIGHT) {
						pathsUsed = -1;
					} else if (c.type == Connection.LEFT) {
						pathsUsed = 1;
					}
					for (int n = 0; n < c.paths.size(); n++) {
						setPathTypes(c, c.paths.get(n));
					}
				}
				int rightSide1 = roads.get(i).yellowLine - 1 + roadSides.get(i);
				int leftSide1 = roads.get(i).yellowLine - roadSides.get(i);
				int farRight1 = (roads.get(i).numPaths - 1) * roadSides.get(i);
				int farLeft1 = (roads.get(i).numPaths - 1) * -(roadSides.get(i) - 1);

				int rightSide2 = roads.get(j).yellowLine - 1 + roadSides.get(j);
				int leftSide2 = roads.get(j).yellowLine - roadSides.get(j);
				int farRight2 = (roads.get(j).numPaths - 1) * roadSides.get(j);
				int farLeft2 = (roads.get(j).numPaths - 1) * -(roadSides.get(j) - 1);

				if (pathsUsed == 1) {
					for (int p = 0; p < c.paths.size(); p++) {

						Path beforePath = c.paths.get(p).beforePath.get(0);
						if (beforePath.segment == roads.get(i)) {
							if (beforePath.segment.paths.indexOf(beforePath) != leftSide1 && beforePath.segment.paths.indexOf(beforePath) != farRight1) {
								beforePath.nextPath.remove(c.paths.get(p));
							}
						} else {
							if (beforePath.segment.paths.indexOf(beforePath) != rightSide2 && beforePath.segment.paths.indexOf(beforePath) != farLeft2) {
								beforePath.nextPath.remove(c.paths.get(p));
							}
						}
					}
				} else if (pathsUsed == -1) {
					for (int p = 0; p < c.paths.size(); p++) {

						Path beforePath = c.paths.get(p).beforePath.get(0);
						if (beforePath.segment == roads.get(i)) {
							if (beforePath.segment.paths.indexOf(beforePath) != rightSide1 && beforePath.segment.paths.indexOf(beforePath) != farLeft1) {
								beforePath.nextPath.remove(c.paths.get(p));
							}
						} else {
							if (beforePath.segment.paths.indexOf(beforePath) != leftSide2 && beforePath.segment.paths.indexOf(beforePath) != farRight2) {
								beforePath.nextPath.remove(c.paths.get(p));
							}
						}
					}
				}
				connectionIndex++;
			}
		}
	}

	public void yieldRules(Connection route, Path path, Connection otherRoute, Path otherPath) {
		if (path == otherPath) {
			return;
		}
		int side1 = roadSides.get(roads.indexOf(route.r1));
		int side2 = roadSides.get(roads.indexOf(route.r2));

		if (route == otherRoute) {
			boolean left1 = leftOf(route.r1, path, otherPath, side1);
			boolean left2 = leftOf(route.r2, path, otherPath, side2);

			boolean right1 = rightOf(route.r1, path, otherPath, side1);
			boolean right2 = rightOf(route.r2, path, otherPath, side2);

			if (left1 && left2 || right1 && right2) {
				// Create yield for path:
				System.out.println("Not possible");
			}
			return;
		}

		if (route.r1 == otherRoute.r1 || route.r1 == otherRoute.r2 || route.r2 == otherRoute.r1 || route.r2 == otherRoute.r2) {
			int theSide;
			RoadSegment sameRoad;
			RoadSegment otherRoad[];
			if (route.r1 == otherRoute.r1 || route.r1 == otherRoute.r2) {
				sameRoad = route.r1;
				theSide = side1;
				if (route.r1 == otherRoute.r1) {
					otherRoad = new RoadSegment[] { route.r2, otherRoute.r2 };
				} else {
					otherRoad = new RoadSegment[] { route.r2, otherRoute.r1 };
				}
			} else {
				sameRoad = route.r2;
				theSide = side2;
				if (route.r2 == otherRoute.r1) {
					otherRoad = new RoadSegment[] { route.r1, otherRoute.r2 };
				} else {
					otherRoad = new RoadSegment[] { route.r1, otherRoute.r1 };
				}
			}

			if (sameLane(sameRoad, path, otherPath, theSide)) {
				path.sameLanePaths.add(otherPath);
				//path.laneType = Path.GO_YIELD;
				return;
			}

			boolean inside = insideOf(sameRoad, otherRoad[0], otherRoad[1]);

			if (inside && leftOf(sameRoad, path, otherPath, theSide)) {
				path.yieldTo.add(otherPath);
				//path.laneType = Path.GO_YIELD;
			} else if (!inside && rightOf(sameRoad, path, otherPath, theSide)) {
				path.yieldTo.add(otherPath);
				//path.laneType = Path.GO_YIELD;
			}
		} else {
			if (insideOf(route.r1, route.r2, otherRoute.r1) != insideOf(route.r1, route.r2, otherRoute.r2)) {
				path.yieldTo.add(otherPath);
				//path.laneType = Path.GO_YIELD;
			}
		}
	}

	// Inside = right of the FROM path, to the to path.
	public boolean insideOf(RoadSegment fromRoad, RoadSegment toRoad, RoadSegment checkRoad) {
		double direction = directions.get(roads.indexOf(checkRoad));
		double d1 = directions.get(roads.indexOf(fromRoad)), d2 = directions.get(roads.indexOf(toRoad));

		if (d1 > d2) {
			return direction < d1 && direction > d2;
		} else {
			return direction < d1 || direction > d2;
		}
	}

	public boolean leftOf(RoadSegment r, Path path, Path otherPath, int side) {
		if (r.paths.contains(path.beforePath.get(0))) {
			path = path.beforePath.get(0);
		} else {
			path = path.nextPath.get(0);
		}
		if (r.paths.contains(otherPath.beforePath.get(0))) {
			otherPath = otherPath.beforePath.get(0);
		} else {
			otherPath = otherPath.nextPath.get(0);
		}
		if (side == 0) {
			return r.paths.indexOf(path) < r.paths.indexOf(otherPath);
		} else {
			return r.paths.indexOf(path) > r.paths.indexOf(otherPath);
		}
	}

	public boolean rightOf(RoadSegment r, Path path, Path otherPath, int side) {
		if (r.paths.contains(path.beforePath.get(0))) {
			path = path.beforePath.get(0);
		} else {
			path = path.nextPath.get(0);
		}
		if (r.paths.contains(otherPath.beforePath.get(0))) {
			otherPath = otherPath.beforePath.get(0);
		} else {
			otherPath = otherPath.nextPath.get(0);
		}
		if (side == 0) {
			return r.paths.indexOf(path) > r.paths.indexOf(otherPath);
		} else {
			return r.paths.indexOf(path) < r.paths.indexOf(otherPath);
		}
	}

	public boolean roadIsRightMost(double dir1, double dir2) {
		double closest = dir1;
		for (int i = 0; i < directions.size(); i++) {
			if (directions.get(i) > dir1 && closest <= dir1) {
				closest = Double.MAX_VALUE;
			}
			if (directions.get(i) < dir1 && directions.get(i) < closest && closest <= dir1 || directions.get(i) > dir1 && directions.get(i) < closest) {
				closest = directions.get(i);
			}
		}
		return closest == dir2;
	}

	public void clearConnectionPaths() {
		for (int i = 0; i < connections.size(); i++) {
			for (int j = 0; j < connections.get(i).paths.size(); j++) {
				for (int k = 0; k < connections.get(i).paths.get(j).cars.size(); k++) {
					Component.cars.remove(connections.get(i).paths.get(j).cars.get(k));
				}

				connections.get(i).paths.get(j).beforePath.get(0).nextPath.remove(connections.get(i).paths.get(j));
				connections.get(i).paths.get(j).nextPath.get(0).beforePath.remove(connections.get(i).paths.get(j));
			}
		}
	}

	public void deleteRoad() {
		for (int i = 0; i < numRoads;) {
			removeRoad(roads.get(i));
		}
		Component.intersections.remove(this);
		this.exists = false;
	}

	public boolean sameLane(RoadSegment r, Path path, Path otherPath, int side) {
		if (r.paths.contains(path.beforePath.get(0))) {
			path = path.beforePath.get(0);
		} else {
			path = path.nextPath.get(0);
		}
		if (r.paths.contains(otherPath.beforePath.get(0))) {
			otherPath = otherPath.beforePath.get(0);
		} else {
			otherPath = otherPath.nextPath.get(0);
		}
		return r.paths.indexOf(path) == r.paths.indexOf(otherPath);
	}

	public void renderRoad(Graphics2D g) {

		if (numRoads >= 2) {

			Polygon2D polygon = new Polygon2D();
			ArrayList<Shape> curves = new ArrayList<Shape>();

			for (int i = 0; i < roads.size(); i++) {
				int j = i + 1;
				if (j == roads.size()) {
					j = 0;
				}

				int farRight = (roads.get(i).numPaths) * Math.abs(roadSides.get(i) - 1);
				int farLeft = (roads.get(j).numPaths) * roadSides.get(j);

				Point.Double a = roads.get(i).getPoint(Math.abs(roadSides.get(i) - 1), farRight);
				Point.Double b = roads.get(j).getPoint(Math.abs(roadSides.get(j) - 1), farLeft);

				Shape curve = Connection.getCurve(directions.get(i), directions.get(j), a, b);

				curves.add(curve);

				PathIterator pit = curve.getPathIterator(null, 1);
				double[] coords = new double[2];
				while (!pit.isDone()) {
					pit.currentSegment(coords);
					polygon.addPoint(coords[0], coords[1]);
					pit.next();
				}

			}

			g.setColor(getRoadColor());
			g.fill(polygon);
			g.setStroke(new ExtendedStroke(polygon, (int) shoulderWidth + 2));
			g.draw(polygon);

			lastRenderCurve = curves;
		} else {

			g.setColor(getRoadColor());
			g.fillOval((int) Component.toSX(x - radius), (int) Component.toSY(y - radius), (int) (radius * 2 * Component.zoomLevel), (int) (radius * 2 * Component.zoomLevel));

			lastRenderCurve = null;
		}
		highlighted = false;
	}

	public void renderLines(Graphics2D g) {
		if (lastRenderCurve != null) {
			g.setStroke(new BasicStroke((float) (lineSize * Component.zoomLevel), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
			g.setColor(new Color(255, 255, 255));
			for (int i = 0; i < lastRenderCurve.size(); i++) {
				g.draw(lastRenderCurve.get(i));
			}
		}
	}
}
