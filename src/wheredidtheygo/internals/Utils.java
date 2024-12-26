package wheredidtheygo.internals;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.blocks.payloads.PayloadSource;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.blocks.units.UnitAssembler;
import mindustry.world.blocks.units.UnitFactory;

import static mindustry.Vars.*;
import static wheredidtheygo.wheredidtheygo.*;

// utility class containing primary functions
public class Utils{
    public static ObjectIntMap<String> timeoutMap = new ObjectIntMap<>(); // keeps track of the last timeout time for the player
    public static ObjectMap<String, Long> timeouts = new ObjectMap<>(); // stores active timeouts, so the client couldn't remove them
    public static ObjectIntMap<String> packets = new ObjectIntMap<>();
    public static ObjectMap<String, Long> packetTimes = new ObjectMap<>();

    static Timer.Task task;
    static boolean prevState = Core.settings.getBool("wdtg-enemies");
    static int timer = 0, refresh = 0;
    public static void init(){
        if(state.rules.pvp || state.isEditor()) return;

        editFactories(!prevState);
        task = Timer.schedule(() ->{
            if(!state.isGame()){
                task.cancel();
                return;
            }

            if(timer++ >= Core.settings.getInt("wdtg-refresh-rate")){
                timer = 0;

                killEnemies();
            }

            if(refresh++ >= 10){
                state.rules.waveTimer = !Core.settings.getBool("wdtg-waves");
                if(prevState != Core.settings.getBool("wdtg-enemies")){
                    editFactories(prevState);
                    prevState = !prevState;
                }
            }
        }, 0f, 0.1f);
    }

    public static void clear(){
        timeoutMap.clear();
        timeouts.clear();
        packets.clear();
        packetTimes.clear();
    }

    public static boolean capture(boolean full, boolean units, Team team){
        if(net.client()){
            if(validHost){
                Call.serverPacketReliable("wdtg-req", Strings.format("@-@-@", full, units, team != null ? team.id : -1));
                mUI.toast(Core.bundle.get("wdtg-request-sent"));
            }else mUI.warnToast(Core.bundle.get("wdtg-vanilla-host"));

            return false;
        }

        if(full || units){
            Groups.unit.each(u ->{
                if(u.team != state.rules.defaultTeam && (team == null || u.team == team)){
                    u.team(state.rules.defaultTeam);
                    u.resetController();
                }
            });
        }

        if(full || !units){
            world.tiles.eachTile(t -> {
                if(t.build == null || (team != null && t.build.team != team))
                    return;

                if(t.block().privileged && !t.block().targetable)
                    return;

                Call.setTeam(t.build, state.rules.defaultTeam);
            });
        }

        return true;
    }

    public static void killEnemies(){
        Groups.unit.each(u ->{
            if(u.team != state.rules.defaultTeam)
                u.kill();
        });
    }

    public static void editFactories(boolean in){
        world.tiles.eachTile(t -> {
            if(t.build == null || t.build.team == state.rules.defaultTeam)
                return;

            if(!(t.block() instanceof UnitFactory
            || t.block() instanceof Reconstructor
            || t.block() instanceof UnitAssembler
            || (t.build instanceof PayloadSource.PayloadSourceBuild b && b.unit != null)))
                return;

            t.build.enabled(in);
        });
    }

    public static void logPacket(String data){
        packetTimes.remove(data);
        packetTimes.put(data, Time.millis());
    }

    public static void addLocalTimeout(long time){
        timeouts.put(player.uuid(), time);
    }

    public static long getLocalTimeout(){
        return timeouts.get(player.uuid());
    }

    public static long addTimeout(String data){
        int time = timeoutMap.get(data, 30);

        timeoutMap.remove(data);
        timeoutMap.put(data, Math.min(300, time + 15));

        long out = time * 1000L;
        timeouts.put(data, Time.millis() + out);

        return out;
    }

    public static long getTimeout(String data){
        long timeout = timeouts.get(data, -1L) - Time.millis();
        if(timeout <= 0)
            timeouts.remove(data);

        return timeout;
    }

    public static boolean hasTimeout(Player p){
        if(Time.millis() - packetTimes.get(p.uuid(), 0L) > 5000L){
            packets.remove(p.uuid());
            packets.put(p.uuid(), 0);
        }

        logPacket(p.uuid());

        int val = packets.increment(p.uuid());
        if(val > 5){
            Call.clientPacketReliable(p.con(), "wdtg-timeout", "" + (getTimeout(p.uuid()) > 0 ? getTimeout(p.uuid()) : addTimeout(p.uuid())));
            return true;
        }

        return false;
    }
}
