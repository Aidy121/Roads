// Author: Aidan Fisher

import java.awt.*;
import java.util.ArrayList;
import java.util.Random;

public class Car {
	private final static double MAX_SPEED = 5; // Max speed under any circumstance.
	private final static double BETWEEN_CAR_DISTANCE = 80;
	private final static double BETWEEN_CAR_CONSTANT = 20; // Distance for every speed that a car has behind a car.
	private final static double REACTION_TIME = 5;

	private int reactionTick = 0;
	public Path onPath = null;
	public ArrayList<Path> nextPath = new ArrayList<Path>();
	private Path oldPath = null;
	private int onPoint = 0;
	private double speed = 0;

	// For now these are the same for all cars, functionality purposes
	private static double brakeSpeed = 0.5;
	private static double accelSpeed = 0.5;
	private double maxSpeed = 5;
	private boolean breaking = false;
	private boolean haveStopped = false;
	private boolean stoppingForStopSign = false;
	private double changeLaneImportance = 1; // If 0, then will completely stop, otherwise slows down accordingly.
	private Color color;

	public Car(Path path) {
		this.onPath = path;
		this.onPath.cars.add(this);
		int total = 255;
		int red = (int) (total * Math.random());
		total -= red;
		int green = (int) (total * Math.random());
		total -= green;
		int blue = (int) (total * Math.random());
		this.color = new Color(red, blue, green);
	}

	public void tick() {

		int maxBrakeDistance = brakeDistance(speed, 0);

		boolean shouldBeBreaking;

		if (oldPath == null) {
			shouldBeBreaking = carsAhead(onPath, onPoint, maxBrakeDistance, 0);
		} else {
			shouldBeBreaking = carsAhead(onPath.nextPath.get(0), getPoint(onPath.nextPath.get(0)), maxBrakeDistance, 0) || carsAhead(oldPath, getPoint(oldPath), maxBrakeDistance, 0);
		}

		if (breaking && !shouldBeBreaking && speed == 0) {
			reactionTick++;
			if (reactionTick == REACTION_TIME) {
				reactionTick = 0;
				breaking = false;
			}
		} else {
			reactionTick = 0;
			breaking = shouldBeBreaking;
		}

		// Update if breaking.
		if (!breaking) {
			breaking = speed > changeLaneImportance * maxSpeed;
		}

		// Update speed. (Note speed can't go under 0 or above max speed)
		if (breaking) {
			addSpeed(-brakeSpeed);
		} else {
			addSpeed(accelSpeed);
		}

		// Check if path exists
		for (int i = 0; i < nextPath.size(); i++) {
			if (!nextPath.get(i).segment.exists) {
				for (; i < nextPath.size();) {
					nextPath.remove(i);
				}
				break;
			}
		}

		// Make sure there is at least 10 paths ahead.
		while (nextPath.size() < 10) {
			if (nextPath.size() == 0) {
				if (onPath.nextPath.size() > 0) {
					int index = new Random().nextInt(onPath.nextPath.size());
					nextPath.add(onPath.nextPath.get(index));
				} else {
					break;
				}
			} else {
				if (nextPath.get(nextPath.size() - 1).nextPath.size() > 0) {
					int index = new Random().nextInt(nextPath.get(nextPath.size() - 1).nextPath.size());
					nextPath.add(nextPath.get(nextPath.size() - 1).nextPath.get(index));
				} else {
					break;
				}
			}
		}

		// Stop sign case.
		if (stoppingForStopSign && speed < RoadSegment.pointDistance) {
			speed = 0;
			stoppingForStopSign = false;
			haveStopped = true;
		}

		// Go forward:
		onPoint += (int) (speed / RoadSegment.pointDistance);
		while (onPoint >= onPath.path.size()) {
			// Go to next path:
			if (nextPath.size() > 0) {
				onPoint = onPoint - onPath.path.size();
				if (onPath.laneType == Path.STOP) {
					haveStopped = false;
				}

				onPath.cars.remove(this);
				onPoint = onPath.nextPathJoin;
				onPath = nextPath.get(0);
				nextPath.remove(0);
				if (oldPath != null) {
					oldPath.cars.remove(this);
					oldPath = null;
				} else {
					onPath.cars.add(this);
				}

			} else {
				onPoint = onPath.path.size() - 1;
			}
		}

		if ((changeLaneImportance < 0.9999 || Math.random() < 0.0000) && oldPath == null && !onPath.isCurve) { // Only start to change lanes when not changing lanes
			// Change lanes!
			int index = onPath.segment.paths.indexOf(onPath);
			int change; // Changing lanes away from yellow line for now
			if (index < onPath.segment.yellowLine) {
				change = -1;
			} else {
				change = 1;
			}

			index += change;

			// Check if valid change:
			if (index >= 0 && index < onPath.segment.numPaths) {

				Path newPath = onPath.segment.paths.get(index);
				int nextPoint = (int) (onPoint * (newPath.path.size() / (double) onPath.path.size())) + RoadSegment.changeLaneDistance;

				// Can only change lanes on same path / road segment, (for now)
				if (nextPoint < newPath.path.size()) {
					// Set to change lanes:

					int dir = carsAround(newPath, nextPoint - RoadSegment.changeLaneDistance, RoadSegment.changeLaneDistance);

					if (dir == 0) {

						changeLaneImportance = 1;

						// Merge:
						newPath.cars.add(this);
						oldPath = onPath;

						onPath = new Path(newPath, nextPoint, onPoint, this, onPath.path.get(onPoint).x, onPath.path.get(onPoint).y, newPath.path.get(nextPoint).x, newPath.path.get(nextPoint).y);
						nextPath.clear();
						nextPath.add(newPath);
						onPoint = 0;
					} else {
						changeLaneImportance -= (0.001 * dir);
						if (changeLaneImportance < 0.95) { // If of upmost importance, will slow to 0.
							changeLaneImportance = 0.95;
						} else if (changeLaneImportance > 1) {
							changeLaneImportance = 1;
						}
					}
				}
			}
		}
	}

	/*private int accelDistance(double fromSpeed, double speed) {
		return (int) ((speed * (speed / accelSpeed) - fromSpeed * (fromSpeed / accelSpeed)) / RoadSegment.pointDistance);
	}

	private int accelTime(double fromSpeed, double speed, int distance) {
		int accelDistance = accelDistance(fromSpeed, speed);
	}

	private int timeToTravel(double fromSpeed, int distance) {
		int accelDistance = accelDistance(fromSpeed, maxSpeed);
		int time;
		if (distance < accelDistance) {
			time = accelTime(fromSpeed, maxSpeed, distance);
		} else {
			time = (int) (maxSpeed / accelSpeed / RoadSegment.pointDistance);
			time += (int) ((distance - accelDistance) / maxSpeed);
		}
		return time;
	}*/

	public int carsAround(Path path, int point, int distance) {
		// This check is based around cars speed
		int dir = 0;
		if (carsBehind(path, point, brakeDistance(MAX_SPEED, speed) + (int) (BETWEEN_CAR_DISTANCE / RoadSegment.pointDistance), 0)) {
			dir = -1;
		}
		if (carsAhead(path, point, brakeDistance(speed, 0) + (int) (BETWEEN_CAR_DISTANCE / RoadSegment.pointDistance), 0)) {
			dir = 1;
		}

		// Will be.
		return dir;

	}

	public Path getNextPath(Path path) {
		if (path == onPath) {
			if (nextPath.size() == 0) {
				return null;
			}
			return nextPath.get(0);
		} else {
			for (int i = 1; i < nextPath.size(); i++) {
				if (path == nextPath.get(i - 1)) {
					return nextPath.get(i);
				}
			}
			return null;
		}
	}

	public static int brakeDistance(double speed1, double speed2) {
		if (speed2 >= speed1) {
			return (int) ((BETWEEN_CAR_DISTANCE + speed1 * BETWEEN_CAR_CONSTANT) / RoadSegment.pointDistance);
		}
		return (int) (((((speed1 * speed1) + (speed2 * speed2)) / (2 * brakeSpeed)) + (BETWEEN_CAR_DISTANCE + speed1 * BETWEEN_CAR_CONSTANT)) / RoadSegment.pointDistance);
	}

	public static int trueBrakeDistance(double speed) {
		return (int) (((speed * speed) / (2 * brakeSpeed)) / RoadSegment.pointDistance);
	}

	// This includes checking for yielding necessary for braking.
	public boolean carsAhead(Path checkPath, int checkPoint, int distance, int totalDistance) {
		checkPoint += 1;
		ArrayList<Car> cars = getCars(checkPath);
		for (int i = 0; i < cars.size(); i++) {
			if (cars.get(i).getPoint(checkPath) >= checkPoint && cars.get(i).getPoint(checkPath) <= checkPoint + distance) {
				// See if within "breaking distance"
				if (totalDistance + (cars.get(i).getPoint(checkPath) - checkPoint) < brakeDistance(speed, cars.get(i).speed)) {
					return true;
				}
			}
		}

		// Checked far enough to be sure:
		if (checkPoint + distance < checkPath.path.size()) {
			return false;
		}

		if (getNextPath(checkPath) == null) {
			return true;
		}
		// Else path does exist:
		totalDistance += (checkPath.path.size() - checkPoint);

		if (getNextPath(checkPath).sameLanePaths != null) {
			int timeStart = (int) (totalDistance / (maxSpeed));
			int timeFinish = (int) ((totalDistance + BETWEEN_CAR_DISTANCE) / (speed / 2 + maxSpeed / 2));
			//int thisDist = getNextPath(checkPath).path.size();
			for (int i = 0; i < getNextPath(checkPath).sameLanePaths.size(); i++) {
				Path checkSamePath = getNextPath(checkPath).sameLanePaths.get(i);
				//int theirDist = checkSamePath.path.size();
				if (getNextPath(checkPath) != checkSamePath && getNextPath(checkPath).nextPath.get(0) == checkSamePath.nextPath.get(0)) {
					int yieldDist = 0; // (int) (BETWEEN_CAR_DISTANCE / RoadSegment.pointDistance);
					if (yieldCheck(checkSamePath, yieldDist, checkSamePath.beforePath.get(0), timeStart, timeFinish, yieldDist, (int) (timeFinish * MAX_SPEED))) {
						return true;
					}
				}
			}
		}
		if (getNextPath(checkPath).laneType == Path.GO) {
			// Just to prevent this from being considered.
		} else if (getNextPath(checkPath).isYield()) { // You yield to a yield zone which you are currently not in.
			// Must also check yield paths: (and behind the yield paths)
			int timeStart = (int) (totalDistance / (maxSpeed));
			int timeFinish = (int) ((totalDistance + getNextPath(checkPath).path.size()) / (speed / 2 + maxSpeed / 2));
			// Check if cars are there:
			for (Path checkYieldPath : getNextPath(checkPath).yieldTo) {
				if (yieldCheck(checkYieldPath, checkYieldPath.path.size(), checkYieldPath, timeStart, timeFinish, checkYieldPath.path.size(), (int) (timeFinish * MAX_SPEED))) {
					return true;
				}
			}
		} else if (getNextPath(checkPath).isGoYield()) {
			for (Path checkYieldPath : getNextPath(checkPath).yieldTo) {
				// Check the paths that the yield path infront says to yield to.
				for (Car car : checkYieldPath.cars) {
					if (car.breaking && car.getPoint(checkYieldPath) + trueBrakeDistance(car.speed) < checkYieldPath.path.size()) {
						return true;
					}
				}
			}
		} else if (getNextPath(checkPath).laneType == Path.STOP) {
			if (!haveStopped) {
				stoppingForStopSign = true;
				return true;
			} else {
				// See if should continue stopping:
				return stopCheck(getNextPath(checkPath)) || carsAhead(getNextPath(checkPath), checkPath.nextPathJoin - 1, distance - (checkPath.path.size() - checkPoint), totalDistance);
			}
		} else if (getNextPath(checkPath).isStopped()) {
			if (getPoint(onPath) + trueBrakeDistance(speed) < totalDistance) {
				return true;
			} else {
				return true; // Already committed.
			}
		}
		return carsAhead(getNextPath(checkPath), checkPath.nextPathJoin - 1, distance - (checkPath.path.size() - checkPoint), totalDistance);
	}

	public boolean carsBehind(Path checkPath, int checkPoint, int distance, int totalDistance) {
		checkPoint -= 1;
		ArrayList<Car> cars = getCars(checkPath);
		for (int i = 0; i < cars.size(); i++) {
			if (cars.get(i).getPoint(checkPath) <= checkPoint && cars.get(i).getPoint(checkPath) >= checkPoint - distance) {
				if (totalDistance + (checkPoint - cars.get(i).getPoint(checkPath)) < brakeDistance(cars.get(i).speed, speed)) {
					return true;
				}
			}
		}
		if (checkPoint - distance > 0) {
			return false;
		}
		if (checkPath.beforePath.size() == 0) {
			// Doesn't care:
			return false;
		} else {
			totalDistance += checkPoint;

			for (int i = 0; i < checkPath.beforePath.size(); i++) {
				if (carsBehind(checkPath.beforePath.get(i), checkPath.lastPathLeave + 1, distance - checkPoint, totalDistance)) {
					return true;
				}
			}
			return false;

		}
	}

	public boolean stopCheck(Path path) {
		for (int i = 0; i < path.yieldTo.size(); i++) {
			if (path.yieldTo.get(i).cars.size() > 0) {
				return true;
			} else {
				for (int j = 0; j < path.yieldTo.get(i).beforePath.get(0).cars.size(); j++) {
					Car car = path.yieldTo.get(i).beforePath.get(0).cars.get(j);
					if (car.haveStopped && car.speed > 0) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public double getAdjustedHighSpeed() {
		if (breaking)
			return speed;
		else
			return maxSpeed;
	}

	// Not necessarily required:
	public double getAdjustedLowSpeed() {
		if (breaking)
			return 0;
		else
			return speed;
	}

	public boolean yieldCheck(Path originalYieldPath, int yieldDist, Path yieldPath, double timeStart, double timeFinish, int distanceToTravel, int maxCheckDistance) {

		// Yielding refers to the one lane only
		for (int i = 0; i < yieldPath.cars.size(); i++) {
			if (yieldPath.cars.get(i).isGoingTo(originalYieldPath)) {
				double distanceToYield = distanceToTravel + (yieldPath.path.size() - yieldPath.cars.get(i).getPoint(yieldPath)) - yieldDist; // Travel distance
				double distanceToEndOfYield = distanceToTravel + (yieldPath.path.size() - yieldPath.cars.get(i).getPoint(yieldPath)); // Travel distance

				if (distanceToEndOfYield / yieldPath.cars.get(i).getAdjustedLowSpeed() > timeStart && distanceToYield / yieldPath.cars.get(i).getAdjustedHighSpeed() < timeFinish) {
					return true;
				}
			}
		}
		if (maxCheckDistance < yieldPath.path.size()) {
			return false;
		}
		if (yieldPath.beforePath.size() == 0) {
			return false;
		} else {
			maxCheckDistance -= yieldPath.path.size();
			distanceToTravel += yieldPath.path.size();
			for (int i = 0; i < yieldPath.beforePath.size(); i++) {
				if (yieldCheck(originalYieldPath, yieldDist, yieldPath.beforePath.get(i), timeStart, timeFinish, distanceToTravel, maxCheckDistance)) {
					return true;
				}
			}
			return false;
		}
	}

	public boolean isGoingTo(Path path) {
		if (onPath == path) {
			return true;
		} else {
			for (int i = 0; i < nextPath.size(); i++) {
				if (nextPath.get(i) == path) {
					return true;
				}
			}
			return false;
		}
	}

	public boolean isOnSameLanePath(Path path) {
		if (onPath.sameLanePaths != null) {
			for (int i = 0; i < onPath.sameLanePaths.size(); i++) {
				if (path == onPath.sameLanePaths.get(i)) {
					return true;
				}
			}
		}
		return false;
	}

	public int getPoint(Path path) {
		if (path == onPath) {
			return onPoint;
		} else if (path == oldPath) {
			return onPath.lastPathLeave + onPoint;
		} else if (path == getNextPath(onPath)) {
			return onPath.nextPathJoin - onPath.path.size() + onPoint;
		} else if (isOnSameLanePath(path)) {
			// Check if merging or not: (With the other path)
			if (path.nextPath.get(0) == onPath.nextPath.get(0)) {
				// Merging!
				return path.path.size() - (onPath.path.size() - onPoint);
			} else {
				return onPoint;
			}
		} else if (isGoingTo(path)) {
			int point = onPath.nextPathJoin - onPath.path.size() + onPoint;
			for (int i = 1; i < nextPath.size(); i++) {
				point += nextPath.get(i - 1).nextPathJoin - nextPath.get(i - 1).path.size();
				if (nextPath.get(i) == path) {
					return point;
				}
			}
			System.out.println("Can't happen");
			return 0; // Can't happen
		}
		System.out.println(path.sameLanePaths + " " + path + " " + onPath + " " + onPath.sameLanePaths);
		System.out.println("Can not happen!");
		return 0; // Can't happen
	}

	public ArrayList<Car> getCars(Path path) {
		if (oldPath != null && onPath == path) { // On a path with no other cars.
			ArrayList<Car> cars = getCars(oldPath);
			cars.addAll(getCars(onPath.nextPath.get(0)));
			return cars;
		}
		if (path.sameLanePaths == null) {
			return path.cars;
		} else {
			ArrayList<Car> cars = new ArrayList<Car>();
			for (int i = 0; i < path.sameLanePaths.size(); i++) {
				cars.addAll(path.sameLanePaths.get(i).cars);
			}
			return cars;
		}
	}

	public void addSpeed(double amount) {
		if (speed + amount < 0) {
			speed = 0;
		} else if (speed + amount > maxSpeed) {
			speed = maxSpeed;
		} else {
			speed += amount;
		}
	}

	public void render(Graphics2D g, Path path) {
		if (path != null) {
			g.setColor(new Color(255, 255, 255));
		} else {
			g.setColor(color);
			path = onPath;
		}
		int x = (int) Component.toSX(onPath.path.get(onPoint).x);
		int y = (int) Component.toSY(onPath.path.get(onPoint).y);
		if (oldPath == null && (path.segment.arc || path.isCurve)) {
			g.rotate(path.rotPath.get(onPoint), x, y);
		} else {
			g.rotate(path.direction[0], x, y);
		}
		g.fillRect(x - (int) (25 * Component.zoomLevel), y - (int) (15 * Component.zoomLevel), (int) (50 * Component.zoomLevel), (int) (30 * Component.zoomLevel));
		if (oldPath == null && (path.segment.arc || path.isCurve)) {
			g.rotate(-path.rotPath.get(onPoint), x, y);
		} else {
			g.rotate(-path.direction[0], x, y);
		}
	}
}
