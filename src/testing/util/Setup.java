package testing.util;

import arc.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import blui.ui.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.Mods.*;
import mindustry.world.*;
import testing.*;
import testing.buttons.*;
import testing.ui.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class Setup{
    public static boolean posLabelAligned = false;

    public static TerrainPainterFragment terrainFrag;
    private static Table timeSlider;

    public static void init(){
        TUDialogs.load();

        BLSetup.addTable(table -> {
            if(mobile && settings.getBool("console")){
                table.table(Tex.buttonEdge3, Console::addButtons);
                table.row();
            }
            table.table(Tex.buttonEdge3, t -> {
                Spawn.addButtons(t);
                Environment.worldButton(t);
                Effect.statusButton(t);
                Sandbox.addButtons(t);
            });
            table.row();

            boolean timeControl = timeControlEnabled();

            table.table(timeControl ? Tex.buttonEdge3 : Tex.pane, t -> {
                TeamChanger.addButton(t);
                Health.addButtons(t);
                Death.addButtons(t);
                LightSwitch.lightButton(t);
            });

            if(timeControl){
                table.row();
                table.add(yoinkTimeSlider());
            }
        }, () -> true);

        BLSetup.addTable(table -> {
            if(timeControlEnabled()) table.add(yoinkTimeSlider());
            table.table(Tex.pane, Death::seppuku);
        }, () -> true);
    }

    private static Table yoinkTimeSlider(){
        if(timeSlider == null){
            timeSlider = Vars.ui.hudGroup.find("tc-slidertable");
            timeSlider.visible(() -> true);

            Vars.ui.hudGroup.find("tc-foldedtable").visible(() -> false);
        }
        return timeSlider;
    }

    public static boolean timeControlEnabled(){
        LoadedMod timeControl = Vars.mods.getMod("time-control");
        return timeControl != null && timeControl.isSupported() && timeControl.enabled();
    }

    private static String fix(float f){
        return Strings.autoFixed(f, 1);
    }
}
