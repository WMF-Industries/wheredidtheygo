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
    public wheredidtheygo(){
        Log.info("Enemy units won't exist no more!");

        Events.on(EventType.ClientLoadEvent.class, e -> {
            Timer.schedule(() -> {
                if(!Vars.state.isGame() || Vars.net.client()) return;

                Groups.unit.each(u -> {
                    if(u.team() != Vars.state.rules.defaultTeam
                            && !Vars.state.rules.pvp && !u.isPlayer())
                        u.kill();
                });
            }, 0, 1f);
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            if(Vars.net.client()) return;

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
}
