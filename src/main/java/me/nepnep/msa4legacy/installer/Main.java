package me.nepnep.msa4legacy.installer;

import net.minecraftforge.binarypatcher.ConsoleTool;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        final JFrame frame = new JFrame("MSA4Legacy Installer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JPanel layout = new JPanel(new GridLayout(2, 0, 0, 5));
        JButton button = new JButton("Install");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                final JFileChooser chooser = new JFileChooser();
                chooser.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent fileEvent) {
                        File file = chooser.getSelectedFile();
                        try {
                            String directory = file.getParentFile().getCanonicalPath() + File.separator;
                            ConsoleTool.main(new String[]{
                                    "--clean",
                                    file.getCanonicalPath(),
                                    "--output",
                                    directory + "launcher-patched.jar",
                                    "--apply",
                                    directory + "patches.lzma",
                                    "--data",
                                    "--unpatched"
                            });
                            System.out.println("Finished patching");
                        } catch (FileNotFoundException e) {
                            JOptionPane.showMessageDialog(
                                    frame, 
                                    "patches.lzma not found!", 
                                    "Error", 
                                    JOptionPane.ERROR_MESSAGE
                            );
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                chooser.showSaveDialog(frame);
            }
        });
        JLabel label = new JLabel("Select the launcher jar to install");
        label.setHorizontalAlignment(JLabel.CENTER);
        layout.add(label);
        layout.add(button);
        
        frame.setContentPane(layout);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
