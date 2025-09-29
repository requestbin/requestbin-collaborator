package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.extension.ExtensionUnloadingHandler;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.gui.Config;
import burp.gui.InteractshTab;
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

	@Override
	public void initialize(MontoyaApi api) {
		BurpExtender.api = api;

		api.extension().setName("RequestBin Collaborator");
		api.userInterface().registerContextMenuItemsProvider(this);
		api.extension().registerUnloadingHandler(this);
		
		// Log startup with debug status
		DebugLogger.info("Starting RequestBin Collaborator - " + DebugLogger.getDebugStatus());

		burp.gui.Config.generateConfig();
		BurpExtender.tab = new InteractshTab(api);
		burp.gui.Config.loadConfig();

		api.userInterface().registerSuiteTab("RequestBin", tab);
	}

	@Override
	public void extensionUnloaded() {
		BurpExtender.tab.cleanup();
		BurpExtender.api.logging().logToOutput("Thanks for collaborating!");
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
