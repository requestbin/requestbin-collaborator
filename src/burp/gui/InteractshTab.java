package burp.gui;

import burp.models.RequestBin;
import burp.services.BinManager;
import burp.listeners.InteractshListener;
import interactsh.InteractshEntry;

import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import java.awt.event.ActionEvent;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Main extension tab implementing tab-per-bin approach
 * Each RequestBin gets its own tab with a logs interface
 */
public class InteractshTab extends JPanel {

	// Core services and API
	private final MontoyaApi api;
	private BinManager binManager; // Non-final to be set later
	private final InteractshListener listener;

	// Main UI component
	private JTabbedPane mainPane;

	public InteractshTab(MontoyaApi api) {
		this.api = api;
		this.binManager = null; // Will be set later after BinManager is initialized
		this.listener = null; // no need initialize listener here
		
		// Initialize listener for interaction monitoring  
		// this.listener = new InteractshListener(
		// 	(url) -> SwingUtilities.invokeLater(() -> {
		// 		// Handle ready callback - could update UI
		// 	}),
		// 	(error) -> SwingUtilities.invokeLater(() -> {
		// 		// Handle failure callback - could show error
		// 	})
		// );

		initializeUI();
		setupKeyboardShortcuts();
		// Don't load bins immediately - will be called later
	}

	/**
	 * Setup keyboard shortcuts
	 */
	private void setupKeyboardShortcuts() {
		// Setup Ctrl+W to close current tab
		KeyStroke closeTabKeyStroke = KeyStroke.getKeyStroke("ctrl W");
		getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(closeTabKeyStroke, "closeTab");
		getActionMap().put("closeTab", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				closeCurrentTab();
			}
		});
	}
	
	/**
	 * Close the currently selected tab
	 */
	private void closeCurrentTab() {
		int selectedIndex = mainPane.getSelectedIndex();
		if (selectedIndex >= 0 && selectedIndex < mainPane.getTabCount()) {
			Component selectedComponent = mainPane.getComponentAt(selectedIndex);
			
			// Don't close the "+" tab
			if (selectedComponent instanceof BinTab) {
				BinTab binTab = (BinTab) selectedComponent;
				closeBinTab(binTab.getBin());
			}
		}
	}
	
	/**
	 * Initialize the main UI components
	 */
	private void initializeUI() {
		setLayout(new BorderLayout());

		// Create main tabbed pane
		mainPane = new JTabbedPane();
		mainPane.setTabPlacement(JTabbedPane.TOP);
		
		// Add tab change listener to handle selection
		mainPane.addChangeListener(e -> handleTabSelection());

		add(mainPane, BorderLayout.CENTER);
	}

	/**
	 * Set BinManager and initialize bins (called after BinManager is ready)
	 */
	public void setBinManager(BinManager binManager) {
		this.binManager = binManager;
		loadBins();
	}

	/**
	 * Load existing bins or show welcome screen
	 */
	private void loadBins() {
		if (binManager != null && binManager.hasBins()) {
			refreshBinTabs();
		} else {
			showWelcomeTab();
		}
		
		// Always add the "+" tab for creating new bins
		addCreateBinTab();
	}

	/**
	 * Show welcome tab for first-time users
	 */
	private void showWelcomeTab() {
		JPanel welcomePanel = createWelcomePanel();
		mainPane.addTab("Welcome", welcomePanel);
	}

	/**
	 * Create welcome panel content
	 */
	private JPanel createWelcomePanel() {
		JPanel welcomePanel = new JPanel(new BorderLayout());
		welcomePanel.setBackground(Color.WHITE);

		// Create center panel with welcome content
		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		centerPanel.setBackground(Color.WHITE);
		// Add padding using empty border
		centerPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(60, 50, 60, 50));

		// Logo + Title section
		JPanel logoTitlePanel = new JPanel();
		logoTitlePanel.setLayout(new BoxLayout(logoTitlePanel, BoxLayout.Y_AXIS));
		logoTitlePanel.setBackground(Color.WHITE);
		logoTitlePanel.setAlignmentX(Component.CENTER_ALIGNMENT);

		// RequestBin.net logo (using text representation)
		JLabel logoLabel = new JLabel("🗂️ RequestBin.net");
		logoLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
		logoLabel.setForeground(new Color(3, 102, 214)); // Blue color
		logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		logoLabel.setHorizontalAlignment(SwingConstants.CENTER);

		// Main title
		JLabel titleLabel = new JLabel("RequestBin Collaborator");
		titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
		titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		titleLabel.setForeground(new Color(64, 64, 64));

		logoTitlePanel.add(logoLabel);
		logoTitlePanel.add(Box.createVerticalStrut(8));
		logoTitlePanel.add(titleLabel);

		// Welcome message (multiple labels for proper formatting)
		JPanel messagePanel = new JPanel();
		messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
		messagePanel.setBackground(Color.WHITE);
		messagePanel.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel welcomeMsg1 = new JLabel("Welcome to RequestBin Collaborator!");
		welcomeMsg1.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
		welcomeMsg1.setAlignmentX(Component.CENTER_ALIGNMENT);
		welcomeMsg1.setHorizontalAlignment(SwingConstants.CENTER);
		welcomeMsg1.setForeground(new Color(64, 64, 64));

		JLabel welcomeMsg2 = new JLabel("This extension helps you manage multiple RequestBin endpoints for testing.");
		welcomeMsg2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		welcomeMsg2.setAlignmentX(Component.CENTER_ALIGNMENT);
		welcomeMsg2.setHorizontalAlignment(SwingConstants.CENTER);
		welcomeMsg2.setForeground(new Color(128, 128, 128));

		JLabel welcomeMsg3 = new JLabel("Each bin gets its own tab where you can monitor incoming requests.");
		welcomeMsg3.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		welcomeMsg3.setAlignmentX(Component.CENTER_ALIGNMENT);
		welcomeMsg3.setHorizontalAlignment(SwingConstants.CENTER);
		welcomeMsg3.setForeground(new Color(128, 128, 128));

		messagePanel.add(welcomeMsg1);
		messagePanel.add(Box.createVerticalStrut(8));
		messagePanel.add(welcomeMsg2);
		messagePanel.add(Box.createVerticalStrut(4));
		messagePanel.add(welcomeMsg3);

		// Development info panel
		JPanel devInfoPanel = new JPanel();
		devInfoPanel.setLayout(new BoxLayout(devInfoPanel, BoxLayout.Y_AXIS));
		devInfoPanel.setBackground(new Color(248, 249, 250)); // Light gray background
		devInfoPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(208, 215, 222), 1),
			BorderFactory.createEmptyBorder(15, 20, 15, 20)
		));
		devInfoPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel devTitle = new JLabel("🛠️ Open Source Project");
		devTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		devTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
		devTitle.setHorizontalAlignment(SwingConstants.CENTER);
		devTitle.setForeground(new Color(87, 96, 106));

		JLabel devText1 = new JLabel("Developed by RequestBin.net team");
		devText1.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		devText1.setAlignmentX(Component.CENTER_ALIGNMENT);
		devText1.setHorizontalAlignment(SwingConstants.CENTER);
		devText1.setForeground(new Color(87, 96, 106));

		JLabel devLink = new JLabel("Open source at github.com/requestbin/requestbin-collaborator");
		devLink.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		devLink.setAlignmentX(Component.CENTER_ALIGNMENT);
		devLink.setHorizontalAlignment(SwingConstants.CENTER);
		devLink.setForeground(new Color(3, 102, 214)); // Blue color
		devLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		// Click handler for GitHub link
		devLink.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				try {
					java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://github.com/requestbin/requestbin-collaborator"));
				} catch (Exception ex) {
					api.logging().logToOutput("Visit https://github.com/requestbin/requestbin-collaborator");
				}
			}
		});

		devInfoPanel.add(devTitle);
		devInfoPanel.add(Box.createVerticalStrut(8));
		devInfoPanel.add(devText1);
		devInfoPanel.add(Box.createVerticalStrut(4));
		devInfoPanel.add(devLink);

		// Promotional panel for RequestBin.net
		JPanel promoPanel = new JPanel();
		promoPanel.setLayout(new BoxLayout(promoPanel, BoxLayout.Y_AXIS));
		promoPanel.setBackground(new Color(240, 248, 255)); // Light blue background
		promoPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(173, 216, 230), 1),
			BorderFactory.createEmptyBorder(15, 20, 15, 20)
		));
		promoPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel promoTitle = new JLabel("🌟 Want Enhanced Features?");
		promoTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		promoTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
		promoTitle.setHorizontalAlignment(SwingConstants.CENTER);
		promoTitle.setForeground(new Color(51, 102, 153));

		JLabel promoText1 = new JLabel("Visit RequestBin.net for advanced features:");
		promoText1.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		promoText1.setAlignmentX(Component.CENTER_ALIGNMENT);
		promoText1.setHorizontalAlignment(SwingConstants.CENTER);
		promoText1.setForeground(new Color(51, 102, 153));

		JLabel promoFeature1 = new JLabel("• Cloud storage & request history");
		promoFeature1.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		promoFeature1.setAlignmentX(Component.CENTER_ALIGNMENT);
		promoFeature1.setHorizontalAlignment(SwingConstants.CENTER);
		promoFeature1.setForeground(new Color(51, 102, 153));

		JLabel promoFeature2 = new JLabel("• Advanced filtering & analytics");
		promoFeature2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		promoFeature2.setAlignmentX(Component.CENTER_ALIGNMENT);
		promoFeature2.setHorizontalAlignment(SwingConstants.CENTER);
		promoFeature2.setForeground(new Color(51, 102, 153));

		JLabel promoFeature3 = new JLabel("• Webhook forwarding & notifications");
		promoFeature3.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		promoFeature3.setAlignmentX(Component.CENTER_ALIGNMENT);
		promoFeature3.setHorizontalAlignment(SwingConstants.CENTER);
		promoFeature3.setForeground(new Color(51, 102, 153));

		JLabel promoLinkLabel = new JLabel("→ Visit RequestBin.net");
		promoLinkLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		promoLinkLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		promoLinkLabel.setHorizontalAlignment(SwingConstants.CENTER);
		promoLinkLabel.setForeground(new Color(3, 102, 214)); // Darker blue
		promoLinkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		// Click handler for RequestBin.net link
		promoLinkLabel.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				try {
					java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://requestbin.net?utm_source=burp_extension&utm_medium=welcome_screen"));
				} catch (Exception ex) {
					api.logging().logToOutput("Visit https://requestbin.net for enhanced features");
				}
			}
		});

		promoPanel.add(promoTitle);
		promoPanel.add(Box.createVerticalStrut(8));
		promoPanel.add(promoText1);
		promoPanel.add(Box.createVerticalStrut(6));
		promoPanel.add(promoFeature1);
		promoPanel.add(Box.createVerticalStrut(2));
		promoPanel.add(promoFeature2);
		promoPanel.add(Box.createVerticalStrut(2));
		promoPanel.add(promoFeature3);
		promoPanel.add(Box.createVerticalStrut(10));
		promoPanel.add(promoLinkLabel);

		// Create bin button
		JButton createBinButton = new JButton("Create Your First Bin");
		createBinButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		createBinButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		createBinButton.setPreferredSize(new Dimension(200, 35));
		createBinButton.setBackground(new Color(216, 102, 51));
		createBinButton.setForeground(Color.WHITE);
		createBinButton.setOpaque(true);
		createBinButton.setBorderPainted(false);
		createBinButton.addActionListener(e -> showCreateBinDialog());

		// Add all components with proper spacing
		centerPanel.add(logoTitlePanel);
		centerPanel.add(Box.createVerticalStrut(25));
		centerPanel.add(messagePanel);
		centerPanel.add(Box.createVerticalStrut(20));
		centerPanel.add(devInfoPanel);
		centerPanel.add(Box.createVerticalStrut(15));
		centerPanel.add(promoPanel);
		centerPanel.add(Box.createVerticalStrut(25));
		centerPanel.add(createBinButton);

		welcomePanel.add(centerPanel, BorderLayout.CENTER);
		return welcomePanel;
	}

	/**
	 * Refresh all bin tabs based on current bins
	 */
	private void refreshBinTabs() {
		// Remove all existing bin tabs (keep welcome and + tabs)
		for (int i = mainPane.getTabCount() - 1; i >= 0; i--) {
			Component component = mainPane.getComponentAt(i);
			if (component instanceof BinTab) {
				mainPane.removeTabAt(i);
			}
		}

		// Add tabs for all current bins
		if (binManager != null) {
			for (RequestBin bin : binManager.getAllBins()) {
				addBinTab(bin);
			}
			// show first bin tab
			if (binManager.getAllBins().size() > 0) {
				selectBinTab(binManager.getAllBins().get(0));
			}

			// Remove welcome tab if we have bins
			if (binManager.hasBins()) {
				removeWelcomeTab();
			}
		}
	}

	/**
	 * Remove welcome tab when bins exist
	 */
	private void removeWelcomeTab() {
		for (int i = 0; i < mainPane.getTabCount(); i++) {
			if ("Welcome".equals(mainPane.getTitleAt(i))) {
				mainPane.removeTabAt(i);
				break;
			}
		}
	}

	/**
	 * Add a new tab for a bin
	 */
	private void addBinTab(RequestBin bin) {
		BinTab binTab = new BinTab(api, bin);
		
		// Set PollingService for manual refresh functionality
		if (binManager != null) {
			binTab.setPollingService(binManager.getPollingService());
		}
		
		// Listen for interaction updates to update tab title
		binTab.addPropertyChangeListener("interactionAdded", evt -> {
			updateTabTitle(bin, binTab);
		});
		
		// Create tab with close button
		String tabTitle = bin.getName();
		
		// Insert before the "+" tab
		int insertIndex = Math.max(0, mainPane.getTabCount() - 1);
		mainPane.insertTab(tabTitle, null, binTab, "Bin: " + bin.getName(), insertIndex);
		
		// Add close button to tab header
		addCloseButtonToTab(insertIndex, bin);
		
		// Setup tab close functionality (middle-click)
		setupTabContextMenu(insertIndex, bin);
	}

	/**
	 * Add the "+" tab for creating new bins
	 */
	private void addCreateBinTab() {
		JLabel addLabel = new JLabel("+");
		addLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
		addLabel.setHorizontalAlignment(SwingConstants.CENTER);
		addLabel.setPreferredSize(new Dimension(30, 25));
		addLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addLabel.setToolTipText("Create new bin");
		
		// Create a panel to hold the label
		JPanel addPanel = new JPanel(new BorderLayout());
		addPanel.add(addLabel, BorderLayout.CENTER);
		addPanel.setOpaque(false);
		
		// Add click handler
		addLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				showCreateBinDialog();
			}
		});
		
		mainPane.addTab("", addPanel);
		mainPane.setTabComponentAt(mainPane.getTabCount() - 1, addLabel);
	}

	/**
	 * Select tab for specific bin
	 */
	private void selectBinTab(RequestBin bin) {
		for (int i = 0; i < mainPane.getTabCount(); i++) {
			Component component = mainPane.getComponentAt(i);
			if (component instanceof BinTab) {
				BinTab binTab = (BinTab) component;
				if (binTab.getBin().equals(bin)) {
					mainPane.setSelectedIndex(i);
					break;
				}
			}
		}
	}

	/**
	 * Update tab title with unread count
	 */
	private void updateTabTitle(RequestBin bin, BinTab binTab) {
		for (int i = 0; i < mainPane.getTabCount(); i++) {
			if (mainPane.getComponentAt(i) == binTab) {
				String title = bin.getName();
				int unreadCount = binTab.getUnreadCount();
				if (unreadCount > 0) {
					title += " (" + unreadCount + ")";
				}
				
				// Update title in custom tab component
				Component tabComponent = mainPane.getTabComponentAt(i);
				if (tabComponent instanceof JPanel) {
					JPanel tabPanel = (JPanel) tabComponent;
					// Find the title label and update it
					for (Component comp : tabPanel.getComponents()) {
						if (comp instanceof JLabel) {
							JLabel titleLabel = (JLabel) comp;
							titleLabel.setText(title);
							break;
						}
					}
				} else {
					// Fallback to standard title setting
					mainPane.setTitleAt(i, title);
				}
				break;
			}
		}
	}

	/**
	 * Add close button to tab header
	 */
	private void addCloseButtonToTab(int tabIndex, RequestBin bin) {
		// Create a panel to hold tab title and close button
		JPanel tabPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		tabPanel.setOpaque(false);
		
		// Add tab title label
		JLabel titleLabel = new JLabel(bin.getName());
		titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
		tabPanel.add(titleLabel);
		
		// Create close button
		JButton closeButton = new JButton("×");
		closeButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		closeButton.setMargin(new Insets(0, 3, 0, 3));
		closeButton.setPreferredSize(new Dimension(18, 18));
		closeButton.setBackground(Color.LIGHT_GRAY);
		closeButton.setForeground(Color.BLACK);
		closeButton.setBorder(BorderFactory.createRaisedBevelBorder());
		closeButton.setFocusable(false);
		closeButton.setToolTipText("Close bin");
		
		// Add hover effects
		closeButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				closeButton.setBackground(Color.RED);
				closeButton.setForeground(Color.WHITE);
			}
			
			@Override
			public void mouseExited(MouseEvent e) {
				closeButton.setBackground(Color.LIGHT_GRAY);
				closeButton.setForeground(Color.BLACK);
			}
		});
		
		// Add click action to close tab
		closeButton.addActionListener(e -> closeBinTab(bin));
		
		tabPanel.add(closeButton);
		
		// Set the custom component as tab header
		mainPane.setTabComponentAt(tabIndex, tabPanel);
	}
	
	/**
	 * Setup context menu for tabs (close, rename, etc.)
	 */
	private void setupTabContextMenu(int tabIndex, RequestBin bin) {
		// Add middle-click to close functionality
		mainPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isMiddleMouseButton(e)) {
					int clickedTab = mainPane.indexAtLocation(e.getX(), e.getY());
					if (clickedTab == tabIndex) {
						closeBinTab(bin);
					}
				}
			}
		});
	}

	/**
	 * Close a bin tab
	 */
	private void closeBinTab(RequestBin bin) {
		// Ask for confirmation
		int option = JOptionPane.showConfirmDialog(
			this,
			"Are you sure you want to remove bin '" + bin.getName() + "'?",
			"Confirm Bin Removal",
			JOptionPane.YES_NO_OPTION
		);
		
		if (option == JOptionPane.YES_OPTION && binManager != null) {
			binManager.deleteBin(bin.getBinId());
			refreshBinTabs();
			
			// If no bins left, show welcome tab
			if (!binManager.hasBins()) {
				showWelcomeTab();
			}
		}
	}

	/**
	 * Show create bin dialog
	 */
	private void showCreateBinDialog() {
		if (binManager == null) return;
		CreateBinDialog dialog = new CreateBinDialog(this, api, binManager);
		dialog.setVisible(true);
		
		RequestBin createdBin = dialog.getCreatedBin();
		if (createdBin != null) {
			// Refresh all tabs
			refreshBinTabs();
			// Select the new bin tab
			selectBinTab(createdBin);
		}
	}

	/**
	 * Handle tab selection changes
	 */
	private void handleTabSelection() {
		int selectedIndex = mainPane.getSelectedIndex();
		if (selectedIndex >= 0) {
			Component selectedComponent = mainPane.getComponentAt(selectedIndex);
			
			// If it's a bin tab, reload persisted interactions to ensure fresh read/unread status
			if (selectedComponent instanceof BinTab) {
				BinTab binTab = (BinTab) selectedComponent;
				binTab.reloadPersistedInteractions();
				updateTabTitle(binTab.getBin(), binTab);
			}
		}
	}

	/**
	 * Called by InteractshListener when new interactions are detected
	 */
	public void onNewInteraction(String binId, Object interaction) {
		SwingUtilities.invokeLater(() -> {
			// Find the corresponding bin tab and update it
			for (int i = 0; i < mainPane.getTabCount(); i++) {
				Component component = mainPane.getComponentAt(i);
				if (component instanceof BinTab) {
					BinTab binTab = (BinTab) component;
					if (binTab.getBin().getBinId().equals(binId)) {
						binTab.addInteraction(interaction);
						
						// Update tab title if not currently selected
						if (mainPane.getSelectedIndex() != i) {
							updateTabTitle(binTab.getBin(), binTab);
						}
						break;
					}
				}
			}
		});
	}

	/**
	 * Get the listener for external access
	 */
	public InteractshListener getListener() {
		return listener;
	}

	/**
	 * Cleanup method for compatibility with old code
	 */
	public void cleanup() {
		if (listener != null) {
			// Cleanup listener if needed
		}
	}

	/**
	 * Get poll field for compatibility (returns null as we don't use global polling)
	 */
	public JTextField getPollField() {
		return null;
	}

	/**
	 * Add to table method with specific bin - adds to active tab only if target bin matches
	 */
	public void addToTable(Object entry, RequestBin targetBin) {
		if (entry instanceof InteractshEntry && targetBin != null) {
			InteractshEntry interactEntry = (InteractshEntry) entry;
			
			// Get the currently active tab
			int activeTabIndex = mainPane.getSelectedIndex();
			if (activeTabIndex >= 0 && activeTabIndex < mainPane.getTabCount()) {
				Component activeTab = mainPane.getComponentAt(activeTabIndex);
				
				// Only add to active tab if it's a BinTab and matches target bin
				if (activeTab instanceof BinTab) {
					BinTab binTab = (BinTab) activeTab;
					RequestBin activeBin = binTab.getBin();
					
					// Check if active bin is the target bin and entry UID matches
					if (activeBin.getUniqueId() != null && 
						activeBin.getUniqueId().equals(targetBin.getUniqueId()) &&
						interactEntry.uid != null && 
						interactEntry.uid.startsWith(activeBin.getUniqueId())) {
						
						binTab.addInteraction(interactEntry);
						
						// Update tab title to show new interaction
						updateTabTitle(activeBin, binTab);
					}
				}
			}
		}
	}
	
	/**
	 * Add to table method - adds to active tab only if UID matches
	 */
	public void addToTable(Object entry) {
		if (entry instanceof InteractshEntry) {
			InteractshEntry interactEntry = (InteractshEntry) entry;
			
			// Get the currently active tab
			int activeTabIndex = mainPane.getSelectedIndex();
			if (activeTabIndex >= 0 && activeTabIndex < mainPane.getTabCount()) {
				Component activeTab = mainPane.getComponentAt(activeTabIndex);
				
				// Only add to active tab if it's a BinTab and UID matches
				if (activeTab instanceof BinTab) {
					BinTab binTab = (BinTab) activeTab;
					RequestBin bin = binTab.getBin();
					
					// Check if entry UID matches active bin's unique ID
					if (bin.getUniqueId() != null && interactEntry.uid != null && 
						interactEntry.uid.startsWith(bin.getUniqueId())) {
						
						binTab.addInteraction(interactEntry);
						
						// Update tab title to show new interaction
						updateTabTitle(bin, binTab);
					}
				}
			}
		}
	}

	// Static methods for config compatibility - these are no longer used but kept for compilation
	public static void setServerText(String text) {
		// No-op - individual bins handle their own config now
	}

	public static void setPortText(String text) {
		// No-op
	}

	public static void setAuthText(String text) {
		// No-op
	}

	public static void setPollText(String text) {
		// No-op
	}

	public static void setTlsBox(boolean value) {
		// No-op
	}

	public static String getServerText() {
		return "";
	}

	public static String getPortText() {
		return "";
	}

	public static String getAuthText() {
		return "";
	}

	public static String getPollText() {
		return "5";
	}

	public static boolean getTlsBox() {
		return true;
	}
}