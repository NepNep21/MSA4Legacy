package me.nepnep.msa4legacy.patches;

public class MicrosoftAccount {
    public final String email;
    public final String uuid;
    public final String token;
    public final String username;
    
    public MicrosoftAccount(String email, String uuid, String token, String username) {
        this.email = email;
        this.uuid = uuid;
        this.token = token;
        this.username = username;
    }
}
