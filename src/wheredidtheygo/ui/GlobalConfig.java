package wheredidtheygo.ui;

import arc.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.blocks.storage.*;

import static mindustry.Vars.*;


public class GlobalConfig{
    public Table mapTable = new Table(), teamsTable = new Table(), textTable = new Table(), overrideTable = new Table();
    public BaseDialog teamSelect = new BaseDialog(Core.bundle.get("wdtg-dialog")),
            overrides = new BaseDialog(Core.bundle.get("wdtg-dialog-override"));
    public Team selectedTeam = Team.derelict, captureTeam, paramTeam;
    String data, playerName, capUnit, capBlock, capAll, teamSelector,
    msgCap, msgBuild, msgUnit, msgAnd, msgFrom, msgTeams, msgEmpty,
    msgSelect, msgAny, msgReq, msgSent, warnHost, warnPvp, warnPerms;
    Seq<Teams.TeamData> teamCache = new Seq<>();
    boolean stateCache, updateButtons, firstRun, validHost;
    int count;

    public GlobalConfig(){
        ui.settings.addCategory(Core.bundle.get("wdtg-category"), Icon.box, t ->{
            SettingsMenuDialog.SettingsTable subTable = new SettingsMenuDialog.SettingsTable();

            subTable.checkPref("wdtg-override", true);
            subTable.checkPref("wdtg-waves", true);
            subTable.sliderPref("wdtg-wave-offset", 0, 0, 150, 1, s -> s > 0 ? "-" + s : "0");
            subTable.checkPref("wdtg-mode", true);
            subTable.sliderPref("wdtg-refresh-rate", 1, 1, 10, 1, s -> s + Strings.format(" time@ per second", s > 1 ? "s" : ""));
            subTable.checkPref("wdtg-buttons", true);
            subTable.checkPref("wdtg-direct", false);

            t.add(subTable);
        });

        teamSelect.addCloseButton();
        teamSelect.cont.center().top().add(textTable);
        teamSelect.buttons.center().bottom().row().add(teamsTable);

        netClient.addPacketHandler("wdtg-true", p -> validHost = true);
        netServer.addPacketHandler("wdtg-check", (p, s) -> Call.clientPacketReliable(p.con(), "wdtg-true", ""));
        netServer.addPacketHandler("wdtg-req", (p, s) -> {
            String[] params = s.split(" ");
            playerName = p.coloredName();
            boolean multi = params[0].equals("true"), alternate = params[1].equals("true");
            if(!params[2].equals("derelict")){
                state.teams.present.each(t -> {
                    if (t.team.name.equals(params[2])) paramTeam = t.team;
                });
            }else paramTeam = Team.derelict;

            if(Core.settings.getBool("wdtg-direct") && p.admin()){
                capture(multi, alternate, true, paramTeam);
            }else{
                StringBuilder build = new StringBuilder();
                String name = paramTeam.coloredName().isEmpty() ? getName(paramTeam) : paramTeam.coloredName();

                build.append(playerName).append("[] ").append(msgReq).append(" ");
                build.append(alternate ? msgBuild : msgUnit).append(" ");
                if(multi) build.append(msgAnd).append(" ").append(!alternate ? msgBuild : msgUnit).append(" ");
                build.append(msgFrom).append(" ").append(paramTeam == Team.derelict ? msgTeams : name);

                ui.hudfrag.showToast(Icon.players, build.toString());

                build.setLength(0);
                playerName = "";
            }
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            validHost = false;
            if(net.client()) Call.serverPacketReliable("wdtg-check", "");

            selectedTeam = Team.derelict;
            teamCache.clear();
            firstRun = true;
        });

        // load strings here and keep them in ram instead of looking through the bundle every single time
        capUnit = Core.bundle.get("wdtg-cap-unit");
        capBlock = Core.bundle.get("wdtg-cap-block");
        capAll = Core.bundle.get("wdtg-cap-all");
        teamSelector = Core.bundle.get("wdtg-team-selector");
        msgCap = Core.bundle.get("wdtg-capture-message");
        msgBuild = Core.bundle.get("wdtg-capture-message-blocks");
        msgUnit = Core.bundle.get("wdtg-capture-message-units");
        msgAnd = Core.bundle.get("wdtg-capture-message-and");
        msgFrom = Core.bundle.get("wdtg-capture-message-from");
        msgTeams = Core.bundle.get("wdtg-capture-message-extra");
        msgEmpty = Core.bundle.get("wdtg-capture-message-empty");
        msgSelect = Core.bundle.get("wdtg-select-message");
        msgAny = Core.bundle.get("wdtg-select-message-any");
        msgReq = Core.bundle.get("wdtg-capture-request");
        msgSent = Core.bundle.get("wdtg-capture-request-sent");
        warnHost = Core.bundle.get("wdtg-vanilla-host-warn");
        warnPvp = Core.bundle.get("wdtg-pvp-warn");
        warnPerms = Core.bundle.get("wdtg-capture-request-warn");
    }

    public void rebuildUis(boolean enabled){
        if(valid(teamCache)
        && enabled == stateCache
        && !updateButtons) return;

        mapTable.reset();
        mapTable.clear();

        mapTable.visibility = () -> ui.minimapfrag.shown() && (enabled && !state.rules.pvp);
        if(state.rules.pvp) ui.hudfrag.showToast(Icon.infoCircle, "[scarlet]" + warnPvp);

        mapTable.button(capUnit, Icon.units, Styles.squareTogglet, ()->{
            capture(false, false, true, selectedTeam);
            updateButtons = true;
        }).width(180f).height(60f).margin(12f).checked(false).row();

        mapTable.button(capBlock, Icon.box, Styles.squareTogglet, ()->{
            capture(false, true, true, selectedTeam);
            updateButtons = true;
        }).width(180f).height(60f).margin(12f).checked(false).row();

        mapTable.button(capAll, Icon.list, Styles.squareTogglet, ()->{
            capture(true, false, true, selectedTeam);
            updateButtons = true;
        }).width(180f).height(60f).margin(12f).checked(false).row();

        mapTable.button(teamSelector, Icon.settings, Styles.squareTogglet, ()->{
            updateSelect();
            teamSelect.show();
            updateButtons = true;
        }).width(180f).height(60f).margin(12f).checked(false).row();

        if(updateButtons){
            updateButtons = false;
            return;
        }

        stateCache = enabled;

        teamCache.clear();
        teamCache.addAll(state.teams.present);

        teamsTable.reset();
        teamsTable.clear();

        teamsTable.button("@rules.anyenv", () -> {
            selectedTeam = Team.derelict;
            updateSelect();
        }).width(100f);

        state.teams.present.each(t -> {
            if(t.team != player.team() && t.team != Team.derelict){
                if(t.team.coloredName().isEmpty()){
                    data = getName(t.team);
                }else{
                    data = t.team.coloredName();
                }

                teamsTable.button(data, () -> {
                    selectedTeam = t.team;
                    updateSelect();
                }).width(data.length() * 5);
            }
        });
    }

    private void updateSelect(){
        textTable.reset();
        textTable.clear();

        textTable.add(msgSelect + " " + (selectedTeam == Team.derelict ? msgAny : selectedTeam.coloredName()));
    }

    private void capture(boolean multi, boolean alternate, boolean notify, Team team){
        if(net.client()){
            if(validHost){
                Call.serverPacketReliable("wdtg-req", Strings.format("@ @ @", multi, alternate, team));
                ui.hudfrag.showToast(msgSent);
            }else ui.hudfrag.showToast(Icon.cancel, "[scarlet]" + warnHost);
            return;
        }

        if(firstRun){
            state.rules.coreDestroyClear = firstRun = false;
            captureTeam = player.team();
        }

        Seq<Teams.TeamData> data = new Seq<>();
        ObjectSet<Team> teams = new ObjectSet<>();
        state.teams.present.each(t -> {
            count = 0;
            t.buildings.each(b -> {
                if(b.block().privileged && !b.block.targetable) ++count;
            });
            if(t.team != player.team() && (t.buildings.size > count
            || t.units.size != 0) && (t.team == team || team == Team.derelict)){
                data.add(t);
                teams.add(t.team);
            }
        });

        if(data.isEmpty()){
            if(notify) ui.hudfrag.showToast(Icon.warning, "[yellow]" + msgEmpty);
            return;
        }

        if(multi || alternate){
            data.each(t -> t.buildings.each(b -> {
                if(b.block().privileged && !b.block.targetable) return;
                // filter out world logic stuff

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
                // TODO: This will put heavy load on the cpu and might leak
                if(t.buildings.size > 0){
                    capture(multi, alternate, false, team);
                }
            });
        }

        if(multi || !alternate){
            Groups.unit.each(u -> {
                if(teams.contains(u.team())){
                    u.team = player.team();
                    u.resetController();
                }
            });
        }

        if(notify){
            StringBuilder build = new StringBuilder();
            String name = team.coloredName().isEmpty() ? getName(team) : team.coloredName();

            if(!playerName.isEmpty()) build.append(playerName).append("[] ");
            build.append(msgCap).append(" ").append(alternate ? msgBuild : msgUnit).append(" ");
            if(multi) build.append(msgAnd).append(" ").append(!alternate ? msgBuild : msgUnit).append(" ");
            build.append(msgFrom).append(" ").append(team == Team.derelict ? msgTeams : name).append("!");

            ui.hudfrag.showToast(build.toString());

            build.setLength(0);
            playerName = "";
        }

        data.clear();
        teams.clear();
    }

    private String getName(Team team){
        return "[#" + team.color + "]#" + team.id;
    }

    private boolean valid(Seq<Teams.TeamData> seq){
        if(seq.isEmpty() || seq.size != state.teams.present.size) return false;

        ObjectSet<Team> teams = new ObjectSet<>();
        state.teams.present.each(t -> teams.add(t.team));

        for(Teams.TeamData data : seq){
            if(!teams.contains(data.team)){
                return false;
            }
        }

        return true;
    }
}
