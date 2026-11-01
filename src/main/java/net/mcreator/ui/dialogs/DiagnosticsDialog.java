/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2012-2020, Pylo
 * Copyright (C) 2020-2026, Pylo, opensource contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.ui.dialogs;

import net.mcreator.Launcher;
import net.mcreator.ModLabConfig;
import net.mcreator.io.UserFolderManager;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Shows what ModLab actually uses (settings read at startup and values in effect in the interface),
 * to help finding why a setting does not seem to apply. The text can be copied and sent.
 */
public class DiagnosticsDialog {

	public static void show(MCreator mcreator) {
		StringBuilder sb = new StringBuilder();
		sb.append("ModLab V").append(ModLabConfig.version()).append(" (based on MCreator ")
				.append(Launcher.version.getFullString()).append(")\n");
		sb.append("Java: ").append(System.getProperty("java.version")).append(" / ")
				.append(System.getProperty("java.vendor")).append("\n");
		sb.append("OS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version"))
				.append("\n");
		sb.append("Screen resolution: ").append(Toolkit.getDefaultToolkit().getScreenResolution()).append(" dpi\n");
		sb.append("Look and feel: ").append(UIManager.getLookAndFeel().getName()).append("\n");
		sb.append("Theme: ").append(Theme.current().getID()).append("\n\n");

		sb.append("Settings in memory:\n");
		sb.append("  corners = ").append(PreferencesManager.PREFERENCES.ui.interfaceCornerStyle.get()).append("\n");
		sb.append("  density = ").append(PreferencesManager.PREFERENCES.ui.interfaceDensity.get()).append("\n");
		sb.append("  toolbar = ").append(PreferencesManager.PREFERENCES.ui.toolbarStyle.get()).append("\n\n");

		sb.append("Values in effect in the interface:\n");
		for (String key : new String[] { "TabbedPane.tabHeight", "Button.arc", "Component.arc", "ScrollBar.thumbArc",
				"Popup.borderCornerRadius", "Table.rowHeight" })
			sb.append("  ").append(key).append(" = ").append(UIManager.getInt(key)).append("\n");
		for (String key : new String[] { "TabbedPane.tabInsets", "Button.margin", "MenuItem.margin",
				"TextField.margin" })
			sb.append("  ").append(key).append(" = ").append(insets(UIManager.getInsets(key))).append("\n");

		File preferences = UserFolderManager.getFileFromUserFolder("userpreferences");
		sb.append("\nFiles:\n  user folder = ").append(preferences.getParentFile().getAbsolutePath()).append("\n");
		try {
			if (preferences.isFile()) {
				sb.append("  preferences file lines about the interface:\n");
				for (String line : Files.readAllLines(preferences.toPath(), StandardCharsets.UTF_8)) {
					String lower = line.toLowerCase();
					if (lower.contains("density") || lower.contains("cornerstyle") || lower.contains("toolbarstyle"))
						sb.append("    ").append(line.trim()).append("\n");
				}
			} else {
				sb.append("  preferences file not found\n");
			}
		} catch (Exception e) {
			sb.append("  preferences file could not be read: ").append(e.getMessage()).append("\n");
		}

		String text = sb.toString();
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		area.setCaretPosition(0);
		JScrollPane scroll = new JScrollPane(area);
		scroll.setPreferredSize(new Dimension(620, 380));

		Object[] options = { L10N.t("diagnostics.copy"), UIManager.getString("OptionPane.okButtonText") };
		int choice = JOptionPane.showOptionDialog(mcreator, scroll, L10N.t("diagnostics.title"),
				JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
		if (choice == 0)
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}

	private static String insets(Insets insets) {
		return insets == null ? "null" : insets.top + "," + insets.left + "," + insets.bottom + "," + insets.right;
	}

}
