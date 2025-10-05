package burp.gui;

import burp.api.montoya.MontoyaApi;
import burp.models.BinServer;
import burp.models.RequestBin;
import burp.services.BinManager;
import burp.gui.ToastNotification.MessageType;

import javax.swing.*;
import java.awt.*;
import java.awt.Desktop;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Dialog for creating new RequestBin
 * Based on CreateBinModal from requestbin.saas
 */
public class CreateBinDialog extends JDialog {
    private final MontoyaApi api;
    private final BinManager binManager;
    
    private JTextField nameField;
    private JComboBox<BinServer> serverCombo;
    private JLabel serverStatusLabel;
    private JLabel serverInfoLabel;
    private JButton refreshServersButton;
    private JButton createButton;
    private JButton cancelButton;
    
    // Removed unused field: availableServers
    private boolean isCreating = false;
    private RequestBin createdBin = null;

    public CreateBinDialog(Component parent, MontoyaApi api, BinManager binManager) {
        super((Frame) SwingUtilities.getWindowAncestor(parent), "Create New RequestBin", true);
        this.api = api;
        this.binManager = binManager;
        
        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        loadServers();
        generateDefaultName();
        
        setSize(400, 280);
        setLocationRelativeTo(parent);
    }

    private void initializeComponents() {
        nameField = new JTextField(15);
        
        serverCombo = new JComboBox<>();
        serverCombo.setRenderer(new ServerListRenderer());
        serverCombo.setEnabled(false);
        
        serverStatusLabel = new JLabel("Loading servers...");
        serverStatusLabel.setFont(serverStatusLabel.getFont().deriveFont(Font.ITALIC, 11f));
        
        // Server info with reference to requestbin.net (clickable)
        serverInfoLabel = new JLabel("Server list collected and maintained by requestbin.net");
        serverInfoLabel.setFont(serverInfoLabel.getFont().deriveFont(Font.ITALIC, 9f));
        serverInfoLabel.setForeground(Color.BLUE);
        serverInfoLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        serverInfoLabel.setToolTipText("Click to visit requestbin.net");
        
        refreshServersButton = new JButton("↻");
        refreshServersButton.setToolTipText("Refresh servers");
        refreshServersButton.setPreferredSize(new Dimension(30, 25));
        refreshServersButton.setEnabled(false);
        
        createButton = new JButton("Create Bin");
        createButton.setPreferredSize(new Dimension(90, 25));
        
        cancelButton = new JButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(70, 25));
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());
        
        // Main panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        
        // Name section
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        namePanel.add(new JLabel("Name: "));
        namePanel.add(nameField);
        mainPanel.add(namePanel);
        mainPanel.add(Box.createVerticalStrut(5));
        
        // Remove type section - now only server-based bins
        
        // Server section - more compact
        JPanel serverPanel = new JPanel();
        serverPanel.setLayout(new BoxLayout(serverPanel, BoxLayout.Y_AXIS));
        serverPanel.setBorder(BorderFactory.createTitledBorder("Server"));
        
        JPanel serverSelectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        serverSelectPanel.add(new JLabel("Select: "));
        serverSelectPanel.add(serverCombo);
        serverSelectPanel.add(Box.createHorizontalStrut(5));
        serverSelectPanel.add(refreshServersButton);
        
        serverPanel.add(serverSelectPanel);
        serverPanel.add(Box.createVerticalStrut(1));
        // serverPanel.add(serverStatusLabel);
        JPanel serverStatusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        serverStatusPanel.add(serverStatusLabel);
        serverPanel.add(serverStatusPanel);
        
        // Server info panel with left alignment
        JPanel serverInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        serverInfoPanel.add(serverInfoLabel);
        serverPanel.add(serverInfoPanel);
        
        mainPanel.add(serverPanel);
        mainPanel.add(Box.createVerticalGlue());
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(createButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        // Server info label click handler for opening requestbin.net
        serverInfoLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(java.net.URI.create("https://requestbin.net?utm_source=burp_extension&utm_medium=create_bin_dialog"));
                } catch (Exception ex) {
                    // Fallback if desktop browsing fails
                    api.logging().logToOutput("Visit https://requestbin.net for more information");
                }
            }
        });
        
        // Name field handler
        nameField.addActionListener(e -> updateCreateButtonState());
        nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateCreateButtonState(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateCreateButtonState(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateCreateButtonState(); }
        });
        
        // Server combo handler
        serverCombo.addActionListener(e -> updateCreateButtonState());
        
        // Refresh servers button
        refreshServersButton.addActionListener(e -> {
            binManager.clearServerCache();
            loadServers();
        });
        
        // Create button
        createButton.addActionListener(e -> createBin());
        
        // Cancel button
        cancelButton.addActionListener(e -> {
            createdBin = null;
            dispose();
        });
        
        // Enter key on name field
        nameField.addActionListener(e -> {
            if (createButton.isEnabled()) {
                createBin();
            }
        });
    }

    private void generateDefaultName() {
        // Generate name like: bin-1005-1430 (MMDD-HHMM)
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMdd-HHmm");
        String defaultName = "bin-" + now.format(formatter);
        nameField.setText(defaultName);
        nameField.selectAll(); // Select text so user can easily replace it
    }

    private void loadServers() {
        serverStatusLabel.setText("Loading servers...");
        serverCombo.removeAllItems();
        serverCombo.setEnabled(false);
        refreshServersButton.setEnabled(false);
        
        binManager.getAvailableServers().thenAccept(servers -> {
            SwingUtilities.invokeLater(() -> {
                for (BinServer server : servers) {
                    serverCombo.addItem(server);
                }
                
                if (!servers.isEmpty()) {
                    // Select default server if available
                    BinServer defaultServer = servers.stream()
                            .filter(BinServer::isDefault)
                            .findFirst()
                            .orElse(servers.get(0));
                    serverCombo.setSelectedItem(defaultServer);
                    
                    serverStatusLabel.setText("✓ " + servers.size() + " servers loaded");
                    serverStatusLabel.setForeground(new Color(0, 150, 0));
                } else {
                    serverStatusLabel.setText("⚠ No servers available");
                    serverStatusLabel.setForeground(Color.RED);
                }
                
                // Always enable server selection since we only support cloud bins now
                serverCombo.setEnabled(!servers.isEmpty());
                refreshServersButton.setEnabled(true);
                
                updateCreateButtonState();
            });
        }).exceptionally(throwable -> {
            SwingUtilities.invokeLater(() -> {
                serverStatusLabel.setText("✗ Failed to load servers");
                serverStatusLabel.setForeground(Color.RED);
                refreshServersButton.setEnabled(true);
                
                ToastNotification.showToast(this, "Failed to load servers: " + throwable.getMessage(), 
                        MessageType.ERROR);
            });
            return null;
        });
    }

    private void updateCreateButtonState() {
        String name = nameField.getText().trim();
        boolean hasName = !name.isEmpty();
        boolean hasServer = serverCombo.getSelectedItem() != null;
        boolean canCreate = hasName && hasServer;
        
        createButton.setEnabled(canCreate && !isCreating);
    }

    private void createBin() {
        if (isCreating) return;
        
        String name = nameField.getText().trim();
        String note = ""; // No note field in compact dialog
        
        if (name.isEmpty()) {
            ToastNotification.showToast(this, "Please enter a bin name", MessageType.ERROR);
            nameField.requestFocus();
            return;
        }
        
        isCreating = true;
        createButton.setEnabled(false);
        createButton.setText("Creating...");
        
        // Create bin with selected server
        BinServer selectedServer = (BinServer) serverCombo.getSelectedItem();
        if (selectedServer == null) {
            ToastNotification.showToast(this, "Please select a server", MessageType.ERROR);
            isCreating = false;
            createButton.setEnabled(true);
            createButton.setText("Create Bin");
            return;
        }
        
        binManager.createBin(name, note, selectedServer)
                .thenAccept(bin -> {
                    SwingUtilities.invokeLater(() -> {
                        createdBin = bin;
                        ToastNotification.showToast(this, "Bin created successfully!", MessageType.SUCCESS);
                        dispose();
                    });
                })
                .exceptionally(throwable -> {
                    SwingUtilities.invokeLater(() -> {
                        api.logging().logToError("Error creating bin: " + throwable.getMessage());
                        ToastNotification.showToast(this, "Failed to create bin: " + throwable.getMessage(), 
                                MessageType.ERROR);
                        
                        isCreating = false;
                        createButton.setEnabled(true);
                        createButton.setText("Create Bin");
                    });
                    return null;
                });
    }

    /**
     * Get the created bin (null if cancelled or failed)
     */
    public RequestBin getCreatedBin() {
        return createdBin;
    }

    /**
     * Custom renderer for server combo box
     */
    private static class ServerListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof BinServer) {
                BinServer server = (BinServer) value;
                setText(server.getDisplayName() + " " + server.getHealthStatusDisplay());
                
                // Color based on health status
                if (!isSelected) {
                    switch (server.getHealthStatus()) {
                        case HEALTHY:
                            setForeground(new Color(0, 150, 0));
                            break;
                        case UNHEALTHY:
                            setForeground(Color.RED);
                            break;
                        case UNKNOWN:
                        default:
                            setForeground(Color.GRAY);
                            break;
                    }
                }
            }
            
            return this;
        }
    }
}