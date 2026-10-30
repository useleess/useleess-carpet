package com.useleess.carpet.launcher;

import com.useleess.carpet.core.Downloader;
import com.useleess.carpet.core.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The installer window for a double-click start (user 2026-10-30: a double-clicked jar flashed a console and
 * vanished): its progress lines, the questions (EULA, start script) as buttons, and errors that stay visible. Tests
 * answer the question through {@code -Duseleess.carpet.answers=eula=yes} (or {@code no}) instead of clicking.
 */
final class Window {
    private final Messages m;
    private final Downloader.Log console;
    private final Map<String, String> answers = new HashMap<>();
    private JFrame frame;
    private JTextArea text;
    private JProgressBar bar;
    private JButton close;
    private JLabel highlight;

    Window(Messages m, Downloader.Log console) throws InterruptedException, InvocationTargetException {
        this.m = m;
        this.console = console;
        String auto = System.getProperty("useleess.carpet.answers", "");
        for (String pair : auto.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) answers.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame(m.get("gui.title"));
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            text = new JTextArea(14, 70);
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            bar = new JProgressBar();
            bar.setIndeterminate(true);
            close = new JButton(m.get("gui.close"));
            close.setEnabled(false);
            close.addActionListener(e -> System.exit(0));
            JPanel bottom = new JPanel(new BorderLayout(8, 0));
            bottom.add(bar, BorderLayout.CENTER);
            bottom.add(close, BorderLayout.EAST);
            highlight = new JLabel(" ");
            highlight.setOpaque(true);
            highlight.setBackground(new Color(255, 236, 140));
            highlight.setForeground(new Color(40, 30, 0));
            highlight.setFont(highlight.getFont().deriveFont(Font.BOLD, 20f));
            highlight.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 150, 0), 2), BorderFactory.createEmptyBorder(14, 16, 14, 16)));
            highlight.setVisible(false);
            JPanel root = new JPanel(new BorderLayout(0, 8));
            root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            root.add(highlight, BorderLayout.NORTH);
            root.add(new JScrollPane(text), BorderLayout.CENTER);
            root.add(bottom, BorderLayout.SOUTH);
            frame.setContentPane(root);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    boolean automatic() {
        return !answers.isEmpty();
    }

    /** Each line goes into the window and to the launcher's console output (logs, tests). */
    Downloader.Log log() {
        return (key, args) -> {
            console.say(key, args);
            line(m.get(key, args));
        };
    }

    private void line(String s) {
        SwingUtilities.invokeLater(() -> {
            text.append(s + "\n");
            text.setCaretPosition(text.getDocument().getLength());
        });
    }

    /** The one thing to remember, big and on top (user 2026-10-30: which script starts the server from now on). */
    void highlight(String s) {
        SwingUtilities.invokeLater(() -> {
            highlight.setText("<html>" + s.replace("&", "&amp;").replace("<", "&lt;") + "</html>");
            highlight.setVisible(true);
            frame.pack();
            frame.toFront();
        });
    }

    /** A yes/no question with our own button names; {@code name} is its answer key for tests ("yes"/"no"). */
    boolean ask(String name, String yesKey, String noKey, String questionKey, Object... args) {
        if (answers.containsKey(name)) return answers.get(name).equals("yes");
        AtomicInteger choice = new AtomicInteger(-1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                Object[] options = {m.get(yesKey), m.get(noKey)};
                choice.set(JOptionPane.showOptionDialog(frame, m.get(questionKey, args), m.get("gui.title"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]));
            });
        } catch (InterruptedException | InvocationTargetException e) {
            return false;
        }
        return choice.get() == 0;
    }

    /** The end: the progress bar stops and Close works; the window stays until Close (tests: closes by itself). */
    void finish(boolean ok) {
        SwingUtilities.invokeLater(() -> {
            bar.setIndeterminate(false);
            bar.setValue(ok ? 100 : 0);
            close.setEnabled(true);
        });
        if (automatic()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                // closing now
            }
            System.exit(ok ? 0 : 2);
        }
    }
}
