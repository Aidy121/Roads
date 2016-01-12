// Author: Aidan Fisher

import javax.imageio.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.applet.*;
import java.io.*;
import java.util.*;

public class Component extends Applet implements Runnable {
	private static final long serialVersionUID = 1L;
	boolean isRunning = false;
	public static double ticksPerSecond = 60;
	public static Point screenPos = new Point(0, 0);
	public static Point mousePos = new Point(0, 0);
	public Image screen;
	public static Component component;

	public static int screenW = 1200;
	public static int screenH = 800;

	public static double screenX = 0;
	public static double screenY = 0;
	public static double zoomLevel = 1;

	public static ArrayList<RoadSegment> roads = new ArrayList<RoadSegment>();
	public static ArrayList<Connection> connections = new ArrayList<Connection>();
	public static ArrayList<Intersection> intersections = new ArrayList<Intersection>();

	public static ArrayList<Car> cars = new ArrayList<Car>();

	public Component() {
		setPreferredSize(new Dimension(screenW, screenH));
		addKeyListener(new Listening());
		addMouseListener(new Listening());
		addMouseWheelListener(new Listening());
		try {
		} catch (Exception e) {
		}

	}

	// Checks if "moving screen" so should be called every tick.
	public static void moveScreen(Point.Double curr, Point.Double last) {
		if (Listening.movingScreen) {
			screenX += (last.x - curr.x) / Component.zoomLevel;
			screenY += (last.y - curr.y) / Component.zoomLevel;
		}
	}

	public void start() {
		// Starts game thread
		isRunning = true;
		new Thread(this).start();
	}

	public void stop() {
		isRunning = false;
	}

	public static void main(String args[]) {
		Component component = new Component();
		JFrame frame = new JFrame();
		frame.add(component);
		frame.setTitle("Road Network");
		frame.setResizable(false);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		frame.pack();
		frame.setLocationRelativeTo(null);
		Component.component = component;
		component.start();

	}

	public static double toSX(double x) {
		return ((x - Component.screenX) * Component.zoomLevel);
	}

	public static double toSY(double y) {
		return ((y - Component.screenY) * Component.zoomLevel);
	}

	public static double toX(int x) {
		return (x / Component.zoomLevel) + Component.screenX;
	}

	public static double toY(int y) {
		return (y / Component.zoomLevel) + Component.screenY;
	}

	public static Point.Double toPoint(Point p) {
		return new Point.Double(p.x / Component.zoomLevel + Component.screenX, p.y / Component.zoomLevel + Component.screenY);
	}

	public RoadSegment getRoadAtPoint(Point.Double p) {
		for (int i = 0; i < roads.size(); i++) {
			if (!roads.get(i).arc) {
				Point.Double a = new Point.Double(roads.get(i).x[0], roads.get(i).y[0]);
				Point.Double b = new Point.Double(roads.get(i).x[1], roads.get(i).y[1]);
				Point.Double inter = RoadAdding.getPointOfIntersection(roads.get(i).direction[0], roads.get(i).direction[0] + Math.PI / 2, a, p, false);

				if (!Connection.isOnLine(roads.get(i).direction[0], inter, a) && Connection.isOnLine(roads.get(i).direction[0], inter, b)) {
					if (Path.distance(inter, p) < (RoadSegment.laneSize * roads.get(i).numPaths + RoadSegment.shoulderWidth) / 2.0) {
						return roads.get(i);
					}
				}
			} else {
				double distance = Path.distance(roads.get(i).arcX, roads.get(i).arcY, p.x, p.y);
				double angle = RoadAdding.rationalizeNN(-Math.atan2(roads.get(i).arcY - p.y, roads.get(i).arcX - p.x) + Math.PI);

				if (distance < roads.get(i).arcDist + (RoadSegment.laneSize * roads.get(i).numPaths + RoadSegment.shoulderWidth) / 2.0
						&& distance > roads.get(i).arcDist - (RoadSegment.laneSize * roads.get(i).numPaths + RoadSegment.shoulderWidth) / 2.0 && roads.get(i).isAngleOnRoad(angle)) {
					return roads.get(i);
				}
			}
		}
		for (int i = 0; i < intersections.size(); i++) {
			if (Path.distance(intersections.get(i).x, intersections.get(i).y, p.x, p.y) < intersections.get(i).radius) {
				return intersections.get(i);
			}
		}
		return null;
	}

	public void tick() {
		for (int i = 0; i < cars.size(); i++) {
			cars.get(i).tick();
		}
		for (int i = 0; i < intersections.size(); i++) {
			intersections.get(i).tick();
		}

		if (Listening.mouseHasClicked) {
			if (Listening.deleteMode) {
				RoadSegment r = getRoadAtPoint(Listening.nextLocation);
				if (r != null) {
					r.deleteRoad();
				}
			} else {
				RoadAdding.buildRoad(Listening.pressLocation, Listening.nextLocation, Listening.roadsSnapped[0], Listening.roadsSnapped[1], Listening.intersSnapped[0], Listening.intersSnapped[1],
						Listening.roadsOnSide[0] == 1, Listening.roadsOnSide[1] == 1, Listening.roadsOnSideOfRoad[0], Listening.roadsOnSideOfRoad[1], true);
			}
			Listening.roadsSnapped[0] = null;
			Listening.roadsSnapped[1] = null;
			Listening.intersSnapped[0] = null;
			Listening.intersSnapped[1] = null;
			Listening.mouseHasClicked = false;
			Listening.mouseDown = false;
			Listening.pressLocation = new Point.Double(-1, -1);
		}
	}

	public void render() {
		((VolatileImage) screen).validate(getGraphicsConfiguration());
		Graphics g = screen.getGraphics();
		Graphics2D g2 = (Graphics2D) g;
		screenPos = getLocationOnScreen();
		mousePos = getMousePosition();
		Listening.currLocation = new Point.Double(MouseInfo.getPointerInfo().getLocation().x - Component.screenPos.x, MouseInfo.getPointerInfo().getLocation().y - Component.screenPos.y);
		moveScreen(Listening.currLocation, Listening.lastLocation);
		Listening.lastLocation = Listening.currLocation;
		//draw:
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0, 150, 50));
		g.fillRect(0, 0, screenW, screenH);

		for (int i = 0; i < intersections.size(); i++) {
			intersections.get(i).renderRoad(g2);
		}
		for (int i = 0; i < connections.size(); i++) {
			connections.get(i).renderRoad(g2);
		}

		// Render the road base first.
		for (int i = 0; i < roads.size(); i++) {
			roads.get(i).renderRoad(g2);
		}

		for (int i = 0; i < intersections.size(); i++) {
			intersections.get(i).renderLines(g2);
		}
		for (int i = 0; i < connections.size(); i++) {
			connections.get(i).renderLines(g2);
		}

		// Render all the lines of the roads on top of the road base.
		for (int i = 0; i < roads.size(); i++) {
			roads.get(i).renderLines(g2);
		}

		// Cars are always on top
		for (int i = 0; i < cars.size(); i++) {
			cars.get(i).render(g2, null);
		}

		if (Listening.deleteMode && mousePos != null) {
			RoadSegment r = getRoadAtPoint(Component.toPoint(mousePos));
			if (r != null) {
				r.highlighted = true;
			}
		} else if (Listening.mouseDown && mousePos != null && !Listening.mouseHasClicked) {
			// Renders the fake road that shows what will be built.
			Point.Double nextLocation = Listening.snapToNearestRoad(Component.toPoint(mousePos), 1);
			ArrayList<RoadSegment> r = RoadAdding.buildRoad(Listening.pressLocation, nextLocation, Listening.roadsSnapped[0], Listening.roadsSnapped[1], Listening.intersSnapped[0],
					Listening.intersSnapped[1], Listening.roadsOnSide[0] == 1, Listening.roadsOnSide[1] == 1, Listening.roadsOnSideOfRoad[0], Listening.roadsOnSideOfRoad[1], false);
			Listening.roadsSnapped[1] = null;
			Listening.intersSnapped[1] = null;
			for (int i = 0; i < r.size(); i++) {
				r.get(i).renderRoad(g2);
			}

			for (int i = 0; i < r.size(); i++) {
				r.get(i).renderLines(g2);
			}
		}

		g = getGraphics();
		g.drawImage(screen, 0, 0, screenW, screenH, 0, 0, screenW, screenH, null);
		g.dispose();
	}

	public void run() {
		screen = createVolatileImage(screenW, screenH);
		long lastTime = System.nanoTime();
		double unprocessed = 0;
		double nsPerTick = 1000000000.0 / /*Just in case*/ticksPerSecond;
		//int frames = 0;
		//int ticks = 0;
		while (isRunning) {
			if (Listening.movingScreen) {
				Listening.movingScreen = false;
				lastTime = System.nanoTime();
				unprocessed = 0;
			}
			nsPerTick = 1000000000.0 / /*Just in case*/ticksPerSecond;
			long now = System.nanoTime();
			unprocessed += (now - lastTime) / nsPerTick;
			lastTime = now;
			while (unprocessed >= 1) {
				tick();
				render(); // Caps at tickrate. Render avoids conflicting with tick.
				unprocessed -= 1;
			}
			{
				if (unprocessed < 1) {
					try {
						Thread.sleep((int) ((1 - unprocessed) * nsPerTick) / 1000000, (int) ((1 - unprocessed) * nsPerTick) % 1000000);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
}
