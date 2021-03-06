package messageclient.ui;

import messageclient.api.Client;
import messageclient.api.MessageObserver;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientWindow extends JFrame implements MessageObserver, Runnable {
    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 16);
    private volatile Client client;
    private final JTextArea textArea;
    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

    public ClientWindow(Client client) {
        super("MessageClient");
        textArea = createTextArea();
        this.client = client;

        add(createScrollableTextArea(textArea), BorderLayout.CENTER);
        add(createTextField(), BorderLayout.SOUTH);

        setSize(600, 800);
        setVisible(true);
        setLocationRelativeTo(null);

        var superThis = this;
        addWindowListener(new WindowAdapter () {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                try {
                    superThis.client.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        });
    }

    public static ClientWindow fromClient(Client client) {
        ClientWindow w = new ClientWindow(client);
        client.register(w);
        return w;
    }


    private JTextField createTextField() {
        JTextField textField = new JTextField();
        textField.setFont(FONT);
        textField.addActionListener(e -> {
            var msg = e.getActionCommand();
            try {
                client.sendMessage(msg + "\n");
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            textField.setText("");
        });
        return textField;
    }

    private static JTextArea createTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(FONT);
        DefaultCaret caret = new DefaultCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        textArea.setCaret(caret);
        return textArea;
    }

    private static JScrollPane createScrollableTextArea(JTextArea textArea) {
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setVerticalScrollBarPolicy (ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS );
        return scroll;
    }

    private void handleMessage(String message) {
        // Should be run from the event dispatch thread to not have races.
        assert SwingUtilities.isEventDispatchThread();
        if (message.startsWith("!")) {
            Scanner s = new Scanner(new StringReader(message.substring(1)));
            switch (s.next()) {
                case "clear":
                    textArea.setText("");
                    break;
            }
        } else {
            textArea.append(message);
        }
    }

    public void run() {
        synchronized (messages) {
            String message;
            while ((message = messages.poll()) != null)
                handleMessage(message);
        }
    }

    @Override
    public void receivedMessage(String message) {
        messages.add(message);
        SwingUtilities.invokeLater(this);
    }

    private void clientMessage(String message) {
        messages.add("-- " + message);
        SwingUtilities.invokeLater(this);
    }


    @Override
    public void connectionStarted(Client client) {
        this.client = client;
        SwingUtilities.invokeLater(() -> setVisible(true));
        clientMessage("Connected to " + client.getAddress() + "\n");
    }

    @Override
    public void connectionClosed() {
        clientMessage("Connection closed.\n");
    }
}
