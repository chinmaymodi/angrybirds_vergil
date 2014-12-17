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
import ab.vision.VisionMBR;
import ab.vision.ABType;
//Naive agent (server/client version)

public class ClientNaiveAgent implements Runnable {

    //Wrapper of the communicating messages
    private ClientActionRobotJava ar;
    public byte currentLevel = -1;
    public int failedCounter = 0;
    public int[] solved;
    TrajectoryPlanner tp;
    private int id = 2708;
    private boolean firstShot;
    private Point prevTarget;
    private Random randomGenerator;

    /**
     * Constructor using the default IP
     */
    public ClientNaiveAgent() {
        // the default ip is the localhost
        System.out.println("LOCALHOST.\n");
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
        System.out.println(ip + "\n");
        ar = new ClientActionRobotJava(ip);
        tp = new TrajectoryPlanner();
        randomGenerator = new Random();
        prevTarget = null;
        firstShot = true;
    }

    public ClientNaiveAgent(String ip, int id) {
        System.out.println("ip=" + ip + ", id=" + id + ".\n");
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
        //System.out.println(" My score: ");
        int level = 1;
        for (int i : scores) {
            //System.out.println(" level " + level + "  " + i);
            if (i > 0)
                solved[level - 1] = 1;
            level++;
        }
    }

    private int checkMyScore(int id) {
        int answer = 0;
        int[] scores = ar.checkMyScore();
        answer = scores[id - 1];
        System.out.println("Score of level " + id + ": " + answer);
        return answer;
    }

    public void run() {
        byte[] info = ar.configure(ClientActionRobot.intToByteArray(id));
        solved = new int[info[2]];

        System.out.println("Team vergil: Chinmay Modi, 200901039\nTeam id = " + id + ".\n");
        //System.out.println("Attempt 1\n");

        //load the initial level (default 1)
        //Check my score
        checkMyScore();
        //System.out.println("Got score, now get next level\n");
        currentLevel = (byte) getNextLevel();
        //System.out.println("Got next level, now load it\n");
        //currentLevel = 4;
        byte temp = ar.loadLevel(currentLevel);
        //byte temp = ar.loadLevel((byte)4);
        //System.out.println("Loaded level, now attempting to analyze state\n");
        GameState state;
        while (true) {
            //System.out.println("Attempting to solve level number " + currentLevel + ".\n");

            state = solve();
            //Make it run other algorithm where it wins but with bad scores
            if(state == GameState.WON) {
                int temp1 = checkMyScore(currentLevel);
                if((satisfactory == false) && (firsttry == false)) {
                    //Do nothing since all strategies failed
                    //System.out.println("We are doomed, all strategies failed :(.\nGoing to next level now.\n");
                    System.out.println("Strategy2 failed to get 3 stars, got score: " + temp1 + ".\n");
                    System.out.println("Both strategies failed at scoring 3 stars.");
                    satisfactory = true;
                    firsttry = true;
                    System.out.println("Set firsttry and satisfactory to true.");
                }
                else if(star3score[currentLevel - 1] > temp1) {
                    if(firsttry == true) {
                        firsttry = false;
                        System.out.println("Strategy1 failed to get 3 stars, got score: " + temp1 + ".\nTrying strategy2 now.\n");
                        satisfactory = false;
                        System.out.println("Set firsttry and satisfactory to false.");
                    }
                }
                else {
                    satisfactory = true;
                    int best = maxscore[currentLevel - 1];
                    double percent = ((double)temp1 / (double)best)*100.0;
                    System.out.println("Maximum score is " + best + ".\nWe scored " + temp1 + ".\nWe scored " + percent + "% of the maximum score!.\n");
                    System.out.println("Got " + temp1 + " score.");
                }
            }
            //If the level is solved , go to the next level
            if ((state == GameState.WON) && (satisfactory == true)) {
                firsttry = true;
                System.out.println("Loading next level now.\n");

                System.out.println(" loading the level " + (currentLevel + 1) );
                checkMyScore();
                currentLevel = (byte) getNextLevel();
                ar.loadLevel(currentLevel);
                //ar.loadLevel((byte)9);
                //display the global best scores
                //int[] scores = ar.checkScore();
                //System.out.println("Global best score: ");
                /*for (int i = 0; i < scores.length; i++) {

                    System.out.print(" level " + (i + 1) + ": " + scores[i]);
                }*/

                // make a new trajectory planner whenever a new level is entered
                tp = new TrajectoryPlanner();

                // first shot on this level, try high shot first
                firstShot = true;

            }
            //If lost, then restart the level
            else if (state == GameState.LOST) {
                    System.out.println("We lost because of GameState.LOST");
                    failedCounter++;
                    if (failedCounter > 2) {
                        System.out.println("Too much failure, abandoning this level.");
                        failedCounter = 0;
                        currentLevel = (byte) getNextLevel();
                        ar.loadLevel(currentLevel);

                        //ar.loadLevel((byte)9);
                    } else {
                        System.out.println("We can still try, restarting level.");
                        System.out.println("restart");
                        ar.restartLevel();
                    }

                }
                else if (state == GameState.WON && satisfactory == false) {
                    System.out.println("We cleared the level but without a 3 star score, so trying again.");
                    failedCounter++;
                    if (failedCounter > 2) {
                        System.out.println("Too much failure, abandoning this level.");
                        failedCounter = 0;
                        currentLevel = (byte) getNextLevel();
                        ar.loadLevel(currentLevel);

                        //ar.loadLevel((byte)9);
                    } else {
                        System.out.println("We can still try, restarting level.");
                        System.out.println("restart");
                        ar.restartLevel();
                    }
                }
                else if (state == GameState.LEVEL_SELECTION) {
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
        satisfactory = true;
        // capture Image
        screenshot = ar.doScreenShot();
        //System.out.println("Got BufferedImage screenshot\n");

        // process image
        vision = new VisionMBR(screenshot);

        Rectangle sling = vision.findSlingshotMBR();
        //System.out.println("Got sling info\n");

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
            vision = new VisionMBR(screenshot);
            sling = vision.findSlingshotMBR();
        }


        // get all the pigs
        recalc();
        //List<ABObject> tnt = vision.findTNTs();
        int i;
        for (i = 0; i < pigs.size(); i++)
            //System.out.println("Pig no. " + (i + 1) + " of type " + pigs.get(i).getType() + ".\n");
        for (i = 0; i < blocks.size(); i++) {
            //System.out.println("Block no. " + (i + 1) + " of type " + blocks.get(i).getType() + ", which has weight " + weight.get(i) + ".\n" +
            //        "Shape is " + blocks.get(i).shape);
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
                ABObject tgt = null;
                if(firsttry == true) {
                    System.out.println("Getting pig according to strategy 1.\n");
                    tgt = target1();
                }
                //TODO: Implement alternate strategy
                else {
                    System.out.println("Getting pig according to strategy 2.\n");
                    tgt = target2();
                }
                //ABObject pig = pigs.get(randomGenerator.nextInt(pigs.size()));

                Point _tpt = tgt.getCenter();


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
                //System.out.println("Target is at x = " + _tpt.x + ", y = " + _tpt.y + "\n");

                // estimate the trajectory
                ArrayList<Point> pts = tp.estimateLaunchPoint(sling, _tpt);

                // do a high shot when entering a level to find an accurate velocity
                releasePoint = pts.get(0);
                //What follows is the original code, we discard it since it randomly picks shots which is not good
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
                    //System.out.println("Release Point: " + releasePoint);
                    //System.out.println("Release Angle: " + Math.toDegrees(releaseAngle));
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
                            Point newtgt = new Point((_tpt.x - 10), (_tpt.y - 200));
                            pts = tp.estimateLaunchPoint(sling, newtgt);
                            releasePoint = pts.get(0);
                            releaseAngle = tp.getReleaseAngle(sling, releasePoint);
                            System.out.println("White bird changes rules, making it drop egg from 200 height above object, 10 units to the left.\n");
                            System.out.println("Release Point: " + releasePoint);
                            System.out.println("Release Angle: "
                                    + Math.toDegrees(releaseAngle));
                            break; // almost on top
                        case BlackBird:
                            tapInterval = 99;
                            break; // end of the way
                        case BlueBird:
                            tapInterval = 90 + randomGenerator.nextInt(5);
                            break; // 90-95% of the way
                        default:
                            tapInterval = 60;
                    }

                    tapTime = tp.getTapTime(sling, releasePoint, _tpt, tapInterval);
                    //System.out.println("Got taptime: " + tapTime);

                } else {
                    System.err.println("No Release Point Found");
                    return ar.checkState();
                }


                // check whether the slingshot is changed. the change of the slingshot indicates a change in the scale.
                //System.out.println("Taking shot now\n");
                ar.fullyZoomOut();
                screenshot = ar.doScreenShot();
                vision = new VisionMBR(screenshot);
                Rectangle _sling = vision.findSlingshotMBR();
                if (_sling != null) {
                    double scale_diff = Math.pow((sling.width - _sling.width), 2) + Math.pow((sling.height - _sling.height), 2);
                    if (scale_diff < 25) {
                        int dx = (int) releasePoint.getX() - refPoint.x;
                        int dy = (int) releasePoint.getY() - refPoint.y;
                        if (dx < 0) {
                            long timer = System.currentTimeMillis();
                            ar.shoot(refPoint.x, refPoint.y, dx, dy, 0, tapTime, false);
                            //System.out.println("It takes " + (System.currentTimeMillis() - timer) + " ms to take a shot");
                            state = ar.checkState();
                            if (state == GameState.PLAYING) {
                                screenshot = ar.doScreenShot();
                                vision = new VisionMBR(screenshot);
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
    //@author: V3rG1L

    //private Vision vision;
    private VisionMBR vision;
    private BufferedImage screenshot;

    private double avgwt;
    private List<ABObject> pigs;
    private List<ABObject> blocks;
    private List<Integer> weight = new ArrayList<Integer>();
    private List<Integer> weight1 = new ArrayList<Integer>();

    private boolean firsttry = true;
    private boolean satisfactory = true;

    //List of 3 star scores, and maximum scores found on http://www.angrybirdsnest.com
    private int[] star3score = new int[] {32000, 60000, 41000, 28000, 64000, 35000, 45000, 50000, 50000, 55000, 54000, 45000, 47000, 70000, 41000, 64000, 53000, 48000, 35000, 50000, 75000};
    private int[] maxscore = new int[]{36870, 63780, 50160, 42650, 77330, 46770, 56690, 65940, 61200, 80470, 64920, 65540, 58950, 87280, 58580, 75360, 63390, 70200, 44610, 73870, 103570};

    //TODO: Get better weights algorithm
    private void clcwt() {
        //System.out.println("Strategy 1 weight calculation.\n");
        clcwt1();
        int i;
        avgwt = 0.0;
        weight.clear();
        int l = blocks.size();
        ABType birdy = ar.getBirdTypeOnSling();
        //t.println("Assigning initial 0 weights before calculation\n");
        for (i = 0; i < l; i++) {
            weight.add(0);
            //System.out.println("Position of center of block " + i + " is: x = " + blocks.get(i).getCenterX() + ", y = " + blocks.get(i).getCenterY() + "\n");
        }
        //System.out.println("Total blocks = " + i + "\n");
        //System.out.println("Calculating weights now\n");
        for(i = 0; i < l; i++) {
            //TODO: Better way of sorting object boundaries to get more accurate weights
            double x = blocks.get(i).getCenterX();
            double y = blocks.get(i).getCenterY();
            int wt = 0;
            for(int j = 0; j < pigs.size(); j++) {
                double xj = pigs.get(j).getCenterX();
                double xy = pigs.get(j).getCenterY();
                if(j != i) {
                    if(xj - x < 10.0) {
                        if(xy - y < 20.0 && xy - y > -20.0) {
                            wt = wt + 10;
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
            int str = 0;
            switch (birdy) {
                case BlueBird:
                    switch (blocks.get(i).getType()) {
                        case Ice:
                            str = 4;
                            break;
                        case Stone:
                            str = 1;
                            break;
                        default:
                            str = 2;
                    }
                    break; // 85-90% of the way
                default:
                    str = 2;
            }
            //System.out.println("Strength of block is " + str + ".\n");
            wt = (wt * (weight1.get(i))) * str;
            weight.set(i, wt);
            avgwt += wt;
        }
        //for(i = 0; i < l; i++ ) System.out.println("Weight of block " + i + " is " + weight.get(i));
        avgwt = avgwt/l;
        //System.out.println("Average weight of blocks is " + avgwt + "\n");
    }

    private void clcwt1 (){
        int i = 0;
        int l = blocks.size();
        weight1.clear();
        for(i = 0; i < l; i++) {
            weight1.add(0);
        }
        int x = 0;
        int y = 0;
        for(i = 0; i < l; i++) {
            x = blocks.get(i).width;
            y = blocks.get(i).height;
            //System.out.println("Block id " + i + " has height, weight = " + y + "," + x + ".\n");
            if(x > (2*y)) {
                //System.out.println("Block " + i + " is a wide rectangle.");
                weight1.set(i, 1);
            }
            else if(y > (2*x)) {
                //System.out.println("Block " + i + " is a tall rectangle.");
                weight1.set(i, 5);
            }
            else {
                //System.out.println("Block " + i + " is neither tall nor wide.");
                weight1.set(i, 3);
            }
        }
    }

    private void getpigs() {
        pigs = vision.findPigs();
    }

    private void getblocks() {
        blocks = vision.findBlocks();
    }

    private void recalc() {
        getpigs();
        getblocks();
    }

    //TODO: pick better targets
    private ABObject target1() {
        clcwt();
        ABObject answer;
        int id = 0;
        int wt = weight.get(0);
        int l = blocks.size();
        for(int i = 1; i < l; i++) {
            int wi = weight.get(i);
            if (wi <= avgwt && wi >= wt) {
                //System.out.println("Weight of block " + i + " = " + wi + " is <= average, but >= current target " + id + " = " + wt + ".\n");
                if(blocks.get(i).getCenterY() < blocks.get(id).getCenterY()) {
                    if(blocks.get(i).getCenterX() < blocks.get(id).getCenterX()) {
                        id = i;
                        wt = wi;
                        //System.out.println("Block is above and to left of previous target.\n");
                    }
                    else {
                        //System.out.println("Block is above but not to left of previous target.\n");
                        if(wi >= 2*wt) {
                            id = i;
                            wt = wi;
                        }
                    }
                }
                else {
                    //System.out.println("Block is not above or left of previous target.\n");
                    if(wi >= 2*wt) {
                        id = i;
                        wt = wi;
                    }
                }
            }
            else if(wi > avgwt && wi > wt) {
                //System.out.println("Weight of block " + i + " = " + wi + " is more then current target " + id + " = " + wt + ".\n");
                id = i;
                wt = wi;
                //System.out.println("Changed target to " + i + ".\n");
            }
        }
        if (wt == -1) {
            //System.out.println("No good block found, returning first pig\n");
            answer = pigs.get(0);
        }
        else {
            //System.out.println("Found good block, returning it\n");
            answer = blocks.get(id);

        }

        return answer;
    }

    private ABObject target2() {
        clcwt3();
        int id = 0;
        int wt = -1;
        int l = blocks.size();
        for(int i = 1; i < l; i++) {
            int wi = weight.get(i);
            if (wi <= avgwt && wi> wt) {
                if((blocks.get(i).getCenterY() < blocks.get(id).getCenterY()) && (blocks.get(i).getCenterX() < blocks.get(id).getCenterX())) {
                    id = i;
                    wt = wi;
                }
            }
            else if(wi > avgwt && wi > wt) {
                //System.out.println("Weight of block " + i + " = " + wi + " is more then current target " + id + " = " + wt + ".\n");
                id = i;
                wt = wi;
                //System.out.println("Changed target to " + i + ".\n");
            }
        }
        if (wt == -1) {
            //System.out.println("No good block found, returning first pig\n");
            return pigs.get(0);
        }
        //System.out.println("Found good block, returning it\n");
        return blocks.get(id);
    }

    private void clcwt3() {
        //System.out.println("Strategy 2 weight calculation.\n");
        int i;
        avgwt = 0.0;
        int l = blocks.size();
        weight.clear();
//        System.out.println("Assigning initial 0 weights before calculation\n");
        for (i = 0; i < l; i++) {
            weight.add(0);
//            System.out.println("Position of center of block " + i + " is: x = " + blocks.get(i).getCenterX() + ", y = " + blocks.get(i).getCenterY() + "\n");
        }
//        System.out.println("Total blocks = " + i + "\n");
//        System.out.println("Calculating weights now\n");
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
                            wt = wt + 10;
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
        //for(i = 0; i < l; i++ ) System.out.println("Weight of block " + i + " is " + weight.get(i));
        avgwt = avgwt/l;
        //System.out.println("Average weight of blocks is " + avgwt + "\n");
    }
}