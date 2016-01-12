// Author: Aidan Fisher

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;

// Roads are either straight line segments or circular arcs.

public class RoadSegment {

	// Types of road:
	public static final int NORMAL = 0;
	public static final int NO_YELLOW_LINE = 1;

	// Road Dimensions:
	public static double laneSize = 50;
	public static float lineSize = 3;
	public static float lineLength = 30;
	public static double pointDistance = 0.1; // Distance between the points contained in paths. A road of a shorter distance than this is a problem.
	public static double shoulderWidth = 30;
	public static int changeLaneDistance = (int) (100 / pointDistance);

	//public int[][] pathConnectionsRequired = new int[2][2];

	public double[] direction = new double[2];

	public boolean highlighted = false;
	public boolean exists;
	public boolean arc;

	// Change of x and y: (cos / sin)
	public double[] xC = new double[2];
	public double[] yC = new double[2];

	// Arc only:
	public double arcDist; // The radius of the arcs.
	public double[] arcAngle = new double[2]; // The start & end of the arc.
	public double arcX, arcY; // Mid point for the arc.

	public double[] x = new double[2];
	public double[] y = new double[2]; // The road "route" (middle of road: used to calculate bounds and paths)
	public ArrayList<Path> paths = new ArrayList<Path>();
	public int yellowLine; // Division between the directions of the road paths.
	public int numPaths = 0;

	public int roadType = NORMAL;

	public RoadSegment() {
	}

	public RoadSegment(double x1, double y1, double x2, double y2, int lanes1, int lanes2, boolean exists) {
		this.exists = exists;
		arc = false;
		this.x[0] = x1;
		this.y[0] = y1;
		this.x[1] = x2;
		this.y[1] = y2;

		direction[0] = Math.atan2(y2 - y1, x2 - x1);
		direction[1] = direction[0];
		xC[0] = -Math.cos(direction[0]);
		yC[0] = Math.sin(direction[0]);
		xC[1] = xC[0];
		yC[1] = yC[0];

		numPaths = lanes1 + lanes2;
		yellowLine = lanes1;

		if (exists) {
			setPaths();
		}
	}

	public RoadSegment(double x1, double y1, double x2, double y2, double d1, double d2, double arcX, double arcY, int lanes1, int lanes2, boolean exists) {
		this.exists = exists;
		arc = true;
		this.x[0] = x1;
		this.y[0] = y1;
		this.x[1] = x2;
		this.y[1] = y2;

		direction[0] = d1;
		direction[1] = d2;
		setC();

		// xC and yC are used for different terms as a curve:
		this.arcX = arcX;
		this.arcY = arcY;

		// ArcDist to be the same on both sides..
		arcDist = Path.distance(x1, y1, arcX, arcY);

		arcAngle[0] = -Math.atan2(y1 - arcY, x1 - arcX);
		arcAngle[1] = direction[0] - direction[1];

		numPaths = lanes1 + lanes2;
		yellowLine = lanes1;

		// Set arcDist to the middle: (After paths have been created)
		if (arcAngle[1] > 0) {
			arcDist += (yellowLine - numPaths / 2.0) * laneSize;
		} else {
			arcDist -= (yellowLine - numPaths / 2.0) * laneSize;
		}

		if (exists) {
			setPaths();
		}

	}

	/*public int getPathsOnSide(int side, int sideOnRoad) {
		if ((side == 0) == (sideOnRoad == 0)) {
			return numPaths - yellowLine;
		} else {
			return yellowLine;
		}
	}*/

	// Checks if this side of the road (s) is free of any connections whatsoever.
	public boolean allPathsFree(int s) {
		for (int path = 0; path < paths.size(); path++) {
			if ((path < yellowLine) == (s == 0)) {
				if (paths.get(path).beforePath.size() > 0) {
					return false;
				}
			} else {
				if (paths.get(path).nextPath.size() > 0) {
					return false;
				}
			}
		}
		return true;
	}

	// Checks the sides for how far the side has no path connections.
	public int getFreeRoadFinish(int s, int n) {
		if (n == 0) {
			for (int path = 0; path < paths.size(); path++) {
				if (path == yellowLine) {
					if (path == 0) {
						return -1;
					}
					return path;
				}
				if (s == 1) {
					if (paths.get(path).beforePath.size() > 0) {
						if (path == 0) {
							return -1;
						}
						return path;
					}
				} else {
					if (paths.get(path).nextPath.size() > 0) {
						if (path == 0) {
							return -1;
						}
						return path;
					}
				}
			}
			return paths.size();
		} else {
			for (int path = paths.size() - 1; path >= 0; path--) {
				if (path + 1 == yellowLine) {
					if (path + 1 == paths.size()) {
						return -1;
					}
					return path + 1;
				}
				if ((s == 1)) {
					if (paths.get(path).beforePath.size() > 0) {
						if (path == paths.size() - 1) {
							return -1;
						}
						return path + 1;
					}
				} else {
					if (paths.get(path).nextPath.size() > 0) {
						if (path == paths.size() - 1) {
							return -1;
						}
						return path + 1;
					}
				}
			}
			return 0;
		}
	}

	public void setC() {
		xC[0] = -Math.cos(direction[0]);
		yC[0] = Math.sin(direction[0]);
		xC[1] = -Math.cos(direction[1]);
		yC[1] = Math.sin(direction[1]);
	}

	public void setPaths() {

		int mid;
		if (arc) {
			mid = numPaths - 1;
		} else {
			mid = yellowLine * 2 - 1;
		}

		// Lanes are 2 apart.
		if (arcAngle[1] <= 0) {
			for (int i = 0; i < numPaths; i++) {
				if (i < yellowLine) {
					paths.add(new Path(this, i * 2 - mid, true));
				} else {
					paths.add(new Path(this, i * 2 - mid, false));
				}
			}
		} else {
			for (int i = numPaths - 1; i >= 0; i--) {
				if (i < numPaths - yellowLine) {
					paths.add(new Path(this, i * 2 - mid, false));
				} else {
					paths.add(new Path(this, i * 2 - mid, true));
				}
			}
		}
	}

	public void regeneratePaths() {
		for (int i = 0; i < numPaths; i++) {
			if (arcAngle[1] <= 0 && i < yellowLine || arcAngle[1] > 0 && i >= numPaths - yellowLine) {
				paths.get(i).setPathVariables(this, i * 2 - (numPaths - 1), true);
			} else {
				paths.get(i).setPathVariables(this, i * 2 - (numPaths - 1), false);
			}
			paths.get(i).generatePath();
		}
	}

	public void resetArcPositions() {
		direction[0] = RoadAdding.rationalizeNN(direction[0]);
		direction[1] = RoadAdding.rationalizeNN(direction[1]);
		setC();
		this.x[0] = arcX - yC[0] * arcDist;
		this.y[0] = arcY - xC[0] * arcDist;
		this.x[1] = arcX - yC[1] * arcDist;
		this.y[1] = arcY - xC[1] * arcDist;
	}

	public Point.Double getSidePoint(int side) {
		return new Point.Double(x[side], y[side]);
	}

	// Only for arc:
	public boolean isAngleOnRoad(double angle) {
		if (arcAngle[1] > 0) {
			if (angle < arcAngle[0]) {
				angle += Math.PI * 2;
			}
			return angle > arcAngle[0] && angle < arcAngle[0] + arcAngle[1];
		} else {
			if (angle > arcAngle[0]) {
				angle -= Math.PI * 2;
			}
			return angle < arcAngle[0] && angle > arcAngle[0] + arcAngle[1];
		}
	}

	// Remove all cars on road and expected to go on road: (Currently, cars do not repath their routes due to issues with yielding)
	public void removeCars(int path) {

		// Remove the cars ON the path:
		for (int j = 0; j < paths.get(path).cars.size(); j++) {
			Component.cars.remove(paths.get(path).cars.get(j));
			// No need to remove them from the path because the path is being deleted.
		}

		// Remove the cars who will be on the path:
		for (int j = 0; j < Component.cars.size(); j++) {
			for (Path nPath : Component.cars.get(j).nextPath) {
				if (this == nPath.segment) {
					// In this case, the cars must be removed from their path:
					Component.cars.get(j).onPath.cars.remove(Component.cars.get(j));
					Component.cars.remove(j);
					break;
				}
			}
		}
	}

	public void checkForAvailablePaths() {

	}

	public void removePathConnections(int path) {
		// Remove path connections
		for (int j = 0; j < Component.roads.size(); j++) {
			for (int k = 0; k < Component.roads.get(j).paths.size(); k++) {
				Component.roads.get(j).paths.get(k).yieldTo.remove(paths.get(path));
				Component.roads.get(j).paths.get(k).nextPath.remove(paths.get(path));
				Component.roads.get(j).paths.get(k).beforePath.remove(paths.get(path));
			}
			Component.roads.get(j).checkForAvailablePaths();
		}
	}

	// Must be called on TICK / RENDER (not listening)
	public void deleteRoad() {
		Component.roads.remove(this);
		this.exists = false;

		// Remove intersections:
		for (int j = 0; j < Component.intersections.size(); j++) {
			if (Component.intersections.get(j).roads.contains(this)) {
				Component.intersections.get(j).removeRoad(this);
			}
		}

		// Remove [Connections]:
		for (int j = 0; j < Component.connections.size(); j++) {
			if (this == Component.connections.get(j).r1 || this == Component.connections.get(j).r2) {
				// Connections must be removed as they rely on 2 roads.
				Component.connections.get(j).deleteRoad();
			}
		}

		// Remove all cars ON the road:
		for (int i = 0; i < paths.size(); i++) {
			removeCars(i);
			removePathConnections(i);
		}
	}

	public int getYellowLine() {
		if (arcAngle[1] > 0) {
			return numPaths - yellowLine;
		} else {
			return yellowLine;
		}
	}

	private double getMiddleX(int side) {
		return x[side] + yC[side] * (numPaths / 2.0 - yellowLine) * laneSize;
	}

	private double getMiddleY(int side) {
		return y[side] + xC[side] * (numPaths / 2.0 - yellowLine) * laneSize;
	}

	public double getBounds(int side, int corner, boolean isX) {
		if (corner == 0) {
			if (isX) {
				return Component.toSX(getMiddleX(side) + yC[side] * (laneSize * numPaths / 2.0 + shoulderWidth / 2.0));
			} else
				return Component.toSY(getMiddleY(side) + xC[side] * (laneSize * numPaths / 2.0 + shoulderWidth / 2.0));
		} else {
			if (isX)
				return Component.toSX(getMiddleX(side) - yC[side] * (laneSize * numPaths / 2.0 + shoulderWidth / 2.0));
			else
				return Component.toSY(getMiddleY(side) - xC[side] * (laneSize * numPaths / 2.0 + shoulderWidth / 2.0));
		}
	}

	public Color getRoadColor() {
		if (highlighted) {
			return new Color(170, 170, 170);
		} else if (exists) {
			return new Color(150, 150, 150);
		} else {
			return new Color(150, 150, 150, 128);
		}
	}

	public void renderRoad(Graphics2D g) {

		BasicStroke basicStroke = new BasicStroke((float) ((laneSize * numPaths + shoulderWidth) * Component.zoomLevel), BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
		g.setColor(getRoadColor());
		if (arc) {
			Arc2D.Double arcD = new Arc2D.Double(Component.toSX(arcX) - (arcDist * Component.zoomLevel), Component.toSY(arcY) - (arcDist * Component.zoomLevel),
					(int) (arcDist * 2 * Component.zoomLevel), (arcDist * 2 * Component.zoomLevel), (arcAngle[0] * (360.0 / Math.PI / 2)), (arcAngle[1] * (360.0 / Math.PI / 2)), Arc2D.OPEN);
			g.setStroke(new ExtendedStroke(basicStroke, 2));
			g.draw(arcD);
			g.setStroke(basicStroke);
			g.draw(arcD);
		} else {
			g.setStroke(basicStroke);
			Polygon2D p = new Polygon2D();
			p.addPoint(getBounds(0, 0, true), getBounds(0, 0, false));
			p.addPoint(getBounds(1, 0, true), getBounds(1, 0, false));
			p.addPoint(getBounds(1, 1, true), getBounds(1, 1, false));
			p.addPoint(getBounds(0, 1, true), getBounds(0, 1, false));
			g.fill(p);
			g.setStroke(new ExtendedStroke(p, 2));
			g.draw(p);

		}

		highlighted = false;
	}

	public void renderLines(Graphics2D g) {
		// Draw the lines:
		for (int i = 0; i <= numPaths; i++) {
			drawLine(g, i, arcAngle[1] <= 0);
		}

	}

	public Point.Double getGamePoint(int side, double line) {
		// In GAME situation, arcAngle should *NOT* be used in calculations
		if (arcAngle[1] > 0) {
			line += ((numPaths - yellowLine) - yellowLine);
		}
		return new Point.Double(x[side] + yC[side] * laneSize * (line - getYellowLine()), y[side] + xC[side] * laneSize * (line - getYellowLine()));
	}

	public Point.Double getPoint(int side, double line) {
		// When rendering, arcAngle *MUST* be considered elsewhere
		if (arcAngle[1] > 0) {
			line += ((numPaths - yellowLine) - yellowLine);
		}
		return new Point.Double(Component.toSX(x[side] + yC[side] * laneSize * (line - getYellowLine())), Component.toSY(y[side] + xC[side] * laneSize * (line - getYellowLine())));
	}

	public void drawLine(Graphics2D g, int i, boolean way) {

		// Set line:
		int n = i;
		if (!way) {
			n = numPaths - i;
		}
		if (n == yellowLine) {
			g.setStroke(new BasicStroke((float) (lineSize * Component.zoomLevel), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
			g.setColor(new Color(255, 255, 0));
			if (roadType == NO_YELLOW_LINE) {
				g.setColor(new Color(255, 255, 255));
			}
		} else if (n == 0 || n == numPaths) {
			g.setStroke(new BasicStroke((float) (lineSize * Component.zoomLevel), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
			g.setColor(new Color(255, 255, 255));
		} else {
			float dash[] = { (float) (lineLength * Component.zoomLevel) };
			g.setStroke(new BasicStroke((float) (lineSize * Component.zoomLevel), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL, (float) (lineLength * Component.zoomLevel), dash, 0.0f));
			g.setColor(new Color(255, 255, 255));
		}

		// Draw line:
		if (!arc) {
			Line2D line = new Line2D.Double(getPoint(0, i), getPoint(1, i));
			g.draw(line);
		} else {
			double arcDist = this.arcDist + (laneSize * (i - numPaths / 2.0));
			Arc2D.Double arcD = new Arc2D.Double(Component.toSX(arcX) - (arcDist * Component.zoomLevel), Component.toSY(arcY) - (arcDist * Component.zoomLevel), (arcDist * 2 * Component.zoomLevel),
					(arcDist * 2 * Component.zoomLevel), (arcAngle[0] * (360.0 / Math.PI / 2)), (arcAngle[1] * (360.0 / Math.PI / 2)), Arc2D.OPEN);
			if (arcD.width < 500000) { // Max radius
				g.draw(arcD);
			}
		}
	}
}
