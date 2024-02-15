package wheredidtheygo.ui;

import arc.*;
import arc.graphics.Color;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;


public class GlobalConfig {
    public Table mapTable = new Table(), teamsTable = new Table();
    public BaseDialog teamSelect = new BaseDialog(Core.bundle.get("wdtg-dialog"));
    public Team selectedTeam = Team.derelict;
    String data;
    boolean open;

    public GlobalConfig(){
        if(headless) return;
        ui.settings.addCategory(Core.bundle.get("wdtg-category"), Icon.box, t ->{
            SettingsMenuDialog.SettingsTable subTable = new SettingsMenuDialog.SettingsTable();

            subTable.checkPref("wdtg-waves", true);
            subTable.sliderPref("wdtg-wave-offset", 0, 0, 150, 1, s -> s > 0 ? "-" + s : "0");
            subTable.checkPref("wdtg-mode", true);
            subTable.sliderPref("wdtg-refresh-rate", 1, 1, 10, 1, s -> s + Strings.format(" time@ per second", s > 1 ? "s" : ""));
            subTable.checkPref("wdtg-buttons", true);

            t.add(subTable);
        });

        teamSelect.buttons.add(teamsTable).left().row();
    }

    public void rebuildUis(){
        mapTable.reset();
        mapTable.clear();

        mapTable.visibility = () -> ui.minimapfrag.shown();

        if(Core.settings.getBool("wdtg-buttons")){
            mapTable.button(Core.bundle.get("wdtg-cap-unit"), Icon.units, Styles.squareTogglet, ()->{
                capture(false, false, true, selectedTeam);
            }).width(180f).height(60f).margin(12f).checked(false).row();

            mapTable.button(Core.bundle.get("wdtg-cap-block"), Icon.box, Styles.squareTogglet, ()->{
                capture(false, true, true, selectedTeam);
            }).width(180f).height(60f).margin(12f).checked(false).row();

            mapTable.button(Core.bundle.get("wdtg-cap-all"), Icon.list, Styles.squareTogglet, ()->{
                capture(true, false, true, selectedTeam);
            }).width(180f).height(60f).margin(12f).checked(false).row();

            mapTable.button(Core.bundle.get("wdtg-team-selector"), Icon.settings, Styles.squareTogglet, ()->{
                teamSelect.show();
                open = true;
            }).width(180f).height(60f).margin(12f).checked(false).row();
        };

        if(!open){
            teamsTable.reset();
            teamsTable.clear();
            teamsTable.button("@close", () -> {
                open = false;
                teamSelect.hide();
            }).width(100f);
            teamsTable.button("@rules.anyenv", () -> selectedTeam = Team.derelict).width(100f);
            state.teams.present.each(t -> {
                if(t.team != player.team() && t.team != Team.derelict){
                    if(t.team.coloredName().isEmpty()){
                        data = color(t.team.color) + "#" + t.team.id;
                    }else{
                        data = t.team.coloredName();
                    }
                    teamsTable.button(data, () -> selectedTeam = t.team).width(data.length() * 5);
                }
            });
        }
    }

    private void capture(boolean multi, boolean alternate, boolean notify, Team team){
        if(net.client()){
            ui.hudfrag.showToast("[scarlet]" + Core.bundle.get("wdtg-client-warn"));
            return;
        }

        Seq<Teams.TeamData> data = new Seq<>();
        ObjectSet<Team> teams = new ObjectSet<>();
        state.teams.present.each(t -> {
            if(t.team != player.team() && (t.team == team || team == Team.derelict)){
                data.add(t);
                teams.add(t.team);
            }
        });

        if(multi || alternate){
            data.each(t -> t.buildings.each(b -> {
                b.remove();
                b.tile.setNet(b.block(), player.team(), b.rotation());
            }));

            state.teams.updateTeamStats();

            data.each(t -> {
                if(t.buildings.size > 0){ // TODO: This will put heavy load on the cpu and might leak
                    capture(multi, alternate, false, team);
                }
            });
        }

        if(multi || !alternate){
            Groups.unit.each(u -> {
                if(teams.contains(u.team())) u.team = player.team();
            });
        }

        if(notify){
            StringBuilder build = new StringBuilder();
            String name = team.coloredName().isEmpty() ? color(team.color) + "#" + team.id : team.coloredName(),
            capUnits = Core.bundle.get("wdtg-capture-message-units"), capBlocks = Core.bundle.get("wdtg-capture-message-blocks");

            build.append(Core.bundle.get("wdtg-capture-message")).append(" ");
            build.append(alternate ? capBlocks : capUnits).append(" ");
            if(multi) build.append(Core.bundle.get("wdtg-capture-message-and")).append(" ").append(!alternate ? capBlocks : capUnits).append(" ");
            build.append(Core.bundle.get("wdtg-capture-message-from")).append(" ");

            ui.hudfrag.showToast(Strings.format("@" + "@!", build.toString(),
            teams.size > 1 ? Core.bundle.get("wdtg-capture-message-extra") : name));

            build.setLength(0);
        }

        data.clear();
        teams.clear();
    }

    public String color(Color color){
        return "[#" + color + "]";
    }
}
