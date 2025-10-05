package burp.gui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import burp.gui.ToastNotification.MessageType;
import burp.models.RequestBin;
import interactsh.InteractshEntry;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Individual bin tab with its own logs and controls
 * Each tab represents one RequestBin
 */
public class BinTab extends JPanel {
    private final MontoyaApi api;
    private final RequestBin bin;
    private burp.services.PollingService pollingService;
    
    // UI Components
    private JSplitPane mainSplitPane;
    private JSplitPane viewersSplitPane;
    private JTable logTable;
    private LogTableModel logTableModel;
    private JScrollPane scrollPane;
    private JPanel controlsPanel;
    private JLabel urlLabel;
    private JButton copyUrlButton;
    private JButton refreshButton;
    private JButton clearLogButton;
    
    // Filter components
    private TableRowSorter<TableModel> tableSorter;
    private JToggleButton unreadFilterButton;
    private JButton markAllReadButton;
    
    // Viewers
    private HttpRequestEditor requestViewer;
    private HttpResponseEditor responseViewer;
    private JPanel resultsCardPanel;
    private CardLayout resultsLayout;
    private JTextArea genericDetailsViewer;
    
    // Empty state guide
    private JPanel emptyGuidePanel;
    private JPanel tableContainerPanel;
    private CardLayout tableContainerLayout;
    
    // Data
    private final List<InteractshEntry> log = new ArrayList<>();
    private int unreadCount = 0;
    
    // Performance optimization for storage updates
    private final java.util.Set<String> pendingViewedUpdates = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile long lastStorageUpdate = 0;
    private static final long STORAGE_UPDATE_THROTTLE_MS = 2000; // 2 seconds throttle

    public BinTab(MontoyaApi api, RequestBin bin) {
        this.api = api;
        this.bin = bin;
        
        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        initializeBinSession();
    }

    private void initializeComponents() {
        setLayout(new BorderLayout());
        
        // Create table model and table
        logTableModel = new LogTableModel();
        logTable = new JTable(logTableModel);
        setupTable();
        
        // Create viewers
        requestViewer = api.userInterface().createHttpRequestEditor();
        responseViewer = api.userInterface().createHttpResponseEditor();
        viewersSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                requestViewer.uiComponent(), responseViewer.uiComponent());
        viewersSplitPane.setResizeWeight(0.5);
        
        // Generic viewer for non-HTTP protocols
        genericDetailsViewer = new JTextArea();
        genericDetailsViewer.setEditable(false);
        genericDetailsViewer.setWrapStyleWord(true);
        genericDetailsViewer.setLineWrap(true);
        
        // Results panel with card layout
        resultsLayout = new CardLayout();
        resultsCardPanel = new JPanel(resultsLayout);
        resultsCardPanel.add(new JScrollPane(genericDetailsViewer), "GENERIC_VIEW");
        resultsCardPanel.add(viewersSplitPane, "HTTP_VIEW");
        
        // Controls panel
        createControlsPanel();
        
        // Create empty state guide
        createEmptyGuidePanel();
        
        // Table container with CardLayout for empty state
        tableContainerLayout = new CardLayout();
        tableContainerPanel = new JPanel(tableContainerLayout);
        
        scrollPane = new JScrollPane(logTable);
        tableContainerPanel.add(scrollPane, "TABLE_VIEW");
        tableContainerPanel.add(emptyGuidePanel, "EMPTY_VIEW");
        
        // Main split pane
        mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableContainerPanel, resultsCardPanel);
        mainSplitPane.setResizeWeight(0); // 50/50 split for better detail viewing
    }

    private void setupTable() {
        logTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logTable.setRowHeight(28); // Slightly taller rows
        logTable.getTableHeader().setReorderingAllowed(false);
        
        // Better selection highlighting and focus management
        logTable.setSelectionBackground(new Color(51, 153, 255)); // Blue selection
        logTable.setSelectionForeground(Color.WHITE);
        logTable.setFocusable(true);
        logTable.setRequestFocusEnabled(true);
        
        // Ensure selection persists
        logTable.setCellSelectionEnabled(false);
        logTable.setRowSelectionAllowed(true);
        logTable.setColumnSelectionAllowed(false);
        
        // Set up sorting
        tableSorter = new TableRowSorter<>(logTableModel);
        logTable.setRowSorter(tableSorter);
        
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(0, SortOrder.DESCENDING)); // Sort by ID descending
        tableSorter.setSortKeys(sortKeys);
        
        // Set column widths
        TableColumn idColumn = logTable.getColumnModel().getColumn(0);
        idColumn.setPreferredWidth(40);
        idColumn.setMaxWidth(60);
        
        TableColumn protocolColumn = logTable.getColumnModel().getColumn(1);
        protocolColumn.setPreferredWidth(80);
        protocolColumn.setMaxWidth(100);
        
        TableColumn timeColumn = logTable.getColumnModel().getColumn(2);
        timeColumn.setPreferredWidth(150);
        
        TableColumn addressColumn = logTable.getColumnModel().getColumn(3);
        addressColumn.setPreferredWidth(120);
        
        TableColumn uidColumn = logTable.getColumnModel().getColumn(4);
        uidColumn.setPreferredWidth(80);
        uidColumn.setMaxWidth(120);
        
        // Custom renderer
        LogTableCellRenderer renderer = new LogTableCellRenderer();
        for (int i = 0; i < logTable.getColumnCount(); i++) {
            logTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        
        // Left align header text
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setHorizontalAlignment(JLabel.LEFT);
        for (int i = 0; i < logTable.getColumnModel().getColumnCount(); i++) {
            logTable.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);
        }
    }

    private void createControlsPanel() {
        controlsPanel = new JPanel(new BorderLayout());
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Left panel for main controls
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        
        // Bin info
        // JLabel binLabel = new JLabel("Bin: " + bin.getName());
        // binLabel.setFont(binLabel.getFont().deriveFont(Font.BOLD));
        // leftPanel.add(binLabel);
        
        leftPanel.add(new JSeparator(SwingConstants.VERTICAL));
        
        // URL display and copy
        urlLabel = new JLabel("URL: " + bin.getShortUrl());
        urlLabel.setFont(urlLabel.getFont().deriveFont(Font.PLAIN, 11f));
        urlLabel.setForeground(Color.GRAY);
        leftPanel.add(urlLabel);
        
        copyUrlButton = new JButton("Copy URL");
        copyUrlButton.setBackground(new Color(216, 102, 51));
        copyUrlButton.setForeground(Color.WHITE);
        copyUrlButton.setOpaque(true);
        copyUrlButton.setBorderPainted(false);
        copyUrlButton.setFont(copyUrlButton.getFont().deriveFont(Font.BOLD, 11f));
        leftPanel.add(copyUrlButton);
        
        leftPanel.add(Box.createHorizontalStrut(10));
        
        // Action buttons
        refreshButton = new JButton("↻ Refresh");
        clearLogButton = new JButton("Clear Log");
        
        leftPanel.add(refreshButton);
        leftPanel.add(clearLogButton);
        
        // Add separator before filters
        leftPanel.add(Box.createHorizontalStrut(15));
        leftPanel.add(new JSeparator(SwingConstants.VERTICAL));
        leftPanel.add(Box.createHorizontalStrut(5));
        
        // Create filter components directly in left panel
        createFilterComponents(leftPanel);
        
        // Right panel for powered-by text
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        
        JLabel poweredByLabel = new JLabel("Powered by RequestBin.net");
        poweredByLabel.setFont(poweredByLabel.getFont().deriveFont(Font.ITALIC, 10f));
        poweredByLabel.setForeground(new Color(108, 117, 125)); // Subtle gray color
        poweredByLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        poweredByLabel.setToolTipText("Visit RequestBin.net for enhanced features");
        
        // Click handler for powered-by link
        poweredByLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://requestbin.net?utm_source=burp_extension&utm_medium=controls_panel"));
                } catch (Exception ex) {
                    api.logging().logToOutput("Visit https://requestbin.net for enhanced features");
                }
            }
            
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                poweredByLabel.setForeground(new Color(3, 102, 214)); // Blue on hover
            }
            
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                poweredByLabel.setForeground(new Color(108, 117, 125)); // Back to gray
            }
        });
        
        rightPanel.add(poweredByLabel);
        
        // Add panels to main controls panel
        controlsPanel.add(leftPanel, BorderLayout.CENTER);
        controlsPanel.add(rightPanel, BorderLayout.EAST);
    }
    
    private void createFilterComponents(JPanel targetPanel) {
        JLabel filterLabel = new JLabel("Filter:");
        filterLabel.setFont(filterLabel.getFont().deriveFont(Font.BOLD, 11f));
        targetPanel.add(filterLabel);
        
        ButtonGroup filterGroup = new ButtonGroup();
        String[] protocols = {"All", "HTTP", "DNS", "SMTP", "LDAP", "SMB", "FTP"};
        
        for (String protocol : protocols) {
            JToggleButton filterButton = new JToggleButton(protocol);
            filterButton.setFont(filterButton.getFont().deriveFont(Font.PLAIN, 10f));
            filterButton.setMargin(new Insets(2, 6, 2, 6));
            
            filterButton.addActionListener(e -> {
                String selectedProtocol = filterButton.getText();
                if ("All".equals(selectedProtocol)) {
                    tableSorter.setRowFilter(null);
                    hideMarkAllReadButton();
                } else {
                    tableSorter.setRowFilter(RowFilter.regexFilter("(?i)" + selectedProtocol, 1)); // Column 1 is Protocol
                    hideMarkAllReadButton();
                }
            });
            
            filterGroup.add(filterButton);
            targetPanel.add(filterButton);
            
            if ("All".equals(protocol)) {
                filterButton.setSelected(true);
            }
        }
        
        // Add separator
        targetPanel.add(Box.createHorizontalStrut(10));
        targetPanel.add(new JSeparator(SwingConstants.VERTICAL));
        targetPanel.add(Box.createHorizontalStrut(5));
        
        // Unread filter button
        unreadFilterButton = new JToggleButton("Unread");
        unreadFilterButton.setFont(unreadFilterButton.getFont().deriveFont(Font.PLAIN, 10f));
        unreadFilterButton.setMargin(new Insets(2, 6, 2, 6));
        unreadFilterButton.setBackground(new Color(255, 193, 7)); // Warning yellow
        unreadFilterButton.setForeground(Color.BLACK);
        
        unreadFilterButton.addActionListener(e -> {
            if (unreadFilterButton.isSelected()) {
                // Clear other filters first
                for (AbstractButton btn : java.util.Collections.list(filterGroup.getElements())) {
                    btn.setSelected(false);
                }
                
                // Apply unread filter
                tableSorter.setRowFilter(new UnreadRowFilter());
                showMarkAllReadButton();
            } else {
                // Revert to All filter
                tableSorter.setRowFilter(null);
                hideMarkAllReadButton();
                // Select "All" button
                for (AbstractButton btn : java.util.Collections.list(filterGroup.getElements())) {
                    if ("All".equals(btn.getText())) {
                        btn.setSelected(true);
                        break;
                    }
                }
            }
        });
        
        targetPanel.add(unreadFilterButton);
        
        // Mark All Read button (initially hidden)
        markAllReadButton = new JButton("Mark All Read");
        markAllReadButton.setFont(markAllReadButton.getFont().deriveFont(Font.PLAIN, 10f));
        markAllReadButton.setMargin(new Insets(2, 6, 2, 6));
        markAllReadButton.setBackground(new Color(40, 167, 69)); // Success green
        markAllReadButton.setForeground(Color.WHITE);
        markAllReadButton.setBorderPainted(false);
        markAllReadButton.setOpaque(true);
        markAllReadButton.setVisible(false);
        
        markAllReadButton.addActionListener(e -> markAllInteractionsAsRead());
        
        targetPanel.add(Box.createHorizontalStrut(5));
        targetPanel.add(markAllReadButton);
    }
    
    private void createEmptyGuidePanel() {
        emptyGuidePanel = new JPanel(new BorderLayout());
        emptyGuidePanel.setBackground(Color.WHITE);
        
        // Main content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(50, 20, 50, 20));
        
        // Icon (using text since we don't have images)
        JLabel iconLabel = new JLabel("📋");
        iconLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 32));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Title
        JLabel titleLabel = new JLabel("No requests yet");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setForeground(new Color(64, 64, 64));
        
        // Main instruction
        // Create instruction panel with multiple labels instead of HTML
        JPanel instructionPanel = new JPanel();
        instructionPanel.setLayout(new BoxLayout(instructionPanel, BoxLayout.Y_AXIS));
        instructionPanel.setBackground(Color.WHITE);
        instructionPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel instructionLabel1 = new JLabel("Start making requests to your RequestBin URL and they'll");
        instructionLabel1.setFont(instructionLabel1.getFont().deriveFont(Font.PLAIN, 14f));
        instructionLabel1.setAlignmentX(Component.CENTER_ALIGNMENT);
        instructionLabel1.setHorizontalAlignment(SwingConstants.CENTER);
        instructionLabel1.setForeground(new Color(128, 128, 128));
        
        JLabel instructionLabel2 = new JLabel("appear here in real-time.");
        instructionLabel2.setFont(instructionLabel2.getFont().deriveFont(Font.PLAIN, 14f));
        instructionLabel2.setAlignmentX(Component.CENTER_ALIGNMENT);
        instructionLabel2.setHorizontalAlignment(SwingConstants.CENTER);
        instructionLabel2.setForeground(new Color(128, 128, 128));
        
        instructionPanel.add(instructionLabel1);
        instructionPanel.add(instructionLabel2);
        
        // Tip section
        JPanel tipPanel = new JPanel();
        tipPanel.setLayout(new BoxLayout(tipPanel, BoxLayout.Y_AXIS));
        tipPanel.setBackground(new Color(240, 248, 255)); // Light blue background
        tipPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(173, 216, 230), 1),
            BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));
        tipPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel tipTitle = new JLabel("💡 Tip: Use curl, Postman, or any HTTP client to send requests to your");
        tipTitle.setFont(tipTitle.getFont().deriveFont(Font.BOLD, 12f));
        tipTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        tipTitle.setForeground(new Color(51, 102, 153));
        
        JLabel tipUrl = new JLabel("unique URL.");
        tipUrl.setFont(tipUrl.getFont().deriveFont(Font.BOLD, 12f));
        tipUrl.setAlignmentX(Component.CENTER_ALIGNMENT);
        tipUrl.setForeground(new Color(51, 102, 153));
        
        tipPanel.add(tipTitle);
        tipPanel.add(Box.createVerticalStrut(3));
        tipPanel.add(tipUrl);
        
        // RequestBin.net promotion
        JPanel promoPanel = new JPanel();
        promoPanel.setLayout(new BoxLayout(promoPanel, BoxLayout.Y_AXIS));
        promoPanel.setBackground(new Color(248, 249, 250)); // Light gray background
        promoPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(208, 215, 222), 1),
            BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));
        promoPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel promoTitle = new JLabel("🌟 Want more features?");
        promoTitle.setFont(promoTitle.getFont().deriveFont(Font.BOLD, 13f));
        promoTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        promoTitle.setForeground(new Color(87, 96, 106));
        
        // Create multiple labels for the promo text
        JLabel promoText1 = new JLabel("Visit requestbin.net for enhanced features:");
        promoText1.setFont(promoText1.getFont().deriveFont(Font.PLAIN, 12f));
        promoText1.setAlignmentX(Component.CENTER_ALIGNMENT);
        promoText1.setForeground(new Color(87, 96, 106));
        
        JLabel promoLink = new JLabel("requestbin.net");
        promoLink.setFont(promoLink.getFont().deriveFont(Font.BOLD, 12f));
        promoLink.setAlignmentX(Component.CENTER_ALIGNMENT);
        promoLink.setForeground(new Color(3, 102, 214)); // Blue color
        promoLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        JLabel promoText2 = new JLabel("• Cloud storage & history");
        promoText2.setFont(promoText2.getFont().deriveFont(Font.PLAIN, 11f));
        promoText2.setAlignmentX(Component.CENTER_ALIGNMENT);
        promoText2.setForeground(new Color(87, 96, 106));
        
        JLabel promoText3 = new JLabel("• Custom request filtering");
        promoText3.setFont(promoText3.getFont().deriveFont(Font.PLAIN, 11f));
        promoText3.setAlignmentX(Component.CENTER_ALIGNMENT);
        promoText3.setForeground(new Color(87, 96, 106));
        
        JLabel promoText4 = new JLabel("• Advanced analytics");
        promoText4.setFont(promoText4.getFont().deriveFont(Font.PLAIN, 11f));
        promoText4.setAlignmentX(Component.CENTER_ALIGNMENT);
        promoText4.setForeground(new Color(87, 96, 106));
        
        JLabel promoText5 = new JLabel("• Webhook forwarding");
        promoText5.setFont(promoText5.getFont().deriveFont(Font.PLAIN, 11f));
        promoText5.setAlignmentX(Component.CENTER_ALIGNMENT);
        promoText5.setForeground(new Color(87, 96, 106));
        
        // Click handler for requestbin.net link
        promoLink.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://requestbin.net?utm_source=burp_extension&utm_medium=interactsh_tab"));
            } catch (Exception ex) {
                api.logging().logToOutput("Visit https://requestbin.net for enhanced features");
            }
            }
        });
        
        promoPanel.add(promoTitle);
        promoPanel.add(Box.createVerticalStrut(8));
        promoPanel.add(promoText1);
        promoPanel.add(Box.createVerticalStrut(3));
        promoPanel.add(promoLink);
        promoPanel.add(Box.createVerticalStrut(8));
        promoPanel.add(promoText2);
        promoPanel.add(promoText3);
        promoPanel.add(promoText4);
        promoPanel.add(promoText5);

        
        // Layout with proper spacing
        contentPanel.add(iconLabel);
        contentPanel.add(Box.createVerticalStrut(20));
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(instructionPanel);
        contentPanel.add(Box.createVerticalStrut(25));
        contentPanel.add(tipPanel);
        contentPanel.add(Box.createVerticalStrut(20));
        contentPanel.add(promoPanel);
        
        // Add to main panel
        emptyGuidePanel.add(contentPanel, BorderLayout.CENTER);
        
        // Initially show empty guide if no interactions
        updateEmptyState();
    }

    private void layoutComponents() {
        // Add controls panel directly (now includes filters)
        add(controlsPanel, BorderLayout.NORTH);
        add(mainSplitPane, BorderLayout.CENTER);
    }

    private void setupEventHandlers() {
        // Table selection listener
        logTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = logTable.getSelectedRow();
                if (selectedRow >= 0) {
                    int modelRow = logTable.convertRowIndexToModel(selectedRow);
                    InteractshEntry entry = log.get(modelRow);
                    showEntryDetails(entry);
                    
                    // Mark entry as viewed and update storage (async for performance)
                    markEntryAsViewedAsync(entry, modelRow);
                }
            }
        });
        
        // Simplified mouse handling for better performance
        logTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = logTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        int modelRow = logTable.convertRowIndexToModel(row);
                        showEntryDetails(log.get(modelRow));
                    }
                }
            }
        });
        
        // Button handlers
        copyUrlButton.addActionListener(e -> copyUrlToClipboard());
        refreshButton.addActionListener(e -> refreshSession());
        clearLogButton.addActionListener(e -> clearLog());
    }

    private void initializeBinSession() {
        // All bins are now server-based with correlation ID
        api.logging().logToOutput("Bin " + bin.getName() + " ready: " + bin.getUrl());
        api.logging().logToOutput("Bin correlation: " + bin.getCorrelationId() + ", server: " + bin.getServerId());
        
        updateUrlDisplay();
        
        // Load persisted interactions from storage (lazy loading)
        // Don't load immediately to prevent UI freeze, load in background after delay
        SwingUtilities.invokeLater(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // Wait 1 second for UI to initialize
                    reloadPersistedInteractions();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
    }

    /**
     * Reload persisted interactions for this bin from storage
     * Called every time tab becomes active to ensure fresh data
     */
    public void reloadPersistedInteractions() {
        try {
            // Clear current data
            synchronized (log) {
                log.clear();
                unreadCount = 0;
            }
            
            // Load fresh data from storage
            List<InteractshEntry> persistedInteractions = getPersistedInteractionsFromStorage();
            
            if (persistedInteractions != null && !persistedInteractions.isEmpty()) {
                // Sort interactions by timestamp (newest first)
                persistedInteractions.sort((a, b) -> b.timestamp.compareTo(a.timestamp));
                
                // Add all persisted interactions to the table
                synchronized (log) {
                    for (InteractshEntry entry : persistedInteractions) {
                        log.add(entry);
                        if (!entry.isRead()) {
                            unreadCount++;
                        }
                    }
                }
            }
            
            // Update table model and empty state
            SwingUtilities.invokeLater(() -> {
                logTableModel.fireTableDataChanged();
                updateEmptyState();
            });
            
        } catch (Exception e) {
            api.logging().logToError("Error reloading interactions for bin " + bin.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Get persisted interactions from storage for this bin
     * This duplicates some logic from BinService to avoid circular dependencies
     */
    private List<InteractshEntry> getPersistedInteractionsFromStorage() {
        List<InteractshEntry> interactions = new ArrayList<>();
        final int MAX_INTERACTIONS = 100; // Limit to prevent UI freeze
        
        try {
            if (bin.getUniqueId() == null || bin.getUniqueId().isEmpty()) {
                return interactions;
            }
            
            String userHome = System.getProperty("user.home");
            java.io.File storageDir = new java.io.File(userHome, ".requestbin-collaborator");
            java.io.File dataFile = new java.io.File(storageDir, "interactions-" + bin.getUniqueId() + ".json");
            
            if (!dataFile.exists()) {
                api.logging().logToOutput("[BinTab] No persisted interactions file for bin: " + bin.getName());
                return interactions;
            }
            
            // Check file size first
            long fileSize = dataFile.length();
            if (fileSize > 10 * 1024 * 1024) { // 10MB limit
                api.logging().logToOutput("[BinTab] Interactions file too large (" + fileSize + " bytes), skipping load for bin: " + bin.getName());
                return interactions;
            }
            
            String existingData = new String(java.nio.file.Files.readAllBytes(dataFile.toPath()));
            org.json.JSONArray storedInteractions = new org.json.JSONArray(existingData);
            
            int totalStored = storedInteractions.length();
            int toLoad = Math.min(totalStored, MAX_INTERACTIONS);
            
            api.logging().logToOutput("[BinTab] Found " + totalStored + " stored interactions, loading " + toLoad + " for bin: " + bin.getName());
            
            // Convert stored interactions back to InteractshEntry objects (load recent first)
            for (int i = 0; i < toLoad; i++) {
                try {
                    org.json.JSONObject interaction = storedInteractions.getJSONObject(i);
                    InteractshEntry entry = parseInteractionFromStorage(interaction);
                    if (entry != null) {
                        interactions.add(entry);
                    }
                } catch (Exception e) {
                    api.logging().logToError("[BinTab] Error parsing interaction " + i + " for bin " + bin.getName() + ": " + e.getMessage());
                    // Continue with other interactions
                }
            }
            
            if (totalStored > MAX_INTERACTIONS) {
                api.logging().logToOutput("[BinTab] Loaded " + interactions.size() + "/" + totalStored + " interactions (limited for performance) for bin: " + bin.getName());
            } else {
                api.logging().logToOutput("[BinTab] Successfully parsed " + interactions.size() + " interactions for bin: " + bin.getName());
            }
            
        } catch (Exception e) {
            api.logging().logToError("[BinTab] Error getting persisted interactions for bin " + bin.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return interactions;
    }
    
    /**
     * Show Mark All Read button
     */
    private void showMarkAllReadButton() {
        markAllReadButton.setVisible(true);
        controlsPanel.revalidate();
        controlsPanel.repaint();
    }
    
    /**
     * Hide Mark All Read button
     */
    private void hideMarkAllReadButton() {
        markAllReadButton.setVisible(false);
        controlsPanel.revalidate();
        controlsPanel.repaint();
    }
    
    /**
     * Mark all interactions as read
     */
    private void markAllInteractionsAsRead() {
        synchronized (log) {
            int updatedCount = 0;
            for (int i = 0; i < log.size(); i++) {
                InteractshEntry entry = log.get(i);
                if (!entry.isRead()) {
                    entry.setRead(true);
                    updatedCount++;
                    
                    // Update storage in background
                    String entryKey = entry.uid + "_" + entry.timestamp.toString();
                    pendingViewedUpdates.add(entryKey);
                }
            }
            
            unreadCount = 0;
            
            if (updatedCount > 0) {
                // Update table display
                SwingUtilities.invokeLater(() -> {
                    logTableModel.fireTableDataChanged();
                });
                
                // Update storage in background
                new Thread(() -> {
                    updateViewedStatusInStorageThrottled();
                }).start();
                
                ToastNotification.showToast(this, "✓ Marked " + updatedCount + " interactions as read", ToastNotification.MessageType.SUCCESS);
                
                // Switch back to All filter after marking all read
                SwingUtilities.invokeLater(() -> {
                    unreadFilterButton.setSelected(false);
                    tableSorter.setRowFilter(null);
                    hideMarkAllReadButton();
                });
            }
        }
    }
    
    /**
     * Custom RowFilter for unread interactions
     */
    private class UnreadRowFilter extends RowFilter<TableModel, Integer> {
        @Override
        public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
            int modelRow = entry.getIdentifier();
            synchronized (log) {
                if (modelRow < log.size()) {
                    return !log.get(modelRow).isRead();
                }
            }
            return false;
        }
    }
    
    /**
     * Mark an interaction entry as viewed and update storage asynchronously
     */
    private void markEntryAsViewedAsync(InteractshEntry entry, int modelRow) {
        // Only update if not already viewed to avoid unnecessary operations
        if (!entry.isRead()) {
            // Update in memory immediately for UI responsiveness
            entry.setRead(true);
            synchronized (log) {
                if (unreadCount > 0) {
                    unreadCount--;
                }
            }
            
            // Update table display
            SwingUtilities.invokeLater(() -> {
                logTableModel.fireTableRowsUpdated(modelRow, modelRow);
            });
            
            // Update storage in background thread with throttling for performance
            String entryKey = entry.uid + "_" + entry.timestamp.toString();
            pendingViewedUpdates.add(entryKey);
            
            new Thread(() -> {
                updateViewedStatusInStorageThrottled();
            }).start();
        }
    }
    
    /**
     * Update the viewed status in storage file with throttling (optimized for performance)
     */
    private void updateViewedStatusInStorageThrottled() {
        // Throttle storage updates to avoid excessive I/O
        long now = System.currentTimeMillis();
        if (now - lastStorageUpdate < STORAGE_UPDATE_THROTTLE_MS) {
            try {
                Thread.sleep(STORAGE_UPDATE_THROTTLE_MS - (now - lastStorageUpdate));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        
        // Get all pending updates and clear the set
        java.util.Set<String> updates = new java.util.HashSet<>(pendingViewedUpdates);
        pendingViewedUpdates.clear();
        lastStorageUpdate = System.currentTimeMillis();
        
        if (updates.isEmpty()) {
            return;
        }
        
        updateViewedStatusInStorage(updates);
    }

    /**
     * Update the viewed status in storage file for multiple entries (batch operation)
     */
    private void updateViewedStatusInStorage(java.util.Set<String> entryKeys) {
        try {
            if (bin.getUniqueId() == null || bin.getUniqueId().isEmpty()) {
                return;
            }
            
            String userHome = System.getProperty("user.home");
            java.io.File storageDir = new java.io.File(userHome, ".requestbin-collaborator");
            java.io.File dataFile = new java.io.File(storageDir, "interactions-" + bin.getUniqueId() + ".json");
            
            if (!dataFile.exists()) {
                return;
            }
            
            // Read current data
            String existingData = new String(java.nio.file.Files.readAllBytes(dataFile.toPath()));
            org.json.JSONArray storedInteractions = new org.json.JSONArray(existingData);
            
            // Find and update interactions that match the pending keys
            int updateCount = 0;
            for (int i = 0; i < storedInteractions.length(); i++) {
                org.json.JSONObject interaction = storedInteractions.getJSONObject(i);
                
                // Match by unique-id and timestamp for precision
                String storedUid = interaction.optString("unique-id", "");
                String storedTimestamp = interaction.optString("timestamp", "");
                String entryKey = storedUid + "_" + storedTimestamp;
                
                if (entryKeys.contains(entryKey)) {
                    interaction.put("isViewed", true);
                    updateCount++;
                    api.logging().logToOutput("[BinTab] Updated viewed status for interaction: " + storedUid);
                }
            }
            
            // Write back to file only if something was updated
            if (updateCount > 0) {
                java.nio.file.Files.write(dataFile.toPath(), 
                           storedInteractions.toString(2).getBytes(), 
                           java.nio.file.StandardOpenOption.CREATE, 
                           java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                           
                api.logging().logToOutput("[BinTab] Batch updated " + updateCount + " interaction viewed statuses in storage");
            }
            
        } catch (Exception e) {
            api.logging().logToError("[BinTab] Error updating viewed status in storage: " + e.getMessage());
        }
    }
    
    /**
     * Parse InteractshEntry from stored JSON format
     */
    private InteractshEntry parseInteractionFromStorage(org.json.JSONObject stored) {
        try {
            // Convert stored format back to the format expected by InteractshEntry constructor
            org.json.JSONObject reconstructed = new org.json.JSONObject();
            
            // Map fields from storage format to InteractshEntry format
            reconstructed.put("protocol", stored.optString("protocol", "http"));
            reconstructed.put("unique-id", stored.optString("unique-id", ""));
            reconstructed.put("full-id", stored.optString("full-id", ""));
            reconstructed.put("remote-address", stored.optString("remote-address", "unknown"));
            reconstructed.put("raw-request", stored.optString("raw-request", ""));
            reconstructed.put("raw-response", stored.optString("raw-response", ""));
            
            // Handle timestamp - it's already stored as ISO string format
            String timestampStr = stored.optString("timestamp", "");
            if (!timestampStr.isEmpty()) {
                try {
                    // Try to parse as ISO format first (most likely case)
                    java.time.Instant.parse(timestampStr);
                    reconstructed.put("timestamp", timestampStr);
                } catch (Exception e1) {
                    try {
                        // Fallback: try parsing as long (milliseconds)
                        long timestamp = Long.parseLong(timestampStr);
                        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
                        reconstructed.put("timestamp", instant.toString());
                    } catch (NumberFormatException e2) {
                        api.logging().logToOutput("[BinTab] Invalid timestamp format: " + timestampStr + ", using current time");
                        reconstructed.put("timestamp", java.time.Instant.now().toString());
                    }
                }
            } else {
                reconstructed.put("timestamp", java.time.Instant.now().toString());
            }
            
            // Create InteractshEntry using JSON constructor
            InteractshEntry entry = new InteractshEntry(reconstructed.toString());
            
            // Set read status (this is not part of original format)
            boolean isViewed = stored.optBoolean("isViewed", false);
            entry.setRead(isViewed);
            
            return entry;
            
        } catch (Exception e) {
            api.logging().logToError("[BinTab] Error parsing stored interaction: " + e.getMessage());
            return null;
        }
    }

    private void updateUrlDisplay() {
        urlLabel.setText("URL: " + bin.getShortUrl());
        urlLabel.setToolTipText(bin.getUrl());
    }

    private void copyUrlToClipboard() {
        StringSelection stringSelection = new StringSelection(bin.getUrl());
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
            ToastNotification.showToast(this, "✓ URL copied to clipboard", MessageType.SUCCESS);
        } catch (Exception ex) {
            api.logging().logToError("Clipboard issue: " + ex.getMessage());
            ToastNotification.showToast(this, "❌ Failed to copy URL", MessageType.ERROR);
        }
    }

    /**
     * Set PollingService for manual refresh functionality
     */
    public void setPollingService(burp.services.PollingService pollingService) {
        this.pollingService = pollingService;
    }
    
    private void refreshSession() {
        // Manual refresh: reload persisted interactions and poll for new ones
        ToastNotification.showToast(this, "🔄 Refreshing interactions...", MessageType.INFO);
        
        // Execute both operations in background
        new Thread(() -> {
            try {
                // First, reload persisted interactions from storage
                reloadPersistedInteractions();
                
                // Then, trigger manual poll to get latest interactions from server
                if (pollingService != null && bin.getCorrelationId() != null) {
                    api.logging().logToOutput("[BinTab] Triggering manual poll for bin: " + bin.getName() + " (correlation: " + bin.getCorrelationId() + ")");
                    pollingService.manualPoll(bin.getCorrelationId());
                    
                    SwingUtilities.invokeLater(() -> {
                        ToastNotification.showToast(this, "✓ Interactions refreshed & polled from server", MessageType.SUCCESS);
                    });
                } else {
                    api.logging().logToOutput("[BinTab] Manual poll skipped - pollingService: " + (pollingService != null) + ", correlationId: " + bin.getCorrelationId());
                    SwingUtilities.invokeLater(() -> {
                        ToastNotification.showToast(this, "✓ Local interactions refreshed (polling not available)", MessageType.SUCCESS);
                    });
                }
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    api.logging().logToError("Manual refresh failed: " + e.getMessage());
                    ToastNotification.showToast(this, "❌ Refresh failed: " + e.getMessage(), MessageType.ERROR);
                });
            }
        }).start();
    }

    private void clearLog() {
        // Ask for confirmation
        int option = JOptionPane.showConfirmDialog(
            this,
            "This will clear all interactions and delete the persistence file for bin '" + bin.getName() + "'.\nAre you sure?",
            "Clear All Interactions",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (option == JOptionPane.YES_OPTION) {
            synchronized (log) {
                log.clear();
                logTableModel.fireTableDataChanged();
                requestViewer.setRequest(null);
                responseViewer.setResponse(null);
                genericDetailsViewer.setText("");
                unreadCount = 0;
            }
            
            // Update empty state (switch to empty view)
            updateEmptyState();
            
            // Delete persistence file
            deletePersistenceFile();
            
            ToastNotification.showToast(this, "✓ Log cleared and persistence file deleted", MessageType.SUCCESS);
        }
    }
    
    /**
     * Delete the persistence file for this bin
     */
    private void deletePersistenceFile() {
        try {
            if (bin.getUniqueId() == null || bin.getUniqueId().isEmpty()) {
                api.logging().logToError("Cannot delete persistence file: bin has no unique ID");
                return;
            }
            
            // Use same path as updateViewedStatusInStorage method
            String userHome = System.getProperty("user.home");
            File storageDir = new File(userHome, ".requestbin-collaborator");
            File persistenceFile = new File(storageDir, "interactions-" + bin.getUniqueId() + ".json");
            
            if (persistenceFile.exists()) {
                boolean deleted = persistenceFile.delete();
                if (deleted) {
                    api.logging().logToOutput("Deleted persistence file: " + persistenceFile.getAbsolutePath());
                } else {
                    api.logging().logToError("Failed to delete persistence file: " + persistenceFile.getAbsolutePath());
                }
            } else {
                api.logging().logToOutput("Persistence file does not exist: " + persistenceFile.getAbsolutePath());
            }
        } catch (Exception e) {
            api.logging().logToError("Error deleting persistence file: " + e.getMessage());
        }
    }
    
    /**
     * Update the display between empty guide and table view
     */
    private void updateEmptyState() {
        SwingUtilities.invokeLater(() -> {
            synchronized (log) {
                if (log.isEmpty()) {
                    tableContainerLayout.show(tableContainerPanel, "EMPTY_VIEW");
                } else {
                    tableContainerLayout.show(tableContainerPanel, "TABLE_VIEW");
                }
            }
        });
    }

    public void addToTable(InteractshEntry entry) {
        SwingUtilities.invokeLater(() -> {
            synchronized (log) {
                log.add(entry);
                logTableModel.fireTableRowsInserted(log.size() - 1, log.size() - 1);
                unreadCount++;
                
                // Update bin interaction count
                bin.incrementInteractionCount();
                
                // Update empty state (switch to table view)
                updateEmptyState();
                
                // Notify parent about update
                firePropertyChange("interactionAdded", null, entry);
            }
        });
    }

    private void showEntryDetails(InteractshEntry entry) {
        if (entry.protocol.equals("http") || entry.protocol.equals("https")) {
            // Show HTTP details in viewers
            if (entry.rawRequest != null) {
                requestViewer.setRequest(burp.api.montoya.http.message.requests.HttpRequest.httpRequest(entry.rawRequest));
            }
            if (entry.rawResponse != null) {
                responseViewer.setResponse(burp.api.montoya.http.message.responses.HttpResponse.httpResponse(entry.rawResponse));
            }
            resultsLayout.show(resultsCardPanel, "HTTP_VIEW");
        } else {
            // Show generic details
            StringBuilder details = new StringBuilder();
            details.append("Protocol: ").append(entry.protocol.toUpperCase()).append("\n");
            details.append("Unique ID: ").append(entry.uid).append("\n");
            details.append("Remote Address: ").append(entry.address).append("\n");
            details.append("Timestamp: ").append(entry.timestamp.toString()).append("\n\n");
            
            if (entry.rawRequest != null) {
                details.append("Raw Request:\n").append(entry.rawRequest).append("\n\n");
            }
            if (entry.rawResponse != null) {
                details.append("Raw Response:\n").append(entry.rawResponse);
            }
            
            genericDetailsViewer.setText(details.toString());
            genericDetailsViewer.setCaretPosition(0);
            resultsLayout.show(resultsCardPanel, "GENERIC_VIEW");
        }
        
        // Note: Marking as read is now handled in the selection listener
    }

    public RequestBin getBin() {
        return bin;
    }

    public int getUnreadCount() {
        return unreadCount;
    }


    


    /**
     * Table model for interaction logs
     */
    private class LogTableModel extends AbstractTableModel {
        private final String[] columnNames = {"ID", "Protocol", "Date & Time", "Source Address", "UID"};

        @Override
        public int getRowCount() {
            synchronized (log) {
                return log.size();
            }
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            synchronized (log) {
                if (row >= log.size()) return "";
                
                InteractshEntry entry = log.get(row);
                switch (column) {
                    case 0: return log.size() - row; // Hiển thị ID từ cao xuống thấp (mới nhất ở trên)
                    case 1: return entry.protocol.toUpperCase();
                    case 2: return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .format(entry.timestamp.atZone(ZoneId.systemDefault()).toLocalDateTime());
                    case 3: return entry.address;
                    case 4: return entry.uid != null ? entry.uid.substring(0, Math.min(8, entry.uid.length())) : "";
                    default: return "";
                }
            }
        }

        @Override
        public Class<?> getColumnClass(int column) {
            switch (column) {
                case 0: return Integer.class;
                default: return String.class;
            }
        }
    }

    /**
     * Custom cell renderer for log table
     */
    private class LogTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            // Convert table row to model row to handle filtering correctly
            int modelRow = table.convertRowIndexToModel(row);
            
            synchronized (log) {
                if (modelRow >= 0 && modelRow < log.size()) {
                    InteractshEntry entry = log.get(modelRow);
                    boolean isUnread = !entry.isRead();
                    
                    // Set font based on read status - bold for unread items
                    if (isUnread) {
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else {
                        setFont(getFont().deriveFont(Font.PLAIN));
                    }
                    
                    // Set background and foreground colors
                    if (isSelected) {
                        // Selected state - always blue background with white text
                        setBackground(new Color(51, 153, 255));
                        setForeground(Color.WHITE);
                        // Keep font weight as determined by read status above
                    } else {
                        // Unselected state
                        if (isUnread) {
                            setBackground(new Color(255, 255, 230)); // Light yellow for unread
                        } else {
                            setBackground(Color.WHITE);
                        }
                        
                        // Protocol color coding for unselected items
                        if (column == 1) { // Protocol column
                            switch (entry.protocol.toLowerCase()) {
                                case "http":
                                case "https":
                                    setForeground(new Color(34, 139, 34)); // Green
                                    break;
                                case "dns":
                                    setForeground(Color.BLUE);
                                    break;
                                case "smtp":
                                    setForeground(new Color(255, 140, 0)); // Orange
                                    break;
                                default:
                                    setForeground(Color.BLACK);
                            }
                        } else {
                            setForeground(Color.BLACK);
                        }
                    }
                }
            }
            
            return this;
        }
    }

    /**
     * Mark all interactions as read
     */
    public void markAllAsRead() {
        synchronized (log) {
            for (InteractshEntry entry : log) {
                entry.setRead(true);
            }
            unreadCount = 0;
        }
        SwingUtilities.invokeLater(() -> {
            logTableModel.fireTableDataChanged();
            firePropertyChange("interactionRead", null, null);
        });
    }

    /**
     * Add a new interaction to this bin
     */
    public void addInteraction(Object interaction) {
        if (interaction instanceof InteractshEntry) {
            InteractshEntry entry = (InteractshEntry) interaction;
            
            synchronized (log) {
                log.add(0, entry); // Add at beginning for newest first
                if (!entry.isRead()) {
                    unreadCount++;
                }
                // Update empty state (switch to table view)
                updateEmptyState();
            }
            
            SwingUtilities.invokeLater(() -> {
                logTableModel.fireTableRowsInserted(0, 0);
                firePropertyChange("interactionAdded", null, entry);
            });
        }
    }
}