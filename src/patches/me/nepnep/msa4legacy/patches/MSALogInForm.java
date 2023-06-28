package me.nepnep.msa4legacy.patches;

import net.minecraft.launcher.ui.popups.login.LogInPopup;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.function.Consumer;

public class MSALogInForm extends JPanel {
    private final AccountConsumer consumer = new AccountConsumer();
    private final Logger logger = LogManager.getLogger();
    public final LogInPopup popup;
    
    public MSALogInForm(LogInPopup popup) {
        super(new GridLayout(5, 1, 0, 10)); // Extra one for device code
        this.popup = popup;
        
        JLabel label = new JLabel("Email");
        add(label);
        final JTextField emailField = new JTextField();
        add(emailField);
        JButton loginButton = new JButton("Log In");
        add(loginButton);
        JButton backButton = new JButton("Back");
        add(backButton);
        
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (emailField.getDocument() != null) {
                    MicrosoftAuth.authenticate(emailField.getText(), MSALogInForm.this).thenAccept(consumer);
                }
            }
        });
        
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });
    }
    
    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        for (Component button : popup.buttonPanel.getComponents()) {
            button.setVisible(!visible);
        }
        popup.getLogInForm().setVisible(!visible);
    }
    
    private class AccountConsumer implements Consumer<MicrosoftAccount> {
        @Override
        public void accept(MicrosoftAccount microsoftAccount) {
            popup.setLoggedIn(microsoftAccount);
            File cacheFile = MicrosoftAuth.cacheInfoFile;
            try {
                if (!cacheFile.exists()) {
                    cacheFile.createNewFile();
                }
                HashSet<MicrosoftAccount> cached = MicrosoftAuth.gson.fromJson(FileUtils.readFileToString(cacheFile, "UTF-8"), MicrosoftAuth.accountSetType);
                cached.add(microsoftAccount);
                String raw = MicrosoftAuth.gson.toJson(cached);
                FileUtils.write(cacheFile, raw, "UTF-8");
            } catch (IOException e) {
                logger.error("IOException while adding account to info cache", e);
            }
        }
    }
}
