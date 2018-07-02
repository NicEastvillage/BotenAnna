package botenanna.game;

import botenanna.math.RLMath;
import botenanna.math.Vector2;
import botenanna.math.Vector3;
import botenanna.math.zone.Box;
import botenanna.physics.Rigidbody;
import botenanna.physics.SimplePhysics;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.BoostPadState;
import rlbot.flat.FieldInfo;
import rlbot.flat.GameTickPacket;

import java.io.IOException;

/** The Situation is a data class that represents a situation in the game, partly constructed from the
 * GameTickPacket and our own calculations. */
public class Situation {

    public final int myPlayerIndex;
    public final int enemyPlayerIndex;

    private final Car myCar;
    private final Car enemyCar;

    private final Rigidbody ball;
    private final double ballLandingTime;
    private final Vector3 ballLandingPosition;

    private final boolean isKickOffPause;
    private final boolean isMatchOver;
    private final boolean isOvertime;
    private final boolean isRoundActive;
    private final int gamePlayerCount;
    private final Boostpad[] boostpads;

    private GameTickPacket packet;

    private Boostpad bestBoostpad = null;
    private double myPossessionUtility = 0;
    private double enemyPossessionUtility = 0;
    private boolean enemyHasPossession;

    /** Create a Situation from GameTickPacket. */
    public Situation(GameTickPacket packet, int playerIndex) {
        this.packet = packet;
        this.boostpads = constructBoostpadArray(packet);

        // Cars
        myPlayerIndex = playerIndex;
        myCar = new Car(myPlayerIndex, packet);
        enemyPlayerIndex = this.myPlayerIndex == 1 ? 0 : 1;
        enemyCar = new Car(enemyPlayerIndex, packet);

        // Ball
        this.ball = Ball.get(packet.ball());
        double landingTime = SimplePhysics.predictArrivalAtHeight(ball, Ball.RADIUS, true);
        if (Double.isNaN(landingTime)) {
            this.ballLandingTime = 0;
            this.ballLandingPosition = ball.getPosition();
        } else {
            this.ballLandingTime = landingTime;
            this.ballLandingPosition = SimplePhysics.step(ball.clone(), ballLandingTime, true).getPosition();
        }

        // Game
        this.isKickOffPause = packet.gameInfo().isKickoffPause();
        this.isMatchOver = packet.gameInfo().isMatchEnded();
        this.isOvertime = packet.gameInfo().isOvertime();
        this.isRoundActive = packet.gameInfo().isRoundActive();
        this.gamePlayerCount = packet.playersLength();

        decidePossession();
    }

    /** Create Situation by providing the pieces. */
    public Situation(Car car, Car enemyCar, Rigidbody ball, Boostpad[] boostpads) {
        this.myPlayerIndex = car.getPlayerIndex();
        this.enemyPlayerIndex = enemyCar.getPlayerIndex();
        this.myCar = car;
        this.enemyCar = enemyCar;
        this.ball = ball;
        this.boostpads = boostpads;

        // Ball landing
        double landingTime = SimplePhysics.predictArrivalAtHeight(ball, Ball.RADIUS, true);
        if (Double.isNaN(landingTime)) {
            this.ballLandingTime = 0;
            this.ballLandingPosition = ball.getPosition();
        } else {
            this.ballLandingTime = landingTime;
            this.ballLandingPosition = SimplePhysics.step(ball.clone(), ballLandingTime, true).getPosition();
        }

        // TODO Currently no way to determine if we have entered a new phase
        this.isKickOffPause = false;
        this.isMatchOver = false;
        this.isOvertime = false;
        this.isRoundActive = true;
        this.gamePlayerCount = 2;

        decidePossession();
    }

    /** Construct an array of boost pads from the packet's list of BoostpadInfo. */
    private Boostpad[] constructBoostpadArray(GameTickPacket packet) {
        int count = packet.boostPadStatesLength();
        Boostpad[] boostpads = new Boostpad[count];

        try {
            FieldInfo fieldInfo = RLBotDll.getFieldInfo();

            for (int i = 0; i < count; i++) {
                BoostPadState state = packet.boostPadStates(i);
                boostpads[i] = new Boostpad(fieldInfo.boostPads(i), state);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return boostpads;
    }

    /** Used to get the best boostpad based on utility theory.
     * @return the best boostpad for myCar. */
    public Boostpad getBestBoostPad() {
        // Already calculated?
        if (bestBoostpad != null) {
            return bestBoostpad;
        }

        double bestBoostUtility = 0;
        Boostpad bestpad = null;

        for (int i = 0; i < Boostpad.COUNT_TOTAL_PADS; i++) {

            Boostpad pad = boostpads[i];
            Vector3 position = pad.getPosition();

            if (pad.isActive()){
                double angleToBoost = RLMath.carsAngleToPoint(new Vector2(myCar.getPosition()), myCar.getPosition().yaw, position.asVector2());
                double distance = myCar.getPosition().getDistanceTo(position);
                double distFunc = ((Arena.LENGTH - distance) / Arena.LENGTH);
                double newBoostUtility = (Math.cos(angleToBoost) * distFunc);

                if (newBoostUtility > bestBoostUtility){
                    bestBoostUtility = newBoostUtility;
                    bestpad = pad;
                }
            }
        }

        return bestBoostpad = bestpad;
    }

    private void decidePossession() {
        myPossessionUtility = possessionUtility(myCar);
        enemyPossessionUtility = possessionUtility(enemyCar);
        enemyHasPossession = myPossessionUtility < enemyPossessionUtility;
    }

    /** Returns true if car of playerIndex has ball possession. */
    public boolean hasPossession(int playerIndex){
        if (playerIndex == myPlayerIndex) {
            return !enemyHasPossession;
        } else {
            return enemyHasPossession;
        }
    }

    /** Helper function to calculate and return the possession utility of a given car
    * Currently weighed equally and therefore can be considered inaccurate. Requires more testing. */
    private double possessionUtility (Car car){
        double distanceUtility = 1 - car.getPosition().getDistanceTo(ball.getPosition()) / Arena.LENGTH;
        double angleUtility = Math.cos(car.getAngleToBall());
        double velocityUtility = car.getVelocity().getMagnitude() / Car.MAX_VELOCITY;

        // Returns the total utility
        return distanceUtility + angleUtility + velocityUtility;
    }

    /** @return true if point is behind car owned by playerIndex */
    public boolean isPointBehindCar(int playerIndex, Vector3 point){
        Car car = getCar(playerIndex);
        Vector3 vectorToPoint = point.minus(car.getPosition());
        double ang = car.getFrontVector().getAngleTo(vectorToPoint);
        return ang > Math.PI/2;
    }

    /** @return the GameTickPacket this was created from. Can be null. */
    public GameTickPacket getPacket() {
        return packet;
    }

    /** @return a players Car */
    public Car getCar(int index) {
        return index == myPlayerIndex ? myCar.clone() : enemyCar.clone();
    }

    public Car getMyCar() {
        return myCar.clone();
    }

    public Car getEnemyCar() {
        return enemyCar.clone();
    }

    /** @return time in seconds to expected collision between ball and my car  */
    public double getCollisionTime() {

        // TODO CLEAN UP THE CODE AND IMPROVE PREDICTION
        Vector3 expectedBall;
        double predictSeconds = 0;
        double predict = 0.02;
        double counter = 0.02;
        double velocity;
        boolean isBallStill = false;

        //If the ball is really slow or still, skip the loop and don't predict.
        if (10 > ball.getVelocity().getMagnitude()) {
            isBallStill = true;
        }

        //The loop will find a spot where the distance of expected ball to car minus the car velocity multiplied by predict is between -25 and 25.
        //That way the agent should always be able to choose the right amount of prediction seconds, although this will probably change a little bit every tick as
        //the car velocity changes.
        while (predictSeconds < 0.1 && counter <= 5 && !isBallStill) {
            expectedBall = ball.getVelocity().plus(ball.getVelocity().scale(predict));

            // If the car is not really driving, it should overextend its prediction to the future.
            if (myCar.getVelocity().getMagnitude() < 800) {
                velocity = 800;
            } else velocity = myCar.getVelocity().getMagnitude();

            if (-25 < expectedBall.minus(myCar.getPosition().plus(myCar.getFrontVector().scale(70))).getMagnitude() - velocity * predict && expectedBall.minus(myCar.getPosition().plus(myCar.getFrontVector().scale(70))).getMagnitude() - velocity * predict < 25) {
                predictSeconds = predict;
            }

            predict += 0.02;
            counter += 0.02;
        }
        // If it runs through loop without choosing one, then don't predict (Probably not needed)
        if (counter > 5) {
            predictSeconds = 0;
        }
        // if ball is still, don't predict
        if (isBallStill) {
            predictSeconds = 0;
        }
        return predictSeconds;
    }

    /** Returns true if the ball is near a wall */
    public boolean isBallNearWall() {
        Vector3 point = ball.getPosition();
        Box field = Arena.getFieldWithWallOffset(Ball.RADIUS * 3);
        return !field.isPointInBoxArea(point);
    }

    /** Returns true if the car is near or on a wall
     * @param playerIndex owner of the car */
    public boolean isCarNearWall(int playerIndex) {
        return getCar(playerIndex).isNearWall();
    }

    public Rigidbody getBall() {
        return ball.clone();
    }

    public double getBallLandingTime() {
        return ballLandingTime;
    }

    public Vector3 getBallLandingPosition() {
        return new Vector3(ballLandingPosition);
    }

    public boolean isKickOffPause() {
        return isKickOffPause;
    }

    public boolean isMatchOver() {
        return isMatchOver;
    }

    public boolean isOvertime() {
        return isOvertime;
    }

    public boolean isRoundActive() {
        return isRoundActive;
    }

    public int getGamePlayerCount() {
        return gamePlayerCount;
    }

    public Boostpad[] getBoostpads() {
        Boostpad[] copies = new Boostpad[boostpads.length];
        for (int i = 0; i < copies.length; i++) {
            copies[i] = new Boostpad(boostpads[i]);
        }
        return copies;
    }
}
