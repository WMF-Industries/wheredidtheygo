package wheredidtheygo;

import arc.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;

import wheredidtheygo.ui.*;
import wheredidtheygo.internals.*;

import static mindustry.Vars.*;

import static wheredidtheygo.internals.Utils.*;

public class wheredidtheygo extends Mod{
    public static Interface mUI;

    public static boolean validHost = false;
    public wheredidtheygo(){
        netServer.addPacketHandler("wdtg", (p, s) -> Call.clientPacketReliable(p.con(), "wdtg-reply", ""));

        netServer.addPacketHandler("wdtg-req", (p, s) -> {
            if(hasTimeout(p))
                return;

            String[] vars = s.split("-");
            boolean all = vars[0].equals("true"), units = vars[1].equals("true");
            Team team = Team.get(Strings.parseInt(vars[2]));

            if((headless && p.admin) || Core.settings.getBool("wdtg-direct"))
                capture(all, units, team);
            else if(!headless){
                mUI.infoToast(Strings.format(
                    Core.bundle.get(
                        all ? "wdtg-request-all" : units ? "wdtg-request-units" : "wdtg-request-buildings"
                    ), p.coloredName() + "[white]", team != null ? team.coloredName().isEmpty() ? mUI.getName(team) : team.coloredName() : Core.bundle.get("wdtg-capture-extra")
                ));
            }
        });

        Events.on(EventType.PlayerJoin.class, e -> packets.put(e.player.uuid(), 0));
        Events.on(EventType.PlayerLeave.class, e -> packets.remove(e.player.uuid()));

        Events.on(EventType.WorldLoadEvent.class, e -> {
            Utils.clear();
            Utils.init();

            validHost = false;
            if(net.client())
                Call.serverPacketReliable("wdtg", "");
        });

        Events.on(EventType.ClientLoadEvent.class, e -> {
            mUI = new Interface();

            if(Core.settings.getBool("wdtg-commands"))
                Commands.init();

            netClient.addPacketHandler("wdtg-reply", s -> validHost = true);

            netClient.addPacketHandler("wdtg-timeout", s -> {
                long val = Strings.parseLong(s, -1L);
                addLocalTimeout(val);
                mUI.warnToast(Strings.format(Core.bundle.get("wdtg-cooldown"), val / 1000));
            });
        });

        Events.on(EventType.ServerLoadEvent.class, e -> Commands.init());
    }
}
