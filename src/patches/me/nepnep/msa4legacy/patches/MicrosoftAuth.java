package me.nepnep.msa4legacy.patches;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.microsoft.aad.msal4j.*;
import net.minecraft.launcher.Launcher;
import net.minecraft.launcher.ui.popups.login.ExistingUserListForm;
import net.minecraft.launcher.ui.popups.login.LogInPopup;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

public class MicrosoftAuth {
    public final PublicClientApplication app = PublicClientApplication.builder("810b4a0d-7663-4e28-8680-24458240dee4")
            .setTokenCacheAccessAspect(new TokenCache())
            .build();
    public final Gson gson = new Gson();
    @SuppressWarnings("all") // Beta
    public final Type accountSetType = new TypeToken<HashSet<MicrosoftAccount>>() {}.getType();
    public final File cacheInfoFile = new File(Launcher.getCurrentInstance().getLauncher().getWorkingDirectory(), "microsoft_account_info.json");
    private final Set<String> scopes = new HashSet<String>();
    private final String tenant = "consumers";
    private final Logger logger = LogManager.getLogger();
    private final TokenMapper tokenMapper = new TokenMapper();
    
    private final MSALogInForm form;

    public MicrosoftAuth(MSALogInForm form) {
        this.form = form;

        scopes.add("XboxLive.signin");
        addAllToDatabase();
    }

    public CompletableFuture<MicrosoftAccount> authenticate(final String email) {
        return acquireToken(email).thenApply(new Function<String, MicrosoftAccount>() {
            @Override
            public MicrosoftAccount apply(String oauthToken) {
                if (oauthToken == null) {
                    return null;
                }

                try {
                    JsonObject xblResponse = xblAuth(oauthToken);
                    String xblToken = xblResponse.get("Token").getAsString();
                    String userHash = xblResponse.get("DisplayClaims")
                            .getAsJsonObject()
                            .get("xui")
                            .getAsJsonArray()
                            .get(0)
                            .getAsJsonObject()
                            .get("uhs")
                            .getAsString();
                    String xstsToken = xstsAuth(xblToken);
                    String mcToken = minecraftAuth(xstsToken, userHash);
                    MicrosoftAccount msa = getProfile(mcToken, email);
                    addToDatabase(email, msa);
                    return msa;
                } catch (Exception e) {
                    logger.error("Failed to authenticate", e);
                }
                return null;
            }
        });
    }

    @SuppressWarnings("all") // Stupid but it works
    private CompletableFuture<String> acquireToken(String email) {
        try {
            for (IAccount account : app.getAccounts().get()) {
                if (account.username().equals(email)) {
                    SilentParameters param = SilentParameters.builder(scopes, account).tenant(tenant).build();
                    return app.acquireTokenSilently(param).thenApply(tokenMapper);
                }
            }
        } catch (ExecutionException e) {
            return deviceFlow(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Failed to get cached accounts", e);
        }

        return deviceFlow(null);
    }

    private JsonObject xblAuth(String oauthToken) throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) new URL("https://user.auth.xboxlive.com/user/authenticate").openConnection();
        conn.setRequestMethod("POST");

        JsonObject innerObject = new JsonObject();
        innerObject.addProperty("AuthMethod", "RPS");
        innerObject.addProperty("SiteName", "user.auth.xboxlive.com");
        innerObject.addProperty("RpsTicket", "d=" + oauthToken);

        JsonObject body = new JsonObject();
        body.add("Properties", innerObject);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");

        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");

        conn.setDoOutput(true);
        conn.connect();

        OutputStream out = null;
        try {
            out = conn.getOutputStream();
            out.write(bodyToBytes(body));
            return gson.fromJson(new InputStreamReader(conn.getInputStream()), JsonObject.class);
        } finally {
            IOUtils.closeQuietly(out);
            conn.disconnect();
        }
    }

    private String xstsAuth(String xblToken) throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) new URL("https://xsts.auth.xboxlive.com/xsts/authorize").openConnection();
        conn.setRequestMethod("POST");

        JsonObject innerObject = new JsonObject();
        innerObject.addProperty("SandboxId", "RETAIL");

        JsonArray tokenArray = new JsonArray();
        tokenArray.add(new JsonPrimitive(xblToken));
        innerObject.add("UserTokens", tokenArray);

        JsonObject body = new JsonObject();
        body.add("Properties", innerObject);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");

        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");

        conn.setDoOutput(true);
        conn.connect();

        OutputStream out = null;
        try {
            out = conn.getOutputStream();
            out.write(bodyToBytes(body));
            JsonObject responseBody = gson.fromJson(new InputStreamReader(conn.getInputStream()), JsonObject.class);
            if (conn.getResponseCode() == 401) {
                throw new XErrException("XSTS auth returned 401 with XErr " + responseBody.get("XErr").getAsLong());
            }
            return responseBody.get("Token").getAsString();
        } finally {
            IOUtils.closeQuietly(out);
            conn.disconnect();
        }
    }

    private String minecraftAuth(String xstsToken, String userHash) throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) new URL("https://api.minecraftservices.com/authentication/login_with_xbox").openConnection();
        conn.setRequestMethod("POST");

        JsonObject body = new JsonObject();
        body.addProperty("identityToken", String.format("XBL3.0 x=%s;%s", userHash, xstsToken));

        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.connect();

        OutputStream out = null;
        try {
            out = conn.getOutputStream();
            out.write(bodyToBytes(body));
            return gson.fromJson(new InputStreamReader(conn.getInputStream()), JsonObject.class).get("access_token").getAsString();
        } finally {
            IOUtils.closeQuietly(out);
            conn.disconnect();
        }
    }

    private MicrosoftAccount getProfile(String mcToken, String email) throws IOException {
        HttpsURLConnection conn = (HttpsURLConnection) new URL("https://api.minecraftservices.com/minecraft/profile").openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + mcToken);
        conn.connect();

        try {
            JsonObject response = gson.fromJson(new InputStreamReader(conn.getInputStream()), JsonObject.class);
            JsonElement uuidElement = response.get("id");
            if (uuidElement == null) {
                logger.error("Account does not have profile");
                return null;
            }
            return new MicrosoftAccount(email, uuidElement.getAsString(), mcToken, response.get("name").getAsString());
        } finally {
            conn.disconnect();
        }
    }

    @SuppressWarnings("all")
    private CompletableFuture<String> deviceFlow(ExecutionException e) {
        if (e != null) {
            logger.debug("Couldn't load account from cache, starting device flow", e);
        }
        DeviceCodeFlowParameters param = DeviceCodeFlowParameters.builder(scopes, new Consumer<DeviceCode>() {
            @Override
            public void accept(DeviceCode deviceCode) {
                for (Component comp : form.getComponents()) {
                    if (comp instanceof JTextPane && ((JTextPane) comp).getText().contains("/link")) { // Only one with this is the code
                        form.remove(comp);
                        break;
                    }
                }
                JTextPane text = new JTextPane();
                text.setEditable(false);
                text.setBackground(null);
                text.setBorder(null);
                text.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                text.setText(deviceCode.message());
                form.add(text);
                form.validate();
            }
        }).tenant(tenant).build();
        try {
            return app.acquireToken(param).thenApply(tokenMapper);
        } catch (Exception ex) {
            logger.error("Failed to get oauth token through device flow", ex);
            return CompletableFuture.completedFuture(null);
        }
    }

    @SuppressWarnings("all")
    private byte[] bodyToBytes(JsonObject body) {
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("all")
    private void addAllToDatabase() {
        try {
            if (!cacheInfoFile.exists()) {
                cacheInfoFile.createNewFile();
            }
            
            List<String> emails = new ArrayList<String>();
            for (IAccount account : app.getAccounts().get()) {
                emails.add(account.username());
            }

            boolean changed = false;
            HashSet<MicrosoftAccount> accounts;
            try {
                accounts = gson.fromJson(FileUtils.readFileToString(cacheInfoFile, "UTF-8"), accountSetType);
            } catch (JsonSyntaxException e) {
                accounts = new HashSet<MicrosoftAccount>();
                changed = true;
            }
            
            if (accounts == null) {
                accounts = new HashSet<MicrosoftAccount>();
                changed = true;
            }
            
            for (MicrosoftAccount account : accounts) {
                String email = account.email;
                if (emails.contains(email)) { // In case there is a disparity between the caches
                    addToDatabase(email, account);
                } else {
                    accounts.remove(account);
                    changed = true;
                }
            }
            if (changed) {
                String raw = gson.toJson(accounts);
                FileUtils.write(cacheInfoFile, raw, "UTF-8");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Exception adding microsoft accounts", e);
        }
    }

    private void addToDatabase(final String email, MicrosoftAccount msa) {
        Launcher.getCurrentInstance().getProfileManager().getAuthDatabase().msaByEmail.put(email, msa);
        LogInPopup popup = form.popup;
        ExistingUserListForm userListForm = popup.getExistingUserListForm();
        JComboBox box = userListForm.userDropdown;
        List<String> items = new ArrayList<String>();
        for (int i = 0; i < box.getItemCount(); i++) {
            items.add((String) box.getItemAt(i));
        }
        boolean changed = false;
        boolean shouldRepack = false;
        if (!items.contains(email)) {
            box.addItem(email);
            changed = true;
        }
        if (!Arrays.asList(popup.getComponents()).contains(userListForm)) {
            popup.add(userListForm);
            changed = shouldRepack = true;
        }
        
        // Do not optimize by conditionally calling this when the amount changes, it breaks things somehow
        userListForm.createPartialInterface();
        
        if (shouldRepack) {
            popup.repack();
        }
        
        if (changed) {
            popup.invalidate();
            popup.validate();
        }
    }


    private static class TokenMapper implements Function<IAuthenticationResult, String> {
        @Override
        public String apply(IAuthenticationResult iAuthenticationResult) {
            return iAuthenticationResult.accessToken();
        }
    }

    private static class XErrException extends RuntimeException {
        public XErrException(String message) {
            super(message);
        }
    }
}
        
        
