// Author: Aidan Fisher

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class Listening implements MouseListener, MouseWheelListener, KeyListener {

	// Potential Modes:

	// AUTO connect
	// Connection Mode
	// Different Mode

	public static Point.Double pressLocation = new Point.Double(-1, -1); //off screen = none
	public static Point.Double nextLocation = new Point.Double(-1, -1); //off screen = none
	public static Point.Double currLocation = new Point.Double(-1, -1); //off screen = none
	public static Point.Double lastLocation = new Point.Double(-1, -1); //off screen = none
	public static boolean mouseDown = false;
	public static boolean movingScreen = false;
	public static int snapDistance = 100; // Based on screen
	public static RoadSegment[] roadsSnapped = new RoadSegment[2];
	public static Intersection[] intersSnapped = new Intersection[2];
	public static double[] interDirections = new double[2];
	public static int[] roadsOnSide = new int[2];

	public static int[] roadsOnSideOfRoad = new int[2];
	public static final int NORMAL_ROAD = -1;

	public static boolean mouseHasClicked = false;
	public static boolean deleteMode = false;

	public static int[] lanes = { 2, 2 };

	public static void zoomIn(double zoomRate, MouseEvent e) {
		Component.screenX += e.getX() / Component.zoomLevel / (1.0 + zoomRate);
		Component.screenY += e.getY() / Component.zoomLevel / (1.0 + zoomRate);
		Component.zoomLevel = Component.zoomLevel * (1.0 + (1.0 / zoomRate));
	}

	public static void zoomOut(double zoomRate, MouseEvent e) {
		Component.screenX -= e.getX() / Component.zoomLevel * (1.0 / zoomRate);
		Component.screenY -= e.getY() / Component.zoomLevel * (1.0 / zoomRate);
		Component.zoomLevel = Component.zoomLevel / (1.0 + 1.0 / zoomRate);
	}

	public void mouseClicked(MouseEvent e) {

	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {

	}

	public void mousePressed(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON1) {
			mouseDown = true;
			pressLocation = Component.toPoint(e.getPoint()); //nothing happens
			// Building road logic:
			for (int i = 0; i < 2; i++) {
				roadsOnSideOfRoad[i] = NORMAL_ROAD;
			}
			pressLocation = snapToNearestRoad(pressLocation, 0);
		} else if (e.getButton() == MouseEvent.BUTTON3) {
			roadsSnapped[0] = null;
			roadsSnapped[1] = null;
			intersSnapped[0] = null;
			intersSnapped[1] = null;
			mouseDown = false;
			movingScreen = true;
		}
	}

	public static Point.Double snapToNearestRoad(Point.Double p, int x) {

		if (x == 1) {
			if (Path.distance(pressLocation, p) < snapDistance) {
				// Forces an intersection if "road" is shorter than snapDistance: (Prevents tiny roads)
				return pressLocation;
			}
		}

		double nearestDistance = snapDistance;
		Point.Double snapLoc = p;

		for (int i = 0; i < Component.intersections.size(); i++) {
			Intersection inter = Component.intersections.get(i);
			double distance = Path.distance(p.x, p.y, inter.x, inter.y);
			if (distance < nearestDistance + inter.radius) {
				if (distance < inter.radius) {
					nearestDistance = 0;
				} else {
					nearestDistance = distance - inter.radius;
				}

				double direction = Math.atan2(p.y - inter.y, p.x - inter.x);
				intersSnapped[x] = inter;
				interDirections[x] = direction;
				snapLoc = p;
				// Below maintains the intersection circle:
				//return new Point.Double(inter.x + Math.cos(direction) * inter.radius, inter.y + Math.sin(direction) * inter.radius);
			}
		}

		for (int i = 0; i < Component.roads.size(); i++) {
			RoadSegment road = Component.roads.get(i);

			for (int s = 0; s < 2; s++) { // side
				if (road.allPathsFree(s)) {
					// Allow user to connect to not just the yellow Line.
					if (Path.distance(p.x, p.y, road.x[s], road.y[s]) < nearestDistance) {
						roadsSnapped[x] = road;
						roadsOnSide[x] = s;
						roadsOnSideOfRoad[x] = NORMAL_ROAD;
						nearestDistance = Path.distance(p.x, p.y, road.x[s], road.y[s]);
						snapLoc = new Point.Double(road.x[s], road.y[s]);
					}
				} else {
					// Must be a one way road:
					for (int n = 0; n < 2; n++) {
						int line = road.getFreeRoadFinish(Math.abs(s - RoadAdding.opp(n)), n);
						if (line != -1) {
							//if (road.pathConnectionsRequired[s][0] != 0) {
							//int line = Math.abs(road.numPaths * Math.abs(s - 1) - road.pathConnectionsRequired[s][0]);
							Point.Double pathPoint = road.getGamePoint(s, line);
							if (Path.distance(p.x, p.y, pathPoint.x, pathPoint.y) < nearestDistance) {
								roadsSnapped[x] = road;
								roadsOnSide[x] = s;
								roadsOnSideOfRoad[x] = n;
								nearestDistance = Path.distance(p.x, p.y, pathPoint.x, pathPoint.y);
								snapLoc = pathPoint;
							}
						}
					}
				}

			}
		}
		return snapLoc;
	}

	public void mouseReleased(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON1 && mouseDown) {
			if (deleteMode) {
				nextLocation = Component.toPoint(e.getPoint());
			} else {
				nextLocation = snapToNearestRoad(Component.toPoint(e.getPoint()), 1);
			}
			mouseHasClicked = true;
		} else if (e.getButton() == MouseEvent.BUTTON3) {
			movingScreen = false;
		} else if (e.getButton() == MouseEvent.BUTTON2) {
			int r = new Random().nextInt(Component.roads.size());
			Component.cars.add(new Car(Component.roads.get(r).paths.get((int) (Math.random() * Component.roads.get(r).numPaths))));
		}
	}

	public void mouseWheelMoved(MouseWheelEvent e) {
		if (e.getWheelRotation() > 0) {
			Listening.zoomOut(5, e);
		} else if (e.getWheelRotation() < 0) {
			Listening.zoomIn(5, e);
		}
	}

	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_W) {
			lanes[0]++;
		} else if (e.getKeyCode() == KeyEvent.VK_S) {
			lanes[0]--;
			if (lanes[0] < 0) {
				lanes[0] = 0;
			}
		} else if (e.getKeyCode() == KeyEvent.VK_D) {
			lanes[1]++;
		} else if (e.getKeyCode() == KeyEvent.VK_A) {
			lanes[1]--;
			if (lanes[1] < 0) {
				lanes[1] = 0;
			}
		} else if (e.getKeyCode() == KeyEvent.VK_B) {
			deleteMode = !deleteMode;
		} else if (e.getKeyCode() == KeyEvent.VK_2) {
			Intersection.defaultRadius += 10;
		} else if (e.getKeyCode() == KeyEvent.VK_1) {
			Intersection.defaultRadius -= 10;
		}
	}

	public void keyReleased(KeyEvent e) {
	}

	public void keyTyped(KeyEvent e) {
	}
}
