/*****************************************************************************
 ** ANGRYBIRDS AI AGENT FRAMEWORK
 ** Copyright (c) 2014, XiaoYu (Gary) Ge, Stephen Gould, Jochen Renz
 **  Sahan Abeyasinghe,Jim Keys,  Andrew Wang, Peng Zhang
 ** All rights reserved.
**This work is licensed under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
**To view a copy of this license, visit http://www.gnu.org/licenses/
 *****************************************************************************/
package ab.demo;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.lang.Object;
import java.util.Arrays;

import ab.demo.other.ClientActionRobot;
import ab.demo.other.ClientActionRobotJava;
import ab.planner.TrajectoryPlanner;
import ab.vision.ABObject;
import ab.vision.GameStateExtractor.GameState;
import ab.vision.Vision;
//Naive agent (server/client version)

public class ClientNaiveAgent implements Runnable {


    //Wrapper of the communicating messages
    private ClientActionRobotJava ar;
    public byte currentLevel = -1;
    public int failedCounter = 0;
    public int[] solved;
    TrajectoryPlanner tp;
    private int id = 28888;
    private boolean firstShot;
    private Point prevTarget;
    private Random randomGenerator;

    /**
     * Constructor using the default IP
     */
    public ClientNaiveAgent() {
        // the default ip is the localhost
        ar = new ClientActionRobotJava("127.0.0.1");
        tp = new TrajectoryPlanner();
        randomGenerator = new Random();
        prevTarget = null;
        firstShot = true;

    }

    /**
     * Constructor with a specified IP
     */
    public ClientNaiveAgent(String ip) {
        ar = new ClientActionRobotJava(ip);
        tp = new TrajectoryPlanner();
        randomGenerator = new Random();
        prevTarget = null;
        firstShot = true;

    }

    public ClientNaiveAgent(String ip, int id) {
        ar = new ClientActionRobotJava(ip);
        tp = new TrajectoryPlanner();
        randomGenerator = new Random();
        prevTarget = null;
        firstShot = true;
        this.id = id;
    }

    public int getNextLevel() {
        int level = 0;
        boolean unsolved = false;
        //all the level have been solved, then get the first unsolved level
        for (int i = 0; i < solved.length; i++) {
            if (solved[i] == 0) {
                unsolved = true;
                level = i + 1;
                if (level <= currentLevel && currentLevel < solved.length)
                    continue;
                else
                    return level;
            }
        }
        if (unsolved)
            return level;
        level = (currentLevel + 1) % solved.length;
        if (level == 0)
            level = solved.length;
        return level;
    }

    /*
     * Run the Client (Naive Agent)
     */
    private void checkMyScore() {

        int[] scores = ar.checkMyScore();
        System.out.println(" My score: ");
        int level = 1;
        for (int i : scores) {
            System.out.println(" level " + level + "  " + i);
            if (i > 0)
                solved[level - 1] = 1;
            level++;
        }
    }

    public void run() {
        byte[] info = ar.configure(ClientActionRobot.intToByteArray(id));
        solved = new int[info[2]];

        System.out.println("Team vergil: Chinmay Modi, 200901039\n");
        System.out.println("Attempt 101\n");

        //load the initial level (default 1)
        //Check my score
        checkMyScore();
        //System.out.println("Got score, now get next level\n");
        currentLevel = (byte) getNextLevel();
        //System.out.println("Got next level, now load it\n");
        byte temp = ar.loadLevel(currentLevel);
        //byte temp = ar.loadLevel((byte)3);
        //System.out.println("Loaded level, now attempting to analyze state\n");
        GameState state;
        while (true) {

            state = solve();
            //If the level is solved , go to the next level
            if (state == GameState.WON) {

                ///System.out.println(" loading the level " + (currentLevel + 1) );
                checkMyScore();
                System.out.println();
                currentLevel = (byte) getNextLevel();
                ar.loadLevel(currentLevel);
                //ar.loadLevel((byte)9);
                //display the global best scores
                int[] scores = ar.checkScore();
                System.out.println("Global best score: ");
                for (int i = 0; i < scores.length; i++) {

                    System.out.print(" level " + (i + 1) + ": " + scores[i]);
                }
                System.out.println();

                // make a new trajectory planner whenever a new level is entered
                tp = new TrajectoryPlanner();

                // first shot on this level, try high shot first
                firstShot = true;

            } else
                //If lost, then restart the level
                if (state == GameState.LOST) {
                    failedCounter++;
                    if (failedCounter > 3) {
                        failedCounter = 0;
                        currentLevel = (byte) getNextLevel();
                        ar.loadLevel(currentLevel);

                        //ar.loadLevel((byte)9);
                    } else {
                        System.out.println("restart");
                        ar.restartLevel();
                    }

                } else if (state == GameState.LEVEL_SELECTION) {
                    System.out.println("unexpected level selection page, go to the last current level : "
                            + currentLevel);
                    ar.loadLevel(currentLevel);
                } else if (state == GameState.MAIN_MENU) {
                    System.out
                            .println("unexpected main menu page, reload the level : "
                                    + currentLevel);
                    ar.loadLevel(currentLevel);
                } else if (state == GameState.EPISODE_MENU) {
                    System.out.println("unexpected episode menu page, reload the level: "
                            + currentLevel);
                    ar.loadLevel(currentLevel);
                }

        }

    }


    /**
     * Solve a particular level by shooting birds directly to pigs
     *
     * @return GameState: the game state after shots.
     */
    public GameState solve()

    {
        //System.out.println("Beginning to solve this level.");

        // capture Image
        screenshot = ar.doScreenShot();
        //System.out.println("Got BufferedImage screenshot\n");

        // process image
        vision = new Vision(screenshot);

        Rectangle sling = vision.findSlingshotMBR();
        System.out.println("Got sling info\n");

        //If the level is loaded (in PLAYING　state)but no slingshot detected, then the agent will request to fully zoom out.
        while (sling == null && ar.checkState() == GameState.PLAYING) {
            System.out.println("no slingshot detected. Please remove pop up or zoom out");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

                e.printStackTrace();
            }
            ar.fullyZoomOut();
            screenshot = ar.doScreenShot();
            vision = new Vision(screenshot);
            sling = vision.findSlingshotMBR();
        }


        // get all the pigs
        recalc();
        //List<ABObject> tnt = vision.findTNTs();
        int i;
        for (i = 0; i < pigs.size(); i++)
            System.out.println("Pig no. " + (i + 1) + " of type " + pigs.get(i).getType() + ".\n");
        for (i = 0; i < blocks.size(); i++) {
            System.out.println("Block no. " + (i + 1) + " of type " + blocks.get(i).getType() + ", which has weight " + weight.get(i) + ".\n" +
                    "Shape is " + blocks.get(i).shape);
            /*if(blocks.get(i).shape == "Rect") {
                System.out.println("Rectangle shape, reying to determing angle and vertices\n");
            }*/
        }

        GameState state = ar.checkState();
        // if there is a sling, then play, otherwise skip.
        if (sling != null) {
            if (!pigs.isEmpty()) {
                Point releasePoint = null;
                // Get highest weighted block
                ABObject pig = target();
                //ABObject pig = pigs.get(randomGenerator.nextInt(pigs.size()));

                Point _tpt = pig.getCenter();


                // if the target is very close to before, randomly choose a
                // point near it
                if (prevTarget != null && distance(prevTarget, _tpt) < 10) {
                    double _angle = randomGenerator.nextDouble() * Math.PI * 2;
                    _tpt.x = _tpt.x + (int) (Math.cos(_angle) * 10);
                    _tpt.y = _tpt.y + (int) (Math.sin(_angle) * 10);
                    System.out.println("Randomly changing to " + _tpt);
                }

                if(ar.getBirdTypeOnSling().id == 8) _tpt.x = _tpt.x - 100;
                prevTarget = new Point(_tpt.x, _tpt.y);
                System.out.println("Target is at x = " + _tpt.x + ", y = " + _tpt.y + "\n");

                // estimate the trajectory
                ArrayList<Point> pts = tp.estimateLaunchPoint(sling, _tpt);

                // do a high shot when entering a level to find an accurate velocity
                releasePoint = pts.get(0);
                /*if (firstShot && pts.size() > 1) {
                    releasePoint = pts.get(1);
                } else if (pts.size() == 1)
                    releasePoint = pts.get(0);
                else if (pts.size() == 2) {
                    // System.out.println("first shot " + firstShot);
                    // randomly choose between the trajectories, with a 1 in
                    // 6 chance of choosing the high one
                    if (randomGenerator.nextInt(6) == 0)
                        releasePoint = pts.get(1);
                    else
                        releasePoint = pts.get(0);
                }*/
                Point refPoint = tp.getReferencePoint(sling);

                // Get the release point from the trajectory prediction module
                int tapTime = 0;
                if (releasePoint != null) {
                    double releaseAngle = tp.getReleaseAngle(sling,
                            releasePoint);
                    System.out.println("Release Point: " + releasePoint);
                    System.out.println("Release Angle: "
                            + Math.toDegrees(releaseAngle));
                    int tapInterval = 0;
                    switch (ar.getBirdTypeOnSling()) {

                        case RedBird:
                            tapInterval = 0;
                            break;               // start of trajectory
                        case YellowBird:
                            tapInterval = 85 + randomGenerator.nextInt(5);
                            break; // 85-90% of the way
                        case WhiteBird:
                            tapInterval = 100;
                            break; // on top
                        case BlackBird:
                            tapInterval = 0;
                            break; // 70-90% of the way
                        case BlueBird:
                            tapInterval = 85 + randomGenerator.nextInt(5);
                            break; // 85-90% of the way
                        default:
                            tapInterval = 60;
                    }

                    tapTime = tp.getTapTime(sling, releasePoint, _tpt, tapInterval);
                    System.out.println("Got taptime: " + tapTime);

                } else {
                    System.err.println("No Release Point Found");
                    return ar.checkState();
                }


                // check whether the slingshot is changed. the change of the slingshot indicates a change in the scale.
                System.out.println("Taking shot now\n");
                ar.fullyZoomOut();
                screenshot = ar.doScreenShot();
                vision = new Vision(screenshot);
                Rectangle _sling = vision.findSlingshotMBR();
                if (_sling != null) {
                    double scale_diff = Math.pow((sling.width - _sling.width), 2) + Math.pow((sling.height - _sling.height), 2);
                    if (scale_diff < 25) {
                        int dx = (int) releasePoint.getX() - refPoint.x;
                        int dy = (int) releasePoint.getY() - refPoint.y;
                        if (dx < 0) {
                            long timer = System.currentTimeMillis();
                            ar.shoot(refPoint.x, refPoint.y, dx, dy, 0, tapTime, false);
                            System.out.println("It takes " + (System.currentTimeMillis() - timer) + " ms to take a shot");
                            state = ar.checkState();
                            if (state == GameState.PLAYING) {
                                screenshot = ar.doScreenShot();
                                vision = new Vision(screenshot);
                                List<Point> traj = vision.findTrajPoints();
                                tp.adjustTrajectory(traj, sling, releasePoint);
                                firstShot = false;
                            }
                        }
                        recalc();
                    } else
                        System.out.println("Scale is changed, can not execute the shot, will re-segement the image");
                } else
                    System.out.println("no sling detected, can not execute the shot, will re-segement the image");

            }
        }
        return state;
    }

    private double distance(Point p1, Point p2) {
        return Math.sqrt((double) ((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y)));
    }

    public static void main(String args[]) {

        ClientNaiveAgent na;
        if (args.length > 0)
            na = new ClientNaiveAgent(args[0]);
        else
            na = new ClientNaiveAgent();
        na.run();

    }

    //My own stuff

    private Vision vision;
    private BufferedImage screenshot;

    private double avgwt;
    private List<ABObject> pigs;
    private List<ABObject> blocks;
    private List<Integer> weight = new ArrayList<Integer>();

    private void clcwt() {
        int i;
        avgwt = 0.0;
        int l = blocks.size();
        System.out.println("Assigning initial 0 weights before calculation\n");
        for (i = 0; i < l; i++) {
            weight.add(0);
            System.out.println("Position of center of block " + i + " is: x = " + blocks.get(i).getCenterX() + ", y = " + blocks.get(i).getCenterY() + "\n");
        }
        System.out.println("Total blocks = " + i + "\n");
        System.out.println("Calculating weights now\n");
        for(i = 0; i < l; i++) {
            //TODO: Better way of sorting object boundaries to get more accurate weights
            double x = blocks.get(i).getCenterX();
            double y = blocks.get(i).getCenterY();
            int wt = 0;
            for(int j = 0; j < pigs.size(); j++) {
                double xj = pigs.get(j).getCenterX();
                double xy = pigs.get(j).getCenterY();
                if(j != i) {
                    if(xj - x < 10.0 && xj - x > -10.0) {
                        if(xy - y < 20.0 && xy - y > -20.0) {
                            wt = wt + 5;
                            //System.out.println("Difference in distance is " + (xj - x) + " on x and " + (xy - y) + " on y\n");
                        }
                    }
                }
            }
            for(int j = 0; j < l; j++) {
                double xj = blocks.get(j).getCenterX();
                double xy = blocks.get(j).getCenterY();
                if(j != i) {
                    if(xj - x < 10.0 && xj - x > -10.0) {
                        if(xy - y < 20.0 && xy - y > -20.0) {
                            wt++;
                            //System.out.println("Difference in distance is " + (xj - x) + " on x and " + (xy - y) + " on y\n");
                        }
                    }
                }

            }
            weight.set(i, wt);
            avgwt += wt;
        }
        for(i = 0; i < l; i++ ) System.out.println("Weight of block " + i + " is " + weight.get(i));
        avgwt = avgwt/l;
        System.out.println("Average weight of blocks is " + avgwt + "\n");
    }

    private void getpigs() {
        pigs = vision.findPigsMBR();
    }

    private void getblocks() {
        blocks = vision.findBlocksMBR();
    }
    private void recalc() {
        getpigs();
        getblocks();
        clcwt();
    }
    //TODO: pick better targets
    private ABObject target() {
        int id = 0;
        int wt = -1;
        int l = blocks.size();
        for(int i = 1; i < l; i++) {
            int wi = weight.get(i);
            if (wi >= avgwt && wi> wt) {
                if(blocks.get(i).getCenterY() > blocks.get(id).getCenterY()) {
                    id = i;
                    wt = wi;
                }
            }
        }
        if (wt == -1) {
            System.out.println("No good block found, returning first pig\n");
            return pigs.get(0);
        }
        System.out.println("Found good block, returning it\n");
        return blocks.get(id);
    }
}