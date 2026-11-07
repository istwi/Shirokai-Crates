package dev.shirokaicrates.storage;

import dev.shirokaicrates.config.Models.*;
import java.time.Instant;
import java.util.UUID;

public record Transaction(UUID id, UUID player, String playerName, String crateId, String crateName,
                          Reward reward, String pokemonUuid, String pokemonJson, Instant created, State state, boolean announced, String detail) {
    public enum State { RESERVED, READY, DELIVERING, DELIVERED, CANCELLED, UNCERTAIN }
    public static Transaction reserve(UUID player,String name,Crate crate,Reward reward) {
        return new Transaction(UUID.randomUUID(),player,name,crate.id(),crate.name(),reward,
                UUID.randomUUID().toString(),"",Instant.now(),State.RESERVED,false,"");
    }
    public Transaction transition(State next,String reason) {
        boolean valid=switch(state) {
            case RESERVED -> next==State.READY || next==State.CANCELLED || next==State.UNCERTAIN;
            case READY -> next==State.DELIVERING;
            case DELIVERING -> next==State.DELIVERED || next==State.READY || next==State.UNCERTAIN;
            case UNCERTAIN -> next==State.DELIVERED || next==State.READY || next==State.CANCELLED;
            default -> false;
        };
        if(!valid) throw new IllegalStateException(state+" -> "+next);
        return new Transaction(id,player,playerName,crateId,crateName,reward,pokemonUuid,pokemonJson,created,next,announced,reason);
    }
    public Transaction withPokemon(String json) {
        return new Transaction(id,player,playerName,crateId,crateName,reward,pokemonUuid,json,created,state,announced,detail);
    }
    public Transaction announcementClaimed() {
        return new Transaction(id,player,playerName,crateId,crateName,reward,pokemonUuid,pokemonJson,created,state,true,detail);
    }
}
