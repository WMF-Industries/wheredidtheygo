package wheredidtheygo;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.world.blocks.units.*;
import wheredidtheygo.ui.*;

import static mindustry.Vars.*;

public class wheredidtheygo extends Mod{
    int setting1, setting2;
    boolean loaded, teamExists, setting3, setting4, setting5;

    public wheredidtheygo(){
        Events.on(EventType.ClientLoadEvent.class, e -> {
            GlobalConfig config = new GlobalConfig();
            ui.hudGroup.fill(t -> {
                t.name = "wdtg-cont";
                t.visibility = () -> ui.minimapfrag.shown();
                t.bottom().left();
                t.add(config.mapTable);
                Timer.schedule(() -> {
                    if(state.isGame()) config.rebuildUis();
                },0, 1);
            });
            loadMod();
        });

        Events.on(EventType.ServerLoadEvent.class, e -> {
            netServer.clientCommands.<Player>register("capture", "<units> <buildings> <team>", "Captures specified content (true/false) from the specified team (all teams if blank)", (args, player) ->{
                if(setting4){
                    if(!state.rules.pvp){
                        if(player.admin){
                            if((args[0].equals("false") && args[1].equals("false")) || (args[0].isEmpty())){
                                player.sendMessage("[scarlet]Cannot capture nothing!");
                                return;
                            };

                            teamExists = false;
                            Seq<Teams.TeamData> data = new Seq<>();
                            ObjectSet<Team> teams = new ObjectSet<>();
                            state.teams.present.each(t -> {
                                if(t.team != player.team() && (t.team.name.equalsIgnoreCase(args[2]) || args[2].isEmpty())){
                                    data.add(t);
                                    teams.add(t.team);
                                    if(t.team.name.equalsIgnoreCase(args[2])) teamExists = true;
                                }
                            });

                            if(!teamExists)
                                player.sendMessage("[scarlet]The specified team isn't present, defaulting to every present team!");

                            if(teams.isEmpty()){
                                player.sendMessage("[scarlet]No teams to capture!");
                                return;
                            }

                            if(args[0].equals("true")){
                                Groups.unit.each(u -> {
                                    if(teams.contains(u.team())) u.team = player.team();
                                });
                            }

                            if(args[1].equals("true")){
                                data.each(t -> t.buildings.each(b -> {
                                    b.remove();
                                    b.tile.setNet(b.block(), player.team(), b.rotation());
                                }));

                                state.teams.updateTeamStats();

                                data.each(t -> {
                                    if(t.buildings.size > 0){
                                        capture(data);
                                    }
                                });
                            }

                            data.clear();
                            teams.clear();
                        }else player.sendMessage("[scarlet]Missing permissions...");
                    }else player.sendMessage("[scarlet]Did you really think this could be used on pvp?");
                }else player.sendMessage("[lightgray]Command disabled by the server administration!");
            });
            loadMod();
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            if(net.client()) return;
            modifyWorld();
        });
    }

    public void capture(Seq<Teams.TeamData> data){ // TODO: This will put heavy load on the cpu and might leak, replace later on
        data.each(t -> t.buildings.each(b -> {
            b.remove();
            b.tile.setNet(b.block(), player.team(), b.rotation());
        }));

        state.teams.updateTeamStats();

        data.each(t -> {
            if(t.buildings.size > 0){
                capture(data);
            }
        });
    }

    public void modifyWorld(){
        if(!loaded){
            Timer.schedule(this::modifyWorld, 5f);
            return;
        }

        if(setting3){
            Groups.build.each(b -> {
                if (b.team() != state.rules.defaultTeam
                        && b instanceof UnitFactory.UnitFactoryBuild
                        || b instanceof Reconstructor.ReconstructorBuild)
                    b.enabled = false;
            });
        }

        if(setting5){
            if(state.rules.winWave > 0){
                state.rules.winWave = Math.max(state.rules.winWave - setting2, 1);
            }else{
                state.rules.waveSending = state.rules.waves = state.rules.waveTimer = false;
            }
        }
    }

    public void loadMod(){
        if(!headless){
            Log.info(Core.bundle.get("wdtg-log-message"));
            Timer.schedule(() -> {
                setting1 = Core.settings.getInt("wdtg-refresh-rate");
                setting2 = Core.settings.getInt("wdtg-wave-offset");
                setting3 = Core.settings.getBool("wdtg-mode");
                setting4 = Core.settings.getBool("wdtg-buttons");
                setting5 = Core.settings.getBool("wdtg-waves");
            },0, 2.5f);
        }else{
            setting1 = 1;
            setting2 = 0;
            setting3 = setting4 = setting5 = true;
        }

        Timer.schedule(() -> {
            if(!setting3 && (!state.isGame() || net.client())) return;

            Groups.unit.each(u -> {
                if (u.team() != state.rules.defaultTeam
                && !state.rules.pvp && !u.isPlayer()) u.kill();
            });
        }, 0, (float) 1 / setting1);

        loaded = true;
    }
}
