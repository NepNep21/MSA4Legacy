package me.nepnep.msa4legacy.patches;

import java.util.Objects;

public class MicrosoftAccount {
    public String email;
    public String uuid;
    public transient String token;
    public String username;
    
    public MicrosoftAccount(String email, String uuid, String token, String username) {
        this.email = email;
        this.uuid = uuid;
        this.token = token;
        this.username = username;
    }
    
    // Required for deserialization without Unsafe
    @SuppressWarnings("unused")
    MicrosoftAccount() {
        
    }

    // For Set uniqueness
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } 
        
        if (obj instanceof MicrosoftAccount) {
            MicrosoftAccount other = (MicrosoftAccount) obj;
            
            return email.equals(other.email)
                    && uuid.equals(other.uuid)
                    && username.equals(other.username);
        }
        return false;
    }

    @SuppressWarnings("all") // Java 8
    @Override
    public int hashCode() {
        return Objects.hash(email, uuid, username);
    }
}
