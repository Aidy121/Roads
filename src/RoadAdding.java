// Author: Aidan Fisher

import java.awt.Point;
import java.util.ArrayList;

public class RoadAdding {

	// Makes d within -PI and PI.
	public static double rationalizeNN(double d) {
		while (d > Math.PI || d < -Math.PI) {
			if (d > Math.PI) {
				d -= Math.PI * 2;
			} else if (d < -Math.PI) {
				d += Math.PI * 2;
			}
		}
		return d;
	}

	public static double rationalizeLarge(double d) {
		while (d > Math.PI * 2 || d < -Math.PI * 2) {
			if (d > Math.PI * 2) {
				d -= Math.PI * 4;
			} else if (d < -Math.PI * 2) {
				d += Math.PI * 4;
			}
		}
		return d;
	}

	// Makes d within PI of e. (2 PI increments)
	public static double rationalize(double d, double e) {
		while (d > e + Math.PI || d < e - Math.PI) {
			if (d > e + Math.PI) {
				d -= Math.PI * 2;
			} else if (d < e - Math.PI) {
				d += Math.PI * 2;
			}
		}
		return d;
	}

	// Makes d within 2 PI of e. (4 PI increments). This is to allow roads to arc beyond "turning around"
	public static double rationalizeLarge(double d, double e) {
		while (d > e + Math.PI * 2 || d < e - Math.PI * 2) {
			if (d > e + Math.PI * 2) {
				d -= Math.PI * 4;
			} else if (d < e - Math.PI * 2) {
				d += Math.PI * 4;
			}
		}
		return d;
	}

	// The 2 lines a, and b, the point of intersection.
	public static Point.Double getPointOfIntersection(double dirA, double dirB, Point.Double a, Point.Double b, boolean flipped) {
		double m1 = Math.tan(dirA);
		if (flipped) {
			m1 = -1 / m1;
		}
		double b1 = a.y - m1 * a.x;

		double m2 = Math.tan(dirB);
		if (flipped) {
			m2 = -1 / m2;
		}
		double b2 = b.y - m2 * b.x;

		// Point of intersection:
		double iX = (b2 - b1) / (m1 - m2);
		double iY = m1 * iX + b1;

		return new Point.Double(iX, iY);
	}

	// Returns the expected direction with the given road and possibly intersection. (Uses listening variable)
	public static double getDir(RoadSegment r, Intersection i, boolean side, int aOrB) {
		double dir;
		if (i != null) {
			dir = Listening.interDirections[aOrB];
		} else if (!side) {
			dir = r.direction[0] + Math.PI;
		} else {
			dir = r.direction[1];
		}
		if (dir == 0) {
			return 0.0000001;
		} else {
			return dir;
		}
	}

	// buildRoad uses this to add an arc road between the two points, (there is only one possible with the given initial direction dirA.) Point a should be the existing "road"
	public static RoadSegment getRoad(double dirA, Point.Double a, Point.Double b, int lanes1, int lanes2, boolean exists) {
		double betweenDir = rationalizeNN(Math.atan2(b.y - a.y, b.x - a.x));
		double dirB = rationalizeLarge(betweenDir + (betweenDir - dirA));
		dirB = rationalizeLarge(dirB, dirA);
		Point.Double inter = getPointOfIntersection(dirA, dirB, a, b, true);
		return new RoadSegment(a.x, a.y, b.x, b.y, dirA, dirB, inter.x, inter.y, lanes1, lanes2, exists);
	}

	// List of variables for buildRoad:
	// pressLocation / nextLocation
	// roadsSnapped
	// intersSnapped
	// roadsOnSide
	// roadsOnSideOfRoad
	// interDirections

	// Builds the roads applicable to the data given. "exists" determines whether the road is saved and actually connected to the road network through its paths.
	// Uses many variables from Listening
	public static ArrayList<RoadSegment> buildRoad(Point.Double a, Point.Double b, RoadSegment aR, RoadSegment bR, Intersection aI, Intersection bI, boolean aS, boolean bS, int aC, int bC,
			boolean exists) {
		// Note how variables a & b have already been snapped to their final locations.
		ArrayList<RoadSegment> roadSegments = new ArrayList<RoadSegment>();
		if ((aR != null || aI != null) && (bR != null || bI != null) && (aR != bR || aR == null && bR == null && aI != bI)) {
			if (aI != null || bI != null || aS != bS && aR.yellowLine == bR.yellowLine && aR.numPaths == bR.numPaths || aS == bS && aR.yellowLine == bR.numPaths - bR.yellowLine
					&& aR.numPaths == bR.numPaths) {
				// Building a road between two "roads" where the # of lanes is maintained exactly:

				double dirA = rationalizeNN(getDir(aR, aI, aS, 0));
				double dirB = rationalizeNN(getDir(bR, bI, bS, 1));

				Point.Double inter = getPointOfIntersection(dirA, dirB, a, b, false);

				// Determines if the inter is infront / behind the "road" directions.
				boolean onLineA = Connection.isOnLine(dirA, a, inter);
				boolean onLineB = Connection.isOnLine(dirB, b, inter);

				// Case in which there is one with the line intersection infront. Note, that these are the cases that currently don't work every time.
				if (!onLineA && onLineB) {
					Point.Double mid = new Point.Double((a.x + b.x) / 2, (a.y + b.y) / 2);

					RoadSegment r = getRoad(rationalizeNN(getDir(aR, aI, aS, 0)), a, mid, Listening.lanes[0], Listening.lanes[1], exists);

					roadSegments.add(r);
					if (exists) {
						if (aR != null) {
							setSides(true, aS, r, aR, aC);
						} else {
							aI.addRoad(r, Listening.interDirections[0], true);
						}
					}
					a = mid;
					aR = r;
					aC = Listening.NORMAL_ROAD;
					aI = null;
					aS = true;
					dirA = rationalizeNN(getDir(aR, aI, aS, 0));

					inter = getPointOfIntersection(dirA, dirB, a, b, false);
				} else if (!onLineB && onLineA) { // Exact opposite case from above: For consistency.
					Point.Double mid = new Point.Double((a.x + b.x) / 2, (a.y + b.y) / 2);

					RoadSegment r = getRoad(rationalizeNN(getDir(bR, bI, bS, 1)), b, mid, Listening.lanes[1], Listening.lanes[0], exists);

					roadSegments.add(r);
					if (exists) {
						if (bR != null) {
							setSides(true, bS, r, bR, bC);
						} else {
							bI.addRoad(r, Listening.interDirections[1], true);
						}
					}
					b = mid;
					bR = r;
					bC = Listening.NORMAL_ROAD;
					bI = null;
					bS = true;
					dirB = rationalizeNN(getDir(bR, bI, bS, 1));

					inter = getPointOfIntersection(dirA, dirB, a, b, false);
				} else if (!onLineA && !onLineB) {
					// An inconsistent issue with straight / arc roads. One must build the road in two sections in this extreme case.
					// Ultimately, the types of roads built in this situation would be MASSIVE arcs that are not particularly natural.
					return roadSegments; // Doesn't build anything.
				}

				// With aR, bR, dirA, dirB:
				// With the above cases cleared, it is now a true, true for onLineA, onLineB.
				double distanceA = Path.distance(a, inter);
				double distanceB = Path.distance(b, inter);
				RoadSegment r1, r2;
				if (distanceA > distanceB + RoadSegment.pointDistance) {
					Point.Double p = new Point.Double(inter.x - Math.cos(dirA) * distanceB, inter.y - Math.sin(dirA) * distanceB);
					r1 = new RoadSegment(a.x, a.y, p.x, p.y, Listening.lanes[0], Listening.lanes[1], exists);
					Point.Double turnInter = getPointOfIntersection(dirA, dirB, p, b, true);
					dirB = rationalizeNN(dirB + Math.PI);
					r2 = new RoadSegment(p.x, p.y, b.x, b.y, dirA, rationalize(dirB, dirA), turnInter.x, turnInter.y, Listening.lanes[0], Listening.lanes[1], exists);
				} else if (distanceB > distanceA + RoadSegment.pointDistance) {
					Point.Double p = new Point.Double(inter.x - Math.cos(dirB) * distanceA, inter.y - Math.sin(dirB) * distanceA);
					r1 = new RoadSegment(p.x, p.y, b.x, b.y, Listening.lanes[0], Listening.lanes[1], exists);
					Point.Double turnInter = getPointOfIntersection(dirA, dirB, a, p, true);
					dirB = rationalizeNN(dirB - Math.PI);
					r2 = new RoadSegment(a.x, a.y, p.x, p.y, dirA, rationalize(dirB, dirA), turnInter.x, turnInter.y, Listening.lanes[0], Listening.lanes[1], exists);
				} else {
					// This is a situation that has only happened when an arced road is deleted between two existsing roads. Ultimately, the road can be placed in two parts. (It only requires one arc, technically)
					return roadSegments;
				}
				roadSegments.add(r1);
				roadSegments.add(r2);
				if (exists) {
					for (int i = 0; i < roadSegments.size(); i++) {
						Component.roads.add(roadSegments.get(i));
					}
					if (distanceA > distanceB) {
						if (aI != null) {
							aI.addRoad(r1, Listening.interDirections[0], true);
						} else {
							setSides(true, aS, r1, aR, aC);
						}
						setSides(false, false, r1, r2, Listening.NORMAL_ROAD);
						if (bI != null) {
							bI.addRoad(r2, Listening.interDirections[1], false);
						} else {
							setSides(false, bS, r2, bR, bC);
						}
					} else if (distanceB > distanceA) {
						if (bI != null) {
							bI.addRoad(r1, Listening.interDirections[1], false);
						} else {
							setSides(false, bS, r1, bR, bC);
						}
						setSides(true, true, r1, r2, Listening.NORMAL_ROAD);
						if (aI != null) {
							aI.addRoad(r2, Listening.interDirections[0], true);
						} else {
							setSides(true, aS, r2, aR, aC);
						}
					}

				}
				return roadSegments;
			} else {
				// In the case that the lanes aren't exact, and connecting between the two "roads," a connection is made.
				Connection c = new Connection(aR, !aS, bR, !bS, exists);
				if (exists) {
					Component.connections.add(c);
				}
				roadSegments.add(c);
				return roadSegments;
			}
		}

		// Due to snapping, a == b is an intersection:
		if (a == b) {
			double dir = 0;
			if (aR != null) { // Possible to build an intersection connected to a road.
				dir = getDir(aR, null, aS, 0);
				a = new Point.Double(a.x + Math.cos(dir) * Intersection.defaultRadius, a.y + Math.sin(dir) * Intersection.defaultRadius);
			} else {
				for (int i = 0; i < Component.roads.size(); i++) {
					for (int j = 0; j < 2; j++) {
						if (Path.distance(Component.roads.get(i).x[j], Component.roads.get(i).y[j], a.x, a.y) < Intersection.defaultRadius) {
							// Doesn't build an intersection due to inconsistencies, (it could be unexpected that the intersection will be built, and / or the distance will be unexpected)
							return roadSegments;
						}
					}
				}
			}

			// Adds the intersection
			Intersection i = new Intersection(a.x, a.y, exists);
			if (exists) {
				Component.intersections.add(i);
				if (aR != null) {
					i.addRoad(aR, rationalizeNN(dir + Math.PI), !aS);
				}
			}
			roadSegments.add(i);
			return roadSegments;
		}

		// This is if the road is being built with 1 or 0 "roads" connected to it.
		RoadSegment r;
		if (aR != null || aI != null) { // If one exists, builds an arced road to bring it to the point.
			r = getRoad(rationalizeNN(getDir(aR, aI, aS, 0)), a, b, Listening.lanes[0], Listening.lanes[1], exists);
		} else if (bR != null || bI != null) { // Flips a and b because first point has the known direction.
			r = getRoad(rationalizeNN(getDir(bR, bI, bS, 1)), b, a, Listening.lanes[1], Listening.lanes[0], exists);
		} else {
			// Builds a straight road between the two points.
			r = new RoadSegment(a.x, a.y, b.x, b.y, Listening.lanes[0], Listening.lanes[1], exists);
		}

		// Accounts for roads in which it is a one way road where it is like a highway exit.
		if ((aR != null && aC != Listening.NORMAL_ROAD) || (bR != null && bC != Listening.NORMAL_ROAD)) {
			r.roadType = RoadSegment.NO_YELLOW_LINE;
		}

		// Adds the roads if its not just being rendered.
		if (exists) {
			Component.roads.add(r);
			if (aR != null) {
				setSides(true, aS, r, aR, aC);
			} else if (aI != null) {
				aI.addRoad(r, Listening.interDirections[0], true);
			}
			if (bR != null) {
				setSides(true, bS, r, bR, bC);
			} else if (bI != null) {
				bI.addRoad(r, Listening.interDirections[1], true);
			}

		}
		roadSegments.add(r);
		return roadSegments;
	}

	// Flips i from 0 to 1 or 1 to 0
	public static int opp(int i) {
		if (i == 1)
			return 0;
		else
			return 1;
	}

	// Returns the "incremental" value for true (1) / false (-1)
	public static int increment(boolean b) {
		if (b)
			return 1;
		else
			return -1;
	}

	// Connects together two road's paths. Uses listening variable.
	public static void setSides(boolean sideA, boolean sideB, RoadSegment road1, RoadSegment road2, int roadSide) {
		// Connect the roads:
		RoadSegment[] r = { road1, road2 };
		boolean[] side = { sideA, !sideB };
		int[] s = { 0, 0 };
		if (side[0])
			s[0] = 1;
		if (side[1])
			s[1] = 1;

		// This means the connection includes the yellow line:
		if (roadSide == Listening.NORMAL_ROAD) {
			int[][] lanes = new int[2][2];
			lanes[0][s[0]] = r[0].yellowLine;
			lanes[0][opp(s[0])] = r[0].numPaths - r[0].yellowLine;
			lanes[1][s[1]] = r[1].yellowLine;
			lanes[1][opp(s[1])] = r[1].numPaths - r[1].yellowLine;

			for (int r1 = 0, r2 = 1; r1 <= 1; r1++, r2--) {

				if (lanes[r1][0] <= lanes[r2][1]) {
					// All lanes for road 1 continue on same track, based on the yellow line.
					int i = 0;
					int n = r[r1].yellowLine - 1 + s[r1];
					int o = r[r2].yellowLine - 1 + opp(s[r2]);
					for (; i < lanes[r1][0]; i++, n += increment(side[r1]), o += increment(!side[r2])) {
						r[r1].paths.get(n).connectTo(r[r2].paths.get(o));
					}
				} else {
					int i = 0;
					int n = r[r1].yellowLine - 1 + s[r1];
					int o = r[r2].yellowLine - 1 + opp(s[r2]);
					for (; i < lanes[r2][1]; i++, n += increment(side[r1]), o += increment(!side[r2])) {
						r[r1].paths.get(n).connectTo(r[r2].paths.get(o));
					}
				}
			}
		} else {
			s[0] = opp(s[0]);
			s[1] = opp(s[1]);
			// Always should be a 1 way road. Always a road that is only connected to this road.
			if (r[0].yellowLine == r[0].numPaths * Math.abs(roadSide - s[1])) {
				System.out.println("Built");
				// roadSide == s[1] is the direction.
				// r[0].yellowLine should be 0 if roadSide == s[1] is true.

				int numLanes1 = r[0].numPaths;

				// n & o are to connect the paths
				// Solve for "numLanes2" and n.
				int n = Math.abs((r[1].numPaths - 1) * roadSide); // Intends to account for numLanes2
				int numLanes2 = 0;
				// n is either 0 or r[1].numPaths - 1
				for (;; n += increment(roadSide == 0)) { // Note how this is unrelated to iteration for connecting the paths.
					if (n - (increment(roadSide == 0) - 1) / 2 == r[1].yellowLine) {
						n -= increment(roadSide == 0);
						break;
					}
					if (roadSide != s[1]) {
						if (r[1].paths.get(n).nextPath.size() > 0) {
							n -= increment(roadSide == 0);
							break;
						} else {
							numLanes2++;
						}
					} else {
						if (r[1].paths.get(n).beforePath.size() > 0) {
							n -= increment(roadSide == 0);
							break;
						} else {
							numLanes2++;
						}
					}
				}
				int o = r[0].yellowLine - Math.abs(roadSide - s[1]); // Must be a 1 way road.
				for (int i = 0; i < Math.min(numLanes1, numLanes2); i++, n -= increment(roadSide == 0), o += increment(roadSide == s[1])) {
					if (roadSide != s[1]) {
						r[1].paths.get(n).connectTo(r[0].paths.get(o));
					} else {
						r[0].paths.get(o).connectTo(r[1].paths.get(n));
					}
				}
			}
		}
	}

	// Connects together a connection's paths with two roads' paths
	public static void setSides(boolean sideA, boolean sideB, RoadSegment road1, RoadSegment road2, Connection c) {
		RoadSegment[] r = { road1, road2 };
		boolean[] side = { sideA, sideB };
		int[] s = { 0, 0 };
		if (side[0])
			s[0] = 1;
		if (side[1])
			s[1] = 1;

		int[][] lanes = new int[2][2];
		lanes[0][s[0]] = r[0].yellowLine;
		lanes[0][opp(s[0])] = r[0].numPaths - r[0].yellowLine;
		lanes[1][s[1]] = r[1].yellowLine;
		lanes[1][opp(s[1])] = r[1].numPaths - r[1].yellowLine;

		c.yellowLine = Math.max(lanes[0][0], lanes[1][1]);
		if (Math.min(lanes[0][0], lanes[1][1]) == 0) {
			c.yellowLine = 0;
		}
		c.numPaths = c.yellowLine + Math.max(lanes[1][0], lanes[0][1]);
		if (Math.min(lanes[1][0], lanes[0][1]) == 0) {
			c.numPaths = c.yellowLine;
		}
		c.setPaths(r[0], r[1]);

		for (int r1 = 0, r2 = 1; r1 <= 1; r1++, r2--) {

			// When there is more lanes in the 2nd part of the road: (Road expands)
			if (lanes[r1][0] <= lanes[r2][1]) {

				// All lanes for road 1 continue on same track, based on the yellow line.
				int i = 0;
				int n = r[r1].yellowLine - 1 + s[r1];
				int o = r[r2].yellowLine - 1 + opp(s[r2]);
				int k = c.yellowLine - 1 + r1; // r1 is 0 or 1
				for (; i < lanes[r1][0]; i++, n += increment(side[r1]), o += increment(!side[r2]), k += (r1 * 2 - 1)) {
					r[r1].paths.get(n).connectTo(c.paths.get(k));
					c.paths.get(k).connectTo(r[r2].paths.get(o));
					c.paths.get(k).createPath(r[r1], r[r1].paths.get(n).getX(true), r[r1].paths.get(n).getY(true), r[r2], r[r2].paths.get(o).getX(false), r[r2].paths.get(o).getY(false));
				}

				if (lanes[r1][0] != 0) {
					n -= increment(side[r1]);
					int oldK = k - (r1 * 2 - 1);
					for (; i < lanes[r2][1]; i++, o += increment(!side[r2]), k += (r1 * 2 - 1)) {
						c.paths.get(k).connectTo(r[r2].paths.get(o));
						r[r1].paths.get(n).connectTo(c.paths.get(k)); // Connect to the road that the lane "connected" to

						c.paths.get(k).createPath(r[r1], r[r1].paths.get(n).getX(true), r[r1].paths.get(n).getY(true), r[r2], r[r2].paths.get(o).getX(false), r[r2].paths.get(o).getY(false));
					}

					// Combine the paths:
					ArrayList<Path> samePaths = new ArrayList<Path>();
					int nK = oldK;
					for (i = lanes[r1][0] - 1; i < lanes[r2][1]; i++, nK += (r1 * 2 - 1)) {
						samePaths.add(c.paths.get(nK));
					}
					nK = oldK;
					for (i = lanes[r1][0] - 1; i < lanes[r2][1]; i++, nK += (r1 * 2 - 1)) {
						c.paths.get(nK).sameLanePaths = new ArrayList<Path>(samePaths);
					}
				}
			} else {
				// When a road is merging into less lanes.
				ArrayList<Path> mergeLanes = new ArrayList<Path>();

				// Lanes that are beyond road 2 lanes on road 1 merge to the rightmost lane
				int i = 0;
				int n = r[r1].yellowLine - 1 + s[r1];
				int o = r[r2].yellowLine - 1 + opp(s[r2]);
				int k = c.yellowLine - 1 + r1;
				for (; i < lanes[r2][1]; i++, n += increment(side[r1]), o += increment(!side[r2]), k += (r1 * 2 - 1)) {
					r[r1].paths.get(n).connectTo(c.paths.get(k));
					c.paths.get(k).connectTo(r[r2].paths.get(o));
					c.paths.get(k).createPath(r[r1], r[r1].paths.get(n).getX(true), r[r1].paths.get(n).getY(true), r[r2], r[r2].paths.get(o).getX(false), r[r2].paths.get(o).getY(false));

					if (i == lanes[r2][1] - 1) {
						// Lane that is the rightmost one that does not have to yield.
						mergeLanes.add(c.paths.get(k));
					}
				}

				if (lanes[r2][1] != 0) {
					int oldK = k - (r1 * 2 - 1);
					o -= increment(!side[r2]);
					for (; i < lanes[r1][0]; i++, n += increment(side[r1]), k += (r1 * 2 - 1)) {
						r[r1].paths.get(n).connectTo(c.paths.get(k));
						c.paths.get(k).connectTo(r[r2].paths.get(o));
						c.paths.get(k).createPath(r[r1], r[r1].paths.get(n).getX(true), r[r1].paths.get(n).getY(true), r[r2], r[r2].paths.get(o).getX(false), r[r2].paths.get(o).getY(false));
						mergeLanes.add(c.paths.get(k));
					}

					// Combine the paths:
					ArrayList<Path> samePaths = new ArrayList<Path>();
					int nK = oldK;
					for (i = lanes[r2][1] - 1; i < lanes[r1][0]; i++, nK += (r1 * 2 - 1)) {
						samePaths.add(c.paths.get(nK));
					}
					nK = oldK;
					for (i = lanes[r2][1] - 1; i < lanes[r1][0]; i++, nK += (r1 * 2 - 1)) {
						c.paths.get(nK).sameLanePaths = new ArrayList<Path>(samePaths);
					}
				}
			}
		}
	}
}
