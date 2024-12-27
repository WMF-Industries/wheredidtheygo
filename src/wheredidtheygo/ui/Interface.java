package wheredidtheygo.ui;

import arc.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import wheredidtheygo.internals.*;

import static mindustry.Vars.*;

public class Interface{
    Table mapTableLeft = new Table(), mapTableRight = new Table(), teamsTable = new Table(), textTable = new Table();
    BaseDialog teamSelect = new BaseDialog(Core.bundle.get("wdtg-select-dialog"));
    Team selectedTeam = Team.derelict;
    Seq<Teams.TeamData> teamCache = new Seq<>();
    boolean stateCache, updateButtons;

    public Interface(){
        ui.settings.addCategory(Core.bundle.get("wdtg-category"), Icon.box, t ->{
            SettingsMenuDialog.SettingsTable subTable = new SettingsMenuDialog.SettingsTable();

            subTable.checkPref("wdtg-waves", false);
            subTable.checkPref("wdtg-enemies", false);
            subTable.checkPref("wdtg-capturing", false);
            subTable.checkPref("wdtg-direct", false);
            subTable.checkPref("wdtg-commands", false);
            subTable.sliderPref("wdtg-refresh-rate", 1, 1, 10, 1, s -> s + Strings.format(" time@ per second", s > 1 ? "s" : ""));

            t.add(subTable);
        });

        ui.hudGroup.fill(t -> {
            t.name = "wdtg-cont";
            t.visibility = () -> ui.minimapfrag.shown();
            t.bottom().left().add(mapTableLeft);
            t.fill(nt -> nt.bottom().right().add(mapTableRight));
            Timer.schedule(() -> {
                if(state.isGame())
                    rebuildUis(Core.settings.getBool("wdtg-capturing"));
            },0, 1);
        });

        teamSelect.addCloseButton();
        teamSelect.cont.center().top().add(textTable);
        teamSelect.buttons.center().bottom().row().add(teamsTable);

        Events.on(EventType.WorldLoadEvent.class, e -> {
            selectedTeam = null;
            teamCache.clear();

            Timer.schedule(()-> {
                if(state.rules.pvp)
                    infoToast(Core.bundle.get("wdtg-pvp-warn"));
            }, 5);
        });
    }

    public void rebuildUis(boolean enabled){
        if(valid(teamCache) && enabled == stateCache && !updateButtons) return;

        mapTableLeft.reset();
        mapTableRight.reset();

        mapTableLeft.visibility = () -> ui.minimapfrag.shown() && (enabled && !state.rules.pvp);
        mapTableRight.visibility = () -> ui.minimapfrag.shown() && (enabled && (!net.active() || net.server()));

        mapTableRight.button(Core.bundle.get("wdtg-unlock-tech"), Icon.tree, Styles.defaultt, () -> {
            if(state.isCampaign())
                state.getPlanet().techTree.each(n -> n.content.unlock());
            else warnToast(Core.bundle.get("wdtg-campaign"));
        }).width(180f).height(60f).margin(12f).checked(false).row();

        mapTableRight.button(Core.bundle.get("wdtg-cap-sector"), Icon.home, Styles.defaultt, () -> {
            if(state.isCampaign())
                Call.sectorCapture();
            else warnToast(Core.bundle.get("wdtg-campaign"));
        }).width(180f).height(60f).margin(12f).checked(false).row();

        mapTableRight.button(getColor(PlanetDialog.debugSelect) + Core.bundle.get("wdtg-launch-anywhere"), Icon.export, Styles.defaultt, () -> {
            if(state.isCampaign()){
                PlanetDialog.debugSelect = !PlanetDialog.debugSelect;

                updateButtons = true;
                rebuildUis(stateCache);
            }else warnToast(Core.bundle.get("wdtg-campaign"));
        }).width(180f).height(60f).margin(12f).checked(false).row();

        mapTableLeft.button(Core.bundle.get("wdtg-cap-unit"), Icon.units, Styles.defaultt, ()->{
            if(Utils.capture(false, true, selectedTeam))
                toast(Strings.format(Core.bundle.get("wdtg-capture-units"), getPreferredName(selectedTeam)));
        }).width(180f).height(60f).margin(12f).checked(false).row();

        mapTableLeft.button(Core.bundle.get("wdtg-cap-build"), Icon.box, Styles.defaultt, ()->{
            if(Utils.capture(false, false, selectedTeam))
                toast(Strings.format(Core.bundle.get("wdtg-capture-buildings"), getPreferredName(selectedTeam)));
        }).width(180f).height(60f).margin(12f).checked(false).row();

        mapTableLeft.button(Core.bundle.get("wdtg-cap-all"), Icon.list, Styles.defaultt, ()->{
            if(Utils.capture(true, false, selectedTeam))
                toast(Strings.format(Core.bundle.get("wdtg-capture-all"), getPreferredName(selectedTeam)));
        }).width(180f).height(60f).margin(12f).checked(false).row();

        mapTableLeft.button(Core.bundle.get("wdtg-team-selector"), Icon.settings, Styles.defaultt, ()->{
            updateSelect();
            teamSelect.show();
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
            selectedTeam = null;
            updateSelect();
        }).width(100f);

        state.teams.present.each(t -> {
            if(t.team != state.rules.defaultTeam){
                String name = getPreferredName(t.team);
                teamsTable.button(name, () -> {
                    selectedTeam = t.team;
                    updateSelect();
                }).width(name.length() * 5);
            }
        });
    }

    private void updateSelect(){
        textTable.reset();
        textTable.clear();

        textTable.add(Strings.format(Core.bundle.get("wdtg-select-message"), getPreferredName(selectedTeam)));
    }

    public String getColor(boolean active){
        return active ? "[lime]" : "[scarlet]";
    }

    public String getPreferredName(Team team){
        return team != null ? team.coloredName().isEmpty() ? getName(team) : team.coloredName() : Core.bundle.get("wdtg-capture-extra");
    }

    public String getName(Team team){
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

    public void toast(String input){
        ui.hudfrag.showToast(input);
    }

    public void infoToast(String input){
        ui.hudfrag.showToast(Icon.infoCircle, input);
    }

    public void warnToast(String input){
        ui.hudfrag.showToast(Icon.warning, input);
    }
}
