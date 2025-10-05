package burp.gui;

import burp.api.montoya.MontoyaApi;
import burp.models.RequestBin;
import burp.services.BinManager;
import burp.gui.ToastNotification.MessageType;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel for managing RequestBin instances
 * Based on bins page from requestbin.saas
 */
public class BinManagerPanel extends JPanel {
    private final MontoyaApi api;
    private final BinManager binManager;
    
    private JTable binsTable;
    private BinsTableModel tableModel;
    private JButton createBinButton;
    private JButton deleteBinButton;
    private JButton switchBinButton;
    private JButton refreshButton;
    private JLabel statusLabel;
    private JPanel emptyStatePanel;
    private JScrollPane tableScrollPane;
    
    private RequestBin selectedBin;
    private boolean isRefreshing = false;

    public BinManagerPanel(MontoyaApi api, BinManager binManager) {
        this.api = api;
        this.binManager = binManager;
        
        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        refreshBinsList();
    }

    private void initializeComponents() {
        tableModel = new BinsTableModel();
        binsTable = new JTable(tableModel);
        setupTable();
        
        createBinButton = new JButton("Create New Bin");
        createBinButton.setPreferredSize(new Dimension(130, 30));
        createBinButton.setBackground(new Color(34, 139, 34));
        createBinButton.setForeground(Color.WHITE);
        createBinButton.setOpaque(true);
        
        deleteBinButton = new JButton("Delete");
        deleteBinButton.setEnabled(false);
        deleteBinButton.setPreferredSize(new Dimension(80, 30));
        
        switchBinButton = new JButton("Switch To");
        switchBinButton.setEnabled(false);
        switchBinButton.setPreferredSize(new Dimension(90, 30));
        switchBinButton.setBackground(new Color(70, 130, 180));
        switchBinButton.setForeground(Color.WHITE);
        switchBinButton.setOpaque(true);
        
        refreshButton = new JButton("↻ Refresh");
        refreshButton.setPreferredSize(new Dimension(90, 30));
        
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));
        
        tableScrollPane = new JScrollPane(binsTable);
        tableScrollPane.setPreferredSize(new Dimension(600, 300));
        
        // Empty state panel
        emptyStatePanel = createEmptyStatePanel();
    }

    private void setupTable() {
        binsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        binsTable.setRowHeight(25);
        binsTable.getTableHeader().setReorderingAllowed(false);
        
        // Custom renderer for different columns
        binsTable.setDefaultRenderer(String.class, new BinTableCellRenderer());
        binsTable.setDefaultRenderer(Boolean.class, new BinTableCellRenderer());
        
        // Column widths
        if (binsTable.getColumnCount() > 0) {
            TableColumn nameColumn = binsTable.getColumnModel().getColumn(0);
            nameColumn.setPreferredWidth(150);
            
            TableColumn typeColumn = binsTable.getColumnModel().getColumn(1);
            typeColumn.setPreferredWidth(60);
            typeColumn.setMaxWidth(80);
            
            TableColumn statusColumn = binsTable.getColumnModel().getColumn(2);
            statusColumn.setPreferredWidth(60);
            statusColumn.setMaxWidth(80);
            
            TableColumn urlColumn = binsTable.getColumnModel().getColumn(3);
            urlColumn.setPreferredWidth(200);
            
            TableColumn interactionsColumn = binsTable.getColumnModel().getColumn(4);
            interactionsColumn.setPreferredWidth(80);
            interactionsColumn.setMaxWidth(100);
            
            TableColumn createdColumn = binsTable.getColumnModel().getColumn(5);
            createdColumn.setPreferredWidth(120);
        }
    }

    private JPanel createEmptyStatePanel() {
        JPanel emptyPanel = new JPanel(new BorderLayout());
        emptyPanel.setBackground(Color.WHITE);
        
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50));
        
        // Icon
        JLabel iconLabel = new JLabel("📦", JLabel.CENTER);
        iconLabel.setFont(iconLabel.getFont().deriveFont(72f));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(iconLabel);
        
        centerPanel.add(Box.createVerticalStrut(20));
        
        // Title
        JLabel titleLabel = new JLabel("No RequestBins Yet", JLabel.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(titleLabel);
        
        centerPanel.add(Box.createVerticalStrut(10));
        
        // Description
        JLabel descLabel = new JLabel("<html><center>Create your first RequestBin to start capturing<br>and analyzing HTTP requests in real-time</center></html>");
        descLabel.setForeground(Color.GRAY);
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(descLabel);
        
        centerPanel.add(Box.createVerticalStrut(30));
        
        // Create button
        JButton createFirstBinButton = new JButton("Create Your First Bin");
        createFirstBinButton.setPreferredSize(new Dimension(180, 35));
        createFirstBinButton.setBackground(new Color(34, 139, 34));
        createFirstBinButton.setForeground(Color.WHITE);
        createFirstBinButton.setOpaque(true);
        createFirstBinButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        createFirstBinButton.addActionListener(e -> showCreateBinDialog());
        centerPanel.add(createFirstBinButton);
        
        emptyPanel.add(centerPanel, BorderLayout.CENTER);
        return emptyPanel;
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());
        
        // Header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        JLabel titleLabel = new JLabel("RequestBin Manager");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        
        JPanel headerButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        headerButtonsPanel.add(refreshButton);
        headerButtonsPanel.add(createBinButton);
        headerPanel.add(headerButtonsPanel, BorderLayout.EAST);
        
        add(headerPanel, BorderLayout.NORTH);
        
        // Content panel (will switch between empty state and table)
        JPanel contentPanel = new JPanel(new CardLayout());
        contentPanel.add(emptyStatePanel, "empty");
        contentPanel.add(tableScrollPane, "table");
        add(contentPanel, BorderLayout.CENTER);
        
        // Bottom panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        actionPanel.add(switchBinButton);
        actionPanel.add(deleteBinButton);
        bottomPanel.add(actionPanel, BorderLayout.WEST);
        
        bottomPanel.add(statusLabel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        // Table selection
        binsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = binsTable.getSelectedRow();
                if (selectedRow >= 0) {
                    selectedBin = tableModel.getBinAt(selectedRow);
                    deleteBinButton.setEnabled(true);
                    switchBinButton.setEnabled(true);
                } else {
                    selectedBin = null;
                    deleteBinButton.setEnabled(false);
                    switchBinButton.setEnabled(false);
                }
            }
        });
        
        // Double-click to switch
        binsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = binsTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        RequestBin bin = tableModel.getBinAt(row);
                        switchToBin(bin);
                    }
                }
            }
        });
        
        // Button handlers
        createBinButton.addActionListener(e -> showCreateBinDialog());
        deleteBinButton.addActionListener(e -> deleteSelectedBin());
        switchBinButton.addActionListener(e -> switchToSelectedBin());
        refreshButton.addActionListener(e -> refreshBinsList());
    }

    private void showCreateBinDialog() {
        CreateBinDialog dialog = new CreateBinDialog(this, api, binManager);
        dialog.setVisible(true);
        
        RequestBin createdBin = dialog.getCreatedBin();
        if (createdBin != null) {
            refreshBinsList();
            // Select the new bin
            int rowIndex = tableModel.findBinIndex(createdBin);
            if (rowIndex >= 0) {
                binsTable.setRowSelectionInterval(rowIndex, rowIndex);
            }
        }
    }

    private void deleteSelectedBin() {
        if (selectedBin == null) return;
        
        int result = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to delete bin \"" + selectedBin.getName() + "\"?\n" +
            "This action cannot be undone and will remove all associated data.",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            boolean success = binManager.deleteBin(selectedBin.getUniqueId());
            if (success) {
                ToastNotification.showToast(this, "Bin deleted successfully", MessageType.SUCCESS);
                refreshBinsList();
            } else {
                ToastNotification.showToast(this, "Failed to delete bin", MessageType.ERROR);
            }
        }
    }

    private void switchToSelectedBin() {
        if (selectedBin != null) {
            switchToBin(selectedBin);
        }
    }

    private void switchToBin(RequestBin bin) {
        binManager.switchToBin(bin.getUniqueId());
        ToastNotification.showToast(this, "Switched to bin: " + bin.getName(), MessageType.SUCCESS);
        refreshBinsList(); // Refresh to update active status
    }

    public void refreshBinsList() {
        if (isRefreshing) return;
        
        isRefreshing = true;
        refreshButton.setEnabled(false);
        statusLabel.setText("Refreshing...");
        
        SwingUtilities.invokeLater(() -> {
            try {
                List<RequestBin> bins = binManager.getAllBins();
                tableModel.setBins(bins);
                
                // Switch between empty state and table
                CardLayout cardLayout = (CardLayout) ((JPanel) getComponent(1)).getLayout();
                if (bins.isEmpty()) {
                    cardLayout.show((JPanel) getComponent(1), "empty");
                    statusLabel.setText("No bins available");
                } else {
                    cardLayout.show((JPanel) getComponent(1), "table");
                    
                    // Highlight active bin
                    RequestBin activeBin = binManager.getActiveBin();
                    if (activeBin != null) {
                        int activeIndex = tableModel.findBinIndex(activeBin);
                        if (activeIndex >= 0) {
                            binsTable.setRowSelectionInterval(activeIndex, activeIndex);
                        }
                    }
                    
                    statusLabel.setText(bins.size() + " bin(s) loaded");
                }
                
            } finally {
                isRefreshing = false;
                refreshButton.setEnabled(true);
            }
        });
    }

    /**
     * Table model for bins list
     */
    private class BinsTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Name", "Type", "Status", "URL", "Requests", "Created"};
        private List<RequestBin> bins = new ArrayList<>();

        public void setBins(List<RequestBin> bins) {
            this.bins = new ArrayList<>(bins);
            fireTableDataChanged();
        }

        public RequestBin getBinAt(int row) {
            return bins.get(row);
        }

        public int findBinIndex(RequestBin targetBin) {
            for (int i = 0; i < bins.size(); i++) {
                if (bins.get(i).equals(targetBin)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int getRowCount() {
            return bins.size();
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
            RequestBin bin = bins.get(row);
            switch (column) {
                case 0: return bin.getDisplayName();
                case 1: return bin.getType();
                case 2: return bin.getStatus();
                case 3: return bin.getShortUrl();
                case 4: return bin.getInteractionCount();
                case 5: return DateTimeFormatter.ofPattern("MM/dd HH:mm").format(
                        bin.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
                default: return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int column) {
            switch (column) {
                case 4: return Integer.class;
                default: return String.class;
            }
        }
    }

    /**
     * Custom cell renderer for bins table
     */
    private class BinTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            RequestBin bin = tableModel.getBinAt(row);
            RequestBin activeBin = binManager.getActiveBin();
            
            // Highlight active bin
            if (!isSelected && activeBin != null && bin.equals(activeBin)) {
                setBackground(new Color(240, 248, 255)); // Light blue background
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (!isSelected) {
                setBackground(Color.WHITE);
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            
            // Color coding for different columns
            if (!isSelected) {
                switch (column) {
                    case 1: // Type
                        setForeground(new Color(34, 139, 34)); // Green for all server-based bins
                        break;
                    case 2: // Status
                        setForeground(bin.isActive() ? new Color(34, 139, 34) : Color.RED);
                        break;
                    case 4: // Interaction count
                        if (bin.getInteractionCount() > 0) {
                            setForeground(new Color(34, 139, 34));
                            setFont(getFont().deriveFont(Font.BOLD));
                        } else {
                            setForeground(Color.GRAY);
                        }
                        break;
                    default:
                        setForeground(Color.BLACK);
                        break;
                }
            }
            
            return this;
        }
    }
}