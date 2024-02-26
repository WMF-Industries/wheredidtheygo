package wheredidtheygo;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.units.*;
import wheredidtheygo.ui.*;

import static mindustry.Vars.*;

public class wheredidtheygo extends Mod{
    public boolean loaded, teamExists, removeEnemies, enableCapturing,
            affectWaves, showMenu, localOverride, temp1, temp2, temp3;
    int refreshRate, waveOffset, temp, count;
    Team captureTeam;
    public wheredidtheygo(){
        Events.on(EventType.ClientLoadEvent.class, e -> {
            GlobalConfig config = new GlobalConfig();
            ui.hudGroup.fill(t -> {
                t.name = "wdtg-cont";
                t.visibility = () -> ui.minimapfrag.shown();
                t.bottom().left();
                t.add(config.mapTable);
                Timer.schedule(() -> {
                    if(state.isGame()) config.rebuildUis(enableCapturing);
                },0, 1);
            });
            ui.hudGroup.fill(s -> {
                s.name = "wdtg-overrides";
                s.visibility = () -> ui.minimapfrag.shown();
                s.top().left().button(Core.bundle.get("wdtg-dialog-override"),
                ()-> overrideMenu(config, !localOverride)).width(160f).height(80f);
            });

            loadMod();

            config.overrides.buttons.button("@back", () -> {
                config.overrides.hide();

                modifyWorld();
            }).width(210);
            config.overrides.cont.center().add(config.overrideTable);

            Events.on(EventType.WorldLoadEvent.class, ev -> {
                if(net.client()) return;

                localOverride = false;

                temp = state.rules.winWave;
                temp1 = state.rules.waves;
                temp2 = state.rules.waveSending;
                temp3 = state.rules.waveTimer;

                if(showMenu){
                    overrideMenu(config, true);
                    return;
                }

                modifyWorld();
            });
        });

        Events.on(EventType.ServerLoadEvent.class, e -> {
            netServer.clientCommands.<Player>register("capture", "<units> <buildings> [team]", "Captures specified content (true/false) from the specified team (all teams if blank)", (args, player) ->{
                if(enableCapturing){
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
                                count = 0;
                                t.buildings.each(b -> {
                                    if(b.block().privileged && !b.block.targetable) ++count;
                                });
                                if(t.team != player.team() && (t.buildings.size > count
                                || t.units.size != 0) && (t.team.name.equalsIgnoreCase(args[2]) || args[2].isEmpty())){
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
                                    if(teams.contains(u.team())){
                                        u.team = player.team();
                                        u.resetController();
                                    }
                                });
                            }

                            if(args[1].equals("true")){
                                state.rules.coreDestroyClear = false;
                                captureTeam = player.team();
                                data.each(t -> t.buildings.each(b -> {
                                    if(b.block().privileged && !b.block.targetable) return;

                                    b.remove();
                                    b.tile.setNet(b.block(), player.team(), b.rotation());

                                    if(!b.block().update || b.tile.team() != player.team()) return;
                                    // no config or not replaced yet

                                    if(b.block() instanceof CoreBlock){
                                        if(b.team == captureTeam) return;
                                        captureTeam = b.team;
                                    } // cores should only transfer items once

                                    b.tile.build.configure(b.config());
                                    if(b.block().hasLiquids && b.liquids.current() != null){
                                        b.liquids.each((l, a) -> {
                                            b.tile.build.liquids.add(l, a);
                                        }); // if anyone wonders why liquids first, reactors.
                                    }
                                    if(b.block().hasItems && b.items.any()) b.tile.build.items.add(b.items());
                                }));

                                state.teams.updateTeamStats();

                                data.each(t -> {
                                    if(t.buildings.size > 0){
                                        capture(data, player);
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

            Events.on(EventType.WorldLoadEvent.class, ev -> modifyWorld());
        });
    }

    public void capture(Seq<Teams.TeamData> data, Player player){ // TODO: This will put heavy load on the cpu and might leak, replace later on
        data.each(t -> t.buildings.each(b -> {
            if(b.block().privileged && !b.block.targetable) return;

            b.remove();
            b.tile.setNet(b.block(), player.team(), b.rotation());

            if(!b.block().update || b.tile.team() != player.team()) return;

            if(b.block() instanceof CoreBlock){
                if(b.team == captureTeam) return;
                captureTeam = b.team;
            }

            b.tile.build.configure(b.config());
            if(b.block().hasLiquids && b.liquids.current() != null){
                b.liquids.each((l, a) -> {
                    b.tile.build.liquids.add(l, a);
                });
            }
            if(b.block().hasItems && b.items.any()) b.tile.build.items.add(b.items());
        }));

        state.teams.updateTeamStats();

        data.each(t -> {
            count = 0;
            t.buildings.each(b -> {
                if(b.block().privileged && !b.block.targetable) ++count;
            });
            if(t.buildings.size > count){
                capture(data, player);
            }
        });
    }

    public void modifyWorld(){
        if(!loaded){
            Timer.schedule(this::modifyWorld, 5f);
            return;
        }

        Groups.build.each(b -> {
            if(b.team() != state.rules.defaultTeam
            && b instanceof UnitFactory.UnitFactoryBuild
            || b instanceof Reconstructor.ReconstructorBuild) b.enabled = !removeEnemies;
        });

        if(affectWaves){
            if(state.rules.winWave > 0){
                state.rules.winWave = Math.max(state.rules.winWave - waveOffset, 1);
            }else state.rules.waveSending = state.rules.waves = state.rules.waveTimer = false;
        }else{
            state.rules.winWave = temp;
            state.rules.waves = temp1;
            state.rules.waveSending = temp2;
            state.rules.waveTimer = temp3;
        }
    }

    public void loadMod(){
        if(!headless){
            Log.info(Core.bundle.get("wdtg-log-message"));
            refreshRate = Core.settings.getInt("wdtg-refresh-rate");
            Timer.schedule(() -> {
                if(localOverride) return;

                showMenu = Core.settings.getBool("wdtg-override");
                waveOffset = Core.settings.getInt("wdtg-wave-offset");
                removeEnemies = Core.settings.getBool("wdtg-mode");
                enableCapturing = Core.settings.getBool("wdtg-buttons");
                affectWaves = Core.settings.getBool("wdtg-waves");
            },0, 2.5f);
        }else{
            refreshRate = 1;
            waveOffset = 0;
            removeEnemies = enableCapturing = affectWaves = true;
        }

        Timer.schedule(() -> {
            if(!removeEnemies || !state.isGame()
            || state.rules.pvp || net.client()) return;

            Groups.unit.each(u -> {
                if (u.team() != state.rules.defaultTeam
                && !u.isPlayer()) u.kill();
            });
        }, 0, (float) 1 / refreshRate);

        loaded = true;
    }

    public void overrideMenu(GlobalConfig cfg, boolean newInstance){
        if(newInstance){
            cfg.overrideTable.reset();
            cfg.overrideTable.clear();

            cfg.overrideTable.row().check(Core.bundle.get("setting.wdtg-mode.name"), removeEnemies, (checked) -> {
                removeEnemies = checked;
                localOverride = true;
            });
            cfg.overrideTable.row().check(Core.bundle.get("setting.wdtg-buttons.name"), enableCapturing, (checked) -> {
                enableCapturing = checked;
                localOverride = true;
            });
            cfg.overrideTable.row().check(Core.bundle.get("setting.wdtg-waves.name"), affectWaves, (checked) -> {
                affectWaves = checked;
                localOverride = true;
            });
        }

        cfg.overrides.show();
    }
}
