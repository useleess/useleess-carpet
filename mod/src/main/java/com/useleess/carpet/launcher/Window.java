package com.useleess.carpet.launcher;

import com.useleess.carpet.core.Downloader;
import com.useleess.carpet.core.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The installer window for a double-click start (user 2026-10-30: a double-clicked jar flashed a console and
 * vanished): its progress lines, the questions (EULA, start script) as buttons, and errors that stay visible. Tests
 * answer the questions through {@code -Duseleess.carpet.answers=eula=yes,script=update} instead of clicking.
 */
final class Window {
    private final Messages m;
    private final Map<String, String> answers = new HashMap<>();
    private JFrame frame;
    private JTextArea text;
    private JProgressBar bar;
    private JButton close;

    Window(Messages m) throws InterruptedException, InvocationTargetException {
        this.m = m;
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
            JPanel root = new JPanel(new BorderLayout(0, 8));
            root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
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

    Downloader.Log log() {
        return (key, args) -> line(m.get(key, args));
    }

    void line(String s) {
        System.out.println(m.get("log.prefix") + s);
        SwingUtilities.invokeLater(() -> {
            text.append(s + "\n");
            text.setCaretPosition(text.getDocument().getLength());
        });
    }

    /** A yes/no question with our own button names; {@code name} is its answer key for tests ("yes"/"no"). */
    boolean ask(String name, String yesKey, String noKey, String questionKey, Object... args) {
        if (answers.containsKey(name)) return answers.get(name).equals("yes") || answers.get(name).equals("update");
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

    /** The end: the progress bar stops and Close works; a good end closes by itself after a moment. */
    void finish(boolean ok) {
        SwingUtilities.invokeLater(() -> {
            bar.setIndeterminate(false);
            bar.setValue(ok ? 100 : 0);
            close.setEnabled(true);
        });
        if (ok || automatic()) {
            try {
                Thread.sleep(automatic() ? 1000 : 15000);
            } catch (InterruptedException ignored) {
                // closing now
            }
            System.exit(ok ? 0 : 2);
        }
    }
}
