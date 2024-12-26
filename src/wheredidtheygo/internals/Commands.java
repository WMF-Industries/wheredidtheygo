package wheredidtheygo.internals;

import arc.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;

import static mindustry.Vars.*;

public class Commands{
    public static void init(){
        netServer.clientCommands.<Player>register("capture", "[all?] [units?] <team>", "Captures specified content (true/false) from the specified team (all teams if blank)", (args, player) ->{
            if(Core.settings.getBool("wdtg-capturing")){
                if(!state.rules.pvp){
                    if((headless && player.admin) || Core.settings.getBool("wdtg-direct")){
                        if((args[0].equals("false") && args[1].equals("false")) || (args[0].isEmpty())){
                            player.sendMessage("[scarlet]Missing capture data...!");
                            return;
                        }

                        if(args.length > 2){
                            for(Team team : Team.all){
                                if(team.name.equals(args[2]) || team.id == Strings.parseInt(args[2])){
                                    Utils.capture(args[0].equals("true"), args[1].equals("true"), team);
                                }
                            }
                        }

                        player.sendMessage("[scarlet]The specified team isn't present, defaulting to every present team!");
                        Utils.capture(args[0].equals("true"), args[1].equals("true"), null);
                    }else player.sendMessage("[scarlet]Missing permissions...");
                }else player.sendMessage("[scarlet]Did you really think this could be used on pvp?");
            }else player.sendMessage("[lightgray]Command disabled by the server!");
        });
    }
}
