package me.nepnep.msa4legacy.patches;

public class MicrosoftAccount {
    public String email;
    public String uuid;
    public String token;
    public String username;
    
    public MicrosoftAccount(String email, String uuid, String token, String username) {
        this.email = email;
        this.uuid = uuid;
        this.token = token;
        this.username = username;
    }
    
    public MicrosoftAccount(MicrosoftAccount other) {
        email = other.email;
        uuid = other.uuid;
        username = other.username;
    }
    
    // Required for deserialization without Unsafe
    @SuppressWarnings("unused")
    MicrosoftAccount() {
        
    }
}
