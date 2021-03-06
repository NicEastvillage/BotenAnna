package botenanna.display;

import botenanna.BotenAnnaBot;
import botenanna.BotenAnnaWindow;
import botenanna.behaviortree.BehaviorTree;
import botenanna.game.Situation;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.io.IOException;

/** Display for bot info */
public class BotInfoDisplay extends InfoDisplay {

    private static final Color BLUE = new Color(.34, .42, 1, 1);
    private static final Color ORANGE = new Color(1, 0.7, 0.3, 1);

    private BotenAnnaBot bot;

    public BotInfoDisplay(BotenAnnaBot bot) {
        super("Car #" + bot.getIndex(), bot.getTeam() == BotenAnnaBot.Team.BLUE ? BLUE : ORANGE);
        this.bot = bot;

        //addChangeBtButton();
    }

    /** Add button to header that allows changing of behaviour tree. */
    private void addChangeBtButton() {
        Button changeBt = new Button("Tree");
        changeBt.setFont(new Font(10));
        changeBt.setPadding(new Insets(1, 4, 1, 4));
        changeBt.setPrefHeight(16);
        changeBt.setOnAction(e -> changeBehaviourTree());
        header.getChildren().add(changeBt);
    }

    /** Update info displayed. */
    public void update() {
        Situation input = bot.getLastInputReceived();
        if (input == null)
            return;

        infoLabel.setText(String.format(
                "Pos: %s\n" +
                "Possession: %b\n" +
                "Executing: %s",
                input.getMyCar().getPosition().toStringFixedSize(),
                input.hasPossession(input.myPlayerIndex),
                bot.getBehaviorTree().getCurrentNodeAsString()));
    }

    /** Change the behaviour tree of the bot connected to this display. */
    private void changeBehaviourTree() {
        try {
            BehaviorTree tree = BotenAnnaWindow.defaultBTBuilder.buildFromFileChooser();
            if (tree != null) {
                bot.setBehaviorTree(tree);
            }
        } catch (IOException e) {
            // Do nothing
        }
    }
}
