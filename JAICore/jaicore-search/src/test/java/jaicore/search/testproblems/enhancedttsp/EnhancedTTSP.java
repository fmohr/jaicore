package jaicore.search.testproblems.enhancedttsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import jaicore.basic.sets.SetUtil.Pair;
import jaicore.graph.LabeledGraph;
import jaicore.search.algorithms.parallel.parallelexploration.distributed.interfaces.SerializableGraphGenerator;
import jaicore.search.core.interfaces.ISolutionEvaluator;
import jaicore.search.model.travesaltree.NodeExpansionDescription;
import jaicore.search.model.travesaltree.NodeType;
import jaicore.search.structure.graphgenerator.NodeGoalTester;
import jaicore.search.structure.graphgenerator.SingleRootGenerator;
import jaicore.search.structure.graphgenerator.SingleSuccessorGenerator;
import jaicore.search.structure.graphgenerator.SuccessorGenerator;

/**
 * This class provides a search graph generator for an augmented version of the timed TSP (TTSP) problem. In TTSP, the original route graph has not only one cost per edge (u,v) but a list of costs where each position in the list represents an hour of departure from u. That is,
 * depending on the time when leaving u, the cost to reach v may vary.
 *
 * The intended time measure is hours. That is, the length of the lists of all edges should be identical and reflect the number of hours considered. Typically, this is 24 or a multiple of that, e.g. 7 * 24.
 *
 * Time is considered cyclic. That is, if you consider 24 hours and the current time is 25.5, then it is automatically set to 1.5.
 *
 * The TTSP is enhanced by the consideration of blocking times for the streets, e.g. on Sundays (as in Germany) or during nights (as in Austria or Switzerland). Blocking times are stored in a separated list of Booleans.
 *
 * Also, the TTSP is enhanced in that driving pauses need to be considered. That is, the driver must only drive x hours in a row and then make a break of duration y. In addition, there must be an uninterrupted break of z hours every day.
 *
 * IT IS ASSUMED THAT BREAKS CAN ONLY BE MADE AT LOCATIONS AND NOT "ON THE ROAD"!
 *
 * @author fmohr
 *
 */
public class EnhancedTTSP {

	private static final Logger logger = LoggerFactory.getLogger(EnhancedTTSP.class);

	private final short startLocation;
	private final int numberOfConsideredHours;
	private final LabeledGraph<Short, Double> minTravelTimesGraph;
	private final List<Boolean> blockedHours;
	private final double hourOfDeparture;
	private final double durationOfShortBreak;
	private final double durationOfLongBreak;
	private final double maxConsecutiveDrivingTime;
	private final double maxDrivingTimeBetweenLongBreaks;
	private final ShortList possibleDestinations;

	public EnhancedTTSP(final LabeledGraph<Short, Double> minTravelTimesGraph, final short startLocation, final List<Boolean> blockedHours, final double hourOfDeparture, final double maxConsecutiveDrivingTime,
			final double durationOfShortBreak, final double durationOfLongBreak) {
		super();
		this.startLocation = startLocation;
		this.numberOfConsideredHours = blockedHours.size();
		this.minTravelTimesGraph = minTravelTimesGraph;
		this.blockedHours = blockedHours;
		this.hourOfDeparture = hourOfDeparture;
		this.durationOfShortBreak = durationOfShortBreak;
		this.durationOfLongBreak = durationOfLongBreak;
		this.maxConsecutiveDrivingTime = maxConsecutiveDrivingTime;
		this.maxDrivingTimeBetweenLongBreaks = 24 - durationOfLongBreak;
		this.possibleDestinations = new ShortArrayList(minTravelTimesGraph.getItems().stream().sorted().collect(Collectors.toList()));
	}

	public SerializableGraphGenerator<EnhancedTTSPNode, String> getGraphGenerator() {
		return new SerializableGraphGenerator<EnhancedTTSPNode, String>() {

			private static final long serialVersionUID = 1L;

			@Override
			public SingleRootGenerator<EnhancedTTSPNode> getRootGenerator() {
				return () -> {
					EnhancedTTSPNode root = new EnhancedTTSPNode(EnhancedTTSP.this.startLocation, new ShortArrayList(), EnhancedTTSP.this.hourOfDeparture, 0, 0);
					return root;
				};
			}

			@Override
			public SuccessorGenerator<EnhancedTTSPNode, String> getSuccessorGenerator() {
				return new SingleSuccessorGenerator<EnhancedTTSPNode, String>() {

					private ShortList getPossibleDestinationsThatHaveNotBeenGeneratedYet(final EnhancedTTSPNode n) {
						short curLoc = n.getCurLocation();
						ShortList possibleDestinationsToGoFromhere = new ShortArrayList();
						ShortList seenPlaces = n.getCurTour();
						int k = 0;
						boolean openPlaces = seenPlaces.size() < EnhancedTTSP.this.possibleDestinations.size() - 1;
						assert n.getCurTour().size() < EnhancedTTSP.this.possibleDestinations.size() : "We have already visited everything!";
						assert openPlaces || curLoc != 0 : "There are no open places (out of the " + EnhancedTTSP.this.possibleDestinations.size() + ", " + seenPlaces.size() + " of which have already been seen) but we are still in the initial position. This smells like a strange TSP.";
						if (openPlaces) {
							for (short l : EnhancedTTSP.this.possibleDestinations) {
								if (k++ == 0) {
									continue;
								}
								if (l != curLoc && !seenPlaces.contains(l)) {
									possibleDestinationsToGoFromhere.add(l);
								}
							}
						} else {
							possibleDestinationsToGoFromhere.add((short) 0);
						}
						return possibleDestinationsToGoFromhere;
					}

					@Override
					public List<NodeExpansionDescription<EnhancedTTSPNode, String>> generateSuccessors(final EnhancedTTSPNode node) {
						List<NodeExpansionDescription<EnhancedTTSPNode, String>> l = new ArrayList<>();
						if (node.getCurTour().size() >= EnhancedTTSP.this.possibleDestinations.size()) {
							logger.warn("Cannot generate successors of a node in which we are in pos " + node.getCurLocation() + " and in which have already visited everything! " + (getGoalTester().isGoal(node) ? "The goal tester detects this as a goal, but the method is invoked nevertheless. Maybe the algorithm that uses this graph generator does not properly check the goal node property. Another possibility is that a goal check DIFFERENT from this one is used" : "The goal tester does not detect this as a goal node!"));
							return l;
						}
						ShortList possibleUntriedDestinations = this.getPossibleDestinationsThatHaveNotBeenGeneratedYet(node);
						if (possibleUntriedDestinations.contains(node.getCurLocation())) {
							throw new IllegalStateException("The list of possible destinations must not contain the current position " + node.getCurLocation() + ".");
						}
						int N = possibleUntriedDestinations.size();
						for (int i = 0; i < N; i++) {
							l.add(this.generateSuccessor(node, possibleUntriedDestinations.getShort(i)));
						}
						return l;
					}

					private NodeExpansionDescription<EnhancedTTSPNode, String> generateSuccessor(final EnhancedTTSPNode n, final short destination) {

						if (n.getCurLocation() == destination) {
							throw new IllegalArgumentException("It is forbidden to ask for the successor to the current position as a destination!");
						}

						/*
						 * there is one successor for going to any of the not visited places that can be
						 * reached without making a break and that can reached without violating the
						 * blocking hour constraints
						 */
						short curLocation = n.getCurLocation();
						double curTime = n.getTime();
						double timeSinceLastShortBreak = n.getTimeTraveledSinceLastShortBreak();
						double timeSinceLastLongBreak = n.getTimeTraveledSinceLastLongBreak();

						double minTravelTime = EnhancedTTSP.this.minTravelTimesGraph.getEdgeLabel(curLocation, destination);
						logger.info("Simulating the ride from {} to " + destination + ", which minimally takes " + minTravelTime + ". We are departing at {}", curLocation, curTime);

						double timeToNextShortBreak = getTimeToNextShortBreak(curTime, Math.min(timeSinceLastShortBreak, timeSinceLastLongBreak));
						double timeToNextLongBreak = getTimeToNextLongBreak(curTime, timeSinceLastLongBreak);
						logger.info("Next short break will be in " + timeToNextShortBreak + "h");
						logger.info("Next long break will be in " + timeToNextLongBreak + "h");

						/* if we can do the tour without a break, do it */
						boolean arrived = false;
						double shareOfTheTripDone = 0.0;
						double timeOfArrival = -1;
						while (!arrived) {
							double permittedTimeToTravel = Math.min(timeToNextShortBreak, timeToNextLongBreak);
							double travelTimeWithoutBreak = getActualDrivingTimeWithoutBreak(minTravelTime, curTime, shareOfTheTripDone);
							assert timeToNextShortBreak >= 0 : "Time to next short break cannot be negative!";
							assert timeToNextLongBreak >= 0 : "Time to next long break cannot be negative!";
							assert travelTimeWithoutBreak >= 0 : "Travel time cannot be negative!";
							if (permittedTimeToTravel >= travelTimeWithoutBreak) {
								curTime += travelTimeWithoutBreak;
								arrived = true;
								timeOfArrival = curTime;
								timeSinceLastLongBreak += travelTimeWithoutBreak;
								timeSinceLastShortBreak += travelTimeWithoutBreak;
								logger.info("\tDriving the remaining distance to goal without a break. This takes " + travelTimeWithoutBreak + " (min time for this distance is "
										+ minTravelTime * (1 - shareOfTheTripDone) + ")");
							} else {

								/* simulate driving to next break */
								logger.info("\tCurrently achieved " + shareOfTheTripDone + "% of the trip.");
								logger.info("\tCannot reach the goal within the permitted " + permittedTimeToTravel + ", because the travel without a break would take " + travelTimeWithoutBreak);
								shareOfTheTripDone = getShareOfTripWhenDrivingOverEdgeAtAGivenTimeForAGivenTimeWithoutDoingABreak(minTravelTime, curTime, shareOfTheTripDone, permittedTimeToTravel);
								logger.info("\tDriving the permitted " + permittedTimeToTravel + "h. This allows us to finish " + (shareOfTheTripDone * 100) + "% of the trip.");
								if (permittedTimeToTravel == timeToNextLongBreak) {
									logger.info("\tDo long break, because it is necessary");
								}
								if (permittedTimeToTravel + EnhancedTTSP.this.durationOfShortBreak + 2 > timeToNextLongBreak) {
									logger.info("\tDo long break, because short break + 2 hours driving would require a long break anyway");
								}
								boolean doLongBreak = permittedTimeToTravel == timeToNextLongBreak || permittedTimeToTravel + EnhancedTTSP.this.durationOfShortBreak + 2 >= timeToNextLongBreak;
								if (doLongBreak) {
									curTime += permittedTimeToTravel + EnhancedTTSP.this.durationOfLongBreak;
									logger.info("\tDoing a long break (" + EnhancedTTSP.this.durationOfLongBreak + "h)");
									timeSinceLastShortBreak = 0;
									timeSinceLastLongBreak = 0;
									timeToNextShortBreak = getTimeToNextShortBreak(curTime, 0);
									timeToNextLongBreak = getTimeToNextLongBreak(curTime, 0);

								} else {
									double timeElapsed = permittedTimeToTravel + EnhancedTTSP.this.durationOfShortBreak;
									curTime += timeElapsed;
									logger.info("\tDoing a short break (" + EnhancedTTSP.this.durationOfShortBreak + "h)");
									timeSinceLastShortBreak = 0;
									timeSinceLastLongBreak += timeElapsed;
									timeToNextShortBreak = getTimeToNextShortBreak(curTime, 0);
									timeToNextLongBreak = getTimeToNextLongBreak(curTime, timeSinceLastLongBreak);
								}
							}
						}
						double travelDuration = curTime - n.getTime();
						logger.info("Finished travel simulation. Travel duration: " + travelDuration);

						ShortList tourToHere = new ShortArrayList(n.getCurTour());
						tourToHere.add(destination);
						EnhancedTTSPNode newNode = new EnhancedTTSPNode(destination, tourToHere, timeOfArrival, timeSinceLastShortBreak, timeSinceLastLongBreak);
						//						if (!expandedChildrenPerNode.containsKey(n)) {
						//							expandedChildrenPerNode.put(n, new ShortArrayList());
						//						}
						//						expandedChildrenPerNode.get(n).add(destination);
						return new NodeExpansionDescription<EnhancedTTSPNode, String>(n, newNode, n.getCurLocation() + " -> " + destination, NodeType.OR);
					}

					@Override
					public NodeExpansionDescription<EnhancedTTSPNode, String> generateSuccessor(final EnhancedTTSPNode node, final int i) {
						ShortList availableDestinations = this.getPossibleDestinationsThatHaveNotBeenGeneratedYet(node);
						return this.generateSuccessor(node, availableDestinations.getShort(i % availableDestinations.size()));
					}

					@Override
					public boolean allSuccessorsComputed(final EnhancedTTSPNode node) {
						return this.getPossibleDestinationsThatHaveNotBeenGeneratedYet(node).size() == 0;
					}
				};
			}

			private double getTimeToNextShortBreak(final double time, final double timeSinceLastBreak) {
				return EnhancedTTSP.this.maxConsecutiveDrivingTime - timeSinceLastBreak;
			}

			private double getTimeToNextLongBreak(final double time, final double timeSinceLastLongBreak) {
				return Math.min(this.getTimeToNextBlock(time), EnhancedTTSP.this.maxDrivingTimeBetweenLongBreaks - timeSinceLastLongBreak);
			}

			private double getTimeToNextBlock(final double time) {
				double timeToNextBlock = Math.ceil(time) - time;
				while (!EnhancedTTSP.this.blockedHours.get((int) Math.round(time + timeToNextBlock) % EnhancedTTSP.this.numberOfConsideredHours)) {
					timeToNextBlock++;
				}
				return timeToNextBlock;
			}

			public double getActualDrivingTimeWithoutBreak(final double minTravelTime, final double departure, final double shareOfTheTripDone) {
				int t = (int) Math.round(departure);
				double travelTime = minTravelTime * (1 - shareOfTheTripDone);
				int departureTimeRelativeToSevenNineInterval = t > 9 ? t - 24 : t;
				double startSevenNineSubInterval = Math.max(7, departureTimeRelativeToSevenNineInterval);
				double endSevenNineSubInterval = Math.min(9, departureTimeRelativeToSevenNineInterval + travelTime);
				double travelTimeInSevenToNineSlot = Math.max(0, endSevenNineSubInterval - startSevenNineSubInterval);
				// logger.info("Travel time in 7-9 slot: " + travelTimeInSevenToNineSlot
				// + ". Increasing travel time by " + travelTimeInSevenToNineSlot * 0.2);
				travelTime += travelTimeInSevenToNineSlot * 0.5;

				int departureTimeRelativeToFourSixInterval = t > 18 ? t - 24 : t;
				double startFourSixSubInterval = Math.max(16, departureTimeRelativeToFourSixInterval);
				double endFourSixSubInterval = Math.min(18, departureTimeRelativeToFourSixInterval + travelTime);
				double travelTimeInFourToSixSlot = Math.max(0, endFourSixSubInterval - startFourSixSubInterval);
				// logger.info("Increasing travel time by " + travelTimeInFourToSixSlot);
				travelTime += travelTimeInFourToSixSlot * 0.5;
				return travelTime;
			}

			public double getShareOfTripWhenDrivingOverEdgeAtAGivenTimeForAGivenTimeWithoutDoingABreak(final double minTravelTime, final double departureOfCurrentPoint, final double shareOfTripDone,
					final double drivingTime) {

				/* do a somewhat crude numeric approximation */
				double minTravelTimeForRest = minTravelTime * (1 - shareOfTripDone);
				logger.info("\t\tMin travel time for rest: " + minTravelTimeForRest);
				double doableMinTravelTimeForRest = minTravelTimeForRest;
				double estimatedTravelingTimeForThatPortion;
				while ((estimatedTravelingTimeForThatPortion = this.getActualDrivingTimeWithoutBreak(doableMinTravelTimeForRest, departureOfCurrentPoint, shareOfTripDone)) > drivingTime) {
					doableMinTravelTimeForRest -= 0.01;
				}
				logger.info("\t\tDoable min travel time for rest: " + doableMinTravelTimeForRest + ". The estimated true travel time for that portion is " + estimatedTravelingTimeForThatPortion);
				double additionalShare = (doableMinTravelTimeForRest / minTravelTimeForRest) * (1 - shareOfTripDone);
				logger.info("\t\tAdditional share:" + additionalShare);
				return shareOfTripDone + additionalShare;
			}

			@Override
			public NodeGoalTester<EnhancedTTSPNode> getGoalTester() {
				return n -> {
					return n.getCurTour().size() >= EnhancedTTSP.this.possibleDestinations.size() && n.getCurLocation() == EnhancedTTSP.this.startLocation;
				};
			}

			@Override
			public boolean isSelfContained() {
				return true;
			}

			@Override
			public void setNodeNumbering(final boolean nodenumbering) {
			}
		};
	}

	public ISolutionEvaluator<EnhancedTTSPNode, Double> getSolutionEvaluator() {
		return new ISolutionEvaluator<EnhancedTTSPNode, Double>() {

			@Override
			public Double evaluateSolution(final List<EnhancedTTSPNode> solutionPath) {
				if (solutionPath == null || solutionPath.size() == 0) {
					return Double.MAX_VALUE;
				}
				return solutionPath.get(solutionPath.size() - 1).getTime() - EnhancedTTSP.this.hourOfDeparture;
			}

			@Override
			public boolean doesLastActionAffectScoreOfAnySubsequentSolution(final List<EnhancedTTSPNode> partialSolutionPath) {
				return true;
			}

			@Override
			public void cancel() {
				/* nothing to do here */
			}
		};
	}

	public LabeledGraph<Short, Double> getMinTravelTimesGraph() {
		return this.minTravelTimesGraph;
	}

	public static EnhancedTTSP createRandomProblem(final int problemSize, final long seed) {
		Random random = new Random(seed);
		LabeledGraph<Short, Double> minTravelTimesGraph = new LabeledGraph<>();
		List<Pair<Double, Double>> coordinates = new ArrayList<>();
		for (short i = 0; i < problemSize; i++) {
			coordinates.add(new Pair<>(random.nextDouble() * 12, random.nextDouble() * 12));
			minTravelTimesGraph.addItem(i);
		}
		for (short i = 0; i < problemSize; i++) {
			double x1 = coordinates.get(i).getX();
			double y1 = coordinates.get(i).getY();
			for (short j = 0; j < i; j++) {
				double x2 = coordinates.get(j).getX();
				double y2 = coordinates.get(j).getY();
				double minTravelTime = Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
				minTravelTimesGraph.addEdge(i, j, minTravelTime);
				minTravelTimesGraph.addEdge(j, i, minTravelTime);
			}
		}
		;
		List<Boolean> blockedHours = Arrays.asList(
				new Boolean[] { true, true, true, true, true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, true, true });
		double maxConsecutiveDrivingTime = random.nextInt(5) + 5;
		double durationOfShortBreak = random.nextInt(3) + 3;
		double durationOfLongBreak = random.nextInt(6) + 6;
		return new EnhancedTTSP(minTravelTimesGraph, (short) 0, blockedHours, 8, maxConsecutiveDrivingTime, durationOfShortBreak, durationOfLongBreak);
	}
}
