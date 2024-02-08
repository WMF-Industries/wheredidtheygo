package wheredidtheygo;

import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.blocks.units.UnitFactory;

public class wheredidtheygo extends Mod{
    public static Timer.Task updateRunner;
    public wheredidtheygo(){
        Log.info("Enemy units won't exist no more!");
        Events.on(EventType.WorldLoadEvent.class, e ->{
           updateRunner = Timer.schedule(wheredidtheygo::updateUnits, 0, 1f);
           Groups.build.each(b -> {
               if(b.team() != Vars.state.rules.defaultTeam
               && b instanceof UnitFactory.UnitFactoryBuild
               || b instanceof Reconstructor.ReconstructorBuild)
                   b.enabled = false;
           });

           if(Vars.state.rules.winWave > 0 && Vars.state.isCampaign()){
                Vars.state.wave = Vars.state.rules.winWave;
           }else{
               Vars.state.rules.waveSending = Vars.state.rules.waves = Vars.state.rules.waveTimer = false;
           }
        });
    }

    private static void updateUnits(){
        if(!Vars.state.isGame()){
            updateRunner.cancel();
            return;
        }

        Groups.unit.each(u -> {
            if(u.team() != Vars.state.rules.defaultTeam
            && !Vars.state.rules.pvp && !u.isPlayer())
                u.kill();
        });
    }
}
