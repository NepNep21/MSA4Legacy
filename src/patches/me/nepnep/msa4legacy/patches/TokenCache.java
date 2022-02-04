package me.nepnep.msa4legacy.patches;

import com.microsoft.aad.msal4j.ITokenCacheAccessAspect;
import com.microsoft.aad.msal4j.ITokenCacheAccessContext;
import net.minecraft.launcher.Launcher;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;

public class TokenCache implements ITokenCacheAccessAspect {
    private final Logger logger = LogManager.getLogger();
    private final File file = new File(Launcher.getCurrentInstance().getLauncher().getWorkingDirectory(), "microsoft_accounts.json");
    private String data = readFile();

    @Override
    public void beforeCacheAccess(ITokenCacheAccessContext iTokenCacheAccessContext) {
        if (data != null) {
            iTokenCacheAccessContext.tokenCache().deserialize(data);
        }
    }

    @Override
    public void afterCacheAccess(ITokenCacheAccessContext iTokenCacheAccessContext) {
        data = iTokenCacheAccessContext.tokenCache().serialize();
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.write(data);
        } catch (IOException e) {
            logger.error("IOException while saving token cache", e);
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }

    private String readFile() {
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(file);
            return IOUtils.toString(stream);
        } catch (FileNotFoundException e) {
            logger.error("microsoft_accounts.json not found, ignoring", e);
            return null;
        } catch (IOException e) {
            logger.error("IOException while reading profiles for MSA", e);
            return null;
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }
}
