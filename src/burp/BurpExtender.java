package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.extension.ExtensionUnloadingHandler;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.gui.Config;
import burp.gui.InteractshTab;
import burp.services.BinManager;
import burp.util.DebugLogger;
import interactsh.InteractshEntry;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;

public class BurpExtender
		implements BurpExtension, ContextMenuItemsProvider, ExtensionUnloadingHandler {
	public static MontoyaApi api;
	public static InteractshTab tab;
	public static BinManager binManager;

	@Override
	public void initialize(MontoyaApi api) {
		BurpExtender.api = api;

		api.extension().setName("RequestBin Collaborator");
		api.userInterface().registerContextMenuItemsProvider(this);
		api.extension().registerUnloadingHandler(this);
		
		// Log startup with debug status
		DebugLogger.info("[BurpExtender] Starting RequestBin Collaborator - " + DebugLogger.getDebugStatus());
		api.logging().logToOutput("[BurpExtender] Extension initialization started...");

		api.logging().logToOutput("[BurpExtender] Generating configuration...");
		burp.gui.Config.generateConfig();
		
		api.logging().logToOutput("[BurpExtender] Creating InteractshTab UI component...");
		BurpExtender.tab = new InteractshTab(api);
		
		// Initialize BinManager after UI components with InteractshTab integration
		api.logging().logToOutput("[BurpExtender] Initializing BinManager and core services...");
		BurpExtender.binManager = new BinManager(api, BurpExtender.tab);
		
		// Set BinManager on tab and load bins
		api.logging().logToOutput("[BurpExtender] Linking BinManager to UI components...");
		BurpExtender.tab.setBinManager(binManager);
		
		api.logging().logToOutput("[BurpExtender] Loading configuration...");
		burp.gui.Config.loadConfig();

		// Start polling for any existing bins
		api.logging().logToOutput("[BurpExtender] Starting polling for existing bins...");
		binManager.startPollingForAllBins();

		api.logging().logToOutput("[BurpExtender] Registering UI tab...");
		api.userInterface().registerSuiteTab("RequestBin", tab);
		
		api.logging().logToOutput("[BurpExtender] RequestBin Collaborator extension initialization completed successfully!");
	}

	@Override
	public void extensionUnloaded() {
		api.logging().logToOutput("[BurpExtender] Extension unload initiated...");
		
		if (BurpExtender.binManager != null) {
			api.logging().logToOutput("[BurpExtender] Shutting down BinManager and services...");
			BurpExtender.binManager.shutdown();
			api.logging().logToOutput("[BurpExtender] BinManager shutdown completed");
		}
		
		if (BurpExtender.tab != null) {
			api.logging().logToOutput("[BurpExtender] Cleaning up InteractshTab...");
			BurpExtender.tab.cleanup();
			api.logging().logToOutput("[BurpExtender] InteractshTab cleanup completed");
		}
		
		api.logging().logToOutput("[BurpExtender] Extension unload completed - Thanks for collaborating!");
	}

	public static int getPollTime() {
		try {
			return Integer.parseInt(BurpExtender.tab.getPollField().getText());
		} catch (Exception ex) {
		}
		return Integer.parseInt(Config.getPollInterval());
	}

	public static void addToTable(InteractshEntry i) {
		BurpExtender.tab.addToTable(i);
	}

	@Override
	public List<Component> provideMenuItems(ContextMenuEvent event) {
		List<Component> menuList = new ArrayList<Component>();
		JMenuItem item = new JMenuItem("Copy RequestBin URL");
		item.addActionListener(e -> BurpExtender.tab.getListener().copyCurrentUrlToClipboard());
		menuList.add(item);

		return menuList;
	}
}
