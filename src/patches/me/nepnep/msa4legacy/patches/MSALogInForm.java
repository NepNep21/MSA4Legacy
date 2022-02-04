package me.nepnep.msa4legacy.patches;

import net.minecraft.launcher.Launcher;
import net.minecraft.launcher.ui.popups.login.LogInPopup;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

public class MSALogInForm extends JPanel {
    public final MicrosoftAuth auth = new MicrosoftAuth(this);
    private final AccountConsumer consumer = new AccountConsumer();
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
                    auth.authenticate(emailField.getText()).thenAccept(consumer);
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
    
    private static class AccountConsumer implements Consumer<MicrosoftAccount> {
        @Override
        public void accept(MicrosoftAccount microsoftAccount) {
            Launcher.getCurrentInstance().getProfileManager().setSelectedUser(microsoftAccount.uuid);
        }
    }
}
