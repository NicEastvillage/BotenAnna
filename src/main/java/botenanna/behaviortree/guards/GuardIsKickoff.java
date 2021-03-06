package botenanna.behaviortree.guards;

import botenanna.behaviortree.Leaf;
import botenanna.behaviortree.MissingNodeException;
import botenanna.behaviortree.NodeStatus;
import botenanna.game.Situation;

public class GuardIsKickoff extends Leaf {

    /** <p> The GuardIsKickoff returns SUCCESS if there is a kickoff. </p>
     *
     *  <p> It's signature is: {@code GuardIsKickoff}</p> */
    public GuardIsKickoff(String[] arguments) throws IllegalArgumentException {
        super(arguments);

        //Takes no arguments
        if(arguments.length != 0) throw new IllegalArgumentException();
    }

    @Override
    public void reset() {
        // Irrelevant
    }

    @Override
    public NodeStatus run(Situation input) throws MissingNodeException {

        if(input.getBall().getPosition().asVector2().isZero()){
            if(input.getBall().getVelocity().asVector2().isZero())
                return NodeStatus.DEFAULT_SUCCESS;
        }

        return NodeStatus.DEFAULT_FAILURE;
    }
}
