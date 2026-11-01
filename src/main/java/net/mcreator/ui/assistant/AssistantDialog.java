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

package net.mcreator.ui.assistant;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.util.DesktopUtils;
import net.mcreator.workspace.elements.ModElement;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Chat window of the assistant. It knows the name, the generator and the elements of the current workspace.
 */
public final class AssistantDialog extends JDialog {

	private static AssistantDialog instance;

	private final MCreator mcreator;
	private final List<AssistantClient.Message> history = new ArrayList<>();
	private final StringBuilder transcript = new StringBuilder();
	private final JEditorPane view = new JEditorPane("text/html", "");
	private final JTextArea input = new JTextArea(3, 40);
	private final JButton send = new JButton();
	private boolean busy = false;

	public static void open(MCreator mcreator) {
		openWith(mcreator, null);
	}

	public static void openWith(MCreator mcreator, String message) {
		if (instance == null || !instance.isDisplayable() || instance.mcreator != mcreator)
			instance = new AssistantDialog(mcreator);
		instance.setVisible(true);
		instance.toFront();
		if (message != null && !message.isBlank())
			instance.submit(message);
	}

	private AssistantDialog(MCreator mcreator) {
		super(mcreator, L10N.t("assistant.title"), false);
		this.mcreator = mcreator;
		setSize(680, 580);
		setLocationRelativeTo(mcreator);

		view.setEditable(false);
		view.setBackground(Theme.current().getSecondAltBackgroundColor());
		JScrollPane scroll = new JScrollPane(view);

		input.setLineWrap(true);
		input.setWrapStyleWord(true);
		input.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ctrl ENTER"), "send");
		input.getActionMap().put("send", new AbstractAction() {
			@Override public void actionPerformed(ActionEvent e) {
				submit(input.getText());
			}
		});

		send.setText(L10N.t("assistant.send"));
		send.addActionListener(_ -> submit(input.getText()));

		JButton newConversation = new JButton(L10N.t("assistant.new_conversation"));
		newConversation.addActionListener(_ -> reset());

		JButton settings = new JButton(L10N.t("assistant.settings"));
		settings.addActionListener(_ -> mcreator.getActionRegistry().preferences.actionPerformed(
				new ActionEvent(mcreator, ActionEvent.ACTION_PERFORMED, "")));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.add(settings);
		buttons.add(newConversation);
		buttons.add(send);

		JPanel south = new JPanel(new BorderLayout(0, 6));
		south.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		south.add("Center", new JScrollPane(input));
		south.add("South", buttons);

		JPanel content = new JPanel(new BorderLayout());
		content.add("Center", scroll);
		content.add("South", south);
		setContentPane(content);

		reset();
	}

	private void reset() {
		history.clear();
		transcript.setLength(0);
		if (AssistantClient.isConfigured()) {
			addMessage("assistant", L10N.t("assistant.welcome"));
		} else {
			addMessage("assistant", L10N.t("assistant.not_configured"));
			SwingUtilities.invokeLater(() -> {
				int choice = JOptionPane.showConfirmDialog(this, L10N.t("assistant.open_studio"), L10N.t("assistant.title"),
						JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
				if (choice == JOptionPane.YES_OPTION)
					DesktopUtils.browseSafe("https://aistudio.google.com/apikey");
			});
		}
	}

	private void submit(String text) {
		if (busy || text == null || text.isBlank())
			return;
		if (!AssistantClient.isConfigured()) {
			addMessage("error", L10N.t("assistant.not_configured"));
			return;
		}

		input.setText("");
		history.add(new AssistantClient.Message("user", text.strip()));
		addMessage("user", text.strip());

		busy = true;
		send.setEnabled(false);
		List<AssistantClient.Message> snapshot = new ArrayList<>(
				history.subList(Math.max(0, history.size() - 12), history.size()));
		String system = systemPrompt();

		new Thread(() -> {
			String answer;
			boolean error = false;
			try {
				answer = AssistantClient.ask(system, snapshot);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				answer = "Interrupted";
				error = true;
			} catch (Exception e) {
				answer = String.valueOf(e.getMessage());
				error = true;
			}

			String result = answer;
			boolean failed = error;
			SwingUtilities.invokeLater(() -> {
				busy = false;
				send.setEnabled(true);
				if (failed) {
					addMessage("error", result);
				} else {
					history.add(new AssistantClient.Message("assistant", result));
					addMessage("assistant", result);
				}
			});
		}, "ModLabAssistant").start();
	}

	private String systemPrompt() {
		StringBuilder sb = new StringBuilder();
		sb.append("You are the built-in assistant of ModLab, a modernized fork of MCreator, the visual tool used to make ")
				.append("Minecraft Java mods. Help the user make mods: explain MCreator concepts (mod elements, procedures ")
				.append("with visual blocks and triggers, recipes, tags, datapack content), suggest how to build things ")
				.append("step by step, and explain compilation errors in plain words. Answer in the language of the ")
				.append("user, be concise and practical. If you are not sure about a feature or block name, say so ")
				.append("instead of inventing it.\n\n");
		try {
			sb.append("Current workspace: ").append(mcreator.getWorkspaceSettings().getModName()).append("\n");
			sb.append("Generator: ").append(mcreator.getGeneratorConfiguration().getGeneratorName()).append("\n");
			sb.append("Mod elements:");
			int count = 0;
			for (ModElement element : mcreator.getWorkspace().getModElements()) {
				if (count++ >= 60) {
					sb.append(" ...");
					break;
				}
				sb.append(" ").append(element.getName()).append(" (").append(element.getType().getReadableName())
						.append(");");
			}
			sb.append("\n");
		} catch (Exception ignored) {
		}
		return sb.toString();
	}

	private void addMessage(String role, String text) {
		Color accent = Theme.current().getInterfaceAccentColor();
		String fg = hex(Theme.current().getForegroundColor());
		String who = switch (role) {
			case "user" -> L10N.t("assistant.you");
			case "error" -> L10N.t("assistant.error");
			default -> L10N.t("assistant.name");
		};
		String color = "error".equals(role) ? "#e5484d" : "user".equals(role) ? "#9a9a9a" : hex(accent);
		transcript.append("<div style=\"margin:8px 10px;\"><b style=\"color:").append(color).append("\">").append(who)
				.append("</b><br><span style=\"color:").append(fg).append("\">").append(format(text))
				.append("</span></div>");
		view.setText("<html><body style=\"font-family:sans-serif;font-size:13px;\">" + transcript + "</body></html>");
		SwingUtilities.invokeLater(() -> view.setCaretPosition(view.getDocument().getLength()));
	}

	private static String hex(Color color) {
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
	private static final Pattern CODE = Pattern.compile("`([^`\\n]+)`");

	/**
	 * Turns the text of the answer into simple HTML: code blocks, inline code, bold and line breaks.
	 */
	private static String format(String text) {
		String[] blocks = text.split("```", -1);
		StringBuilder html = new StringBuilder();
		for (int i = 0; i < blocks.length; i++) {
			String block = blocks[i];
			if (i % 2 == 1) { // code block
				block = block.replaceFirst("^[A-Za-z0-9+#-]*\\n", "");
				html.append("<pre style=\"background:#00000033;padding:6px;\">").append(escape(block.strip()))
						.append("</pre>");
			} else {
				String escaped = escape(block);
				escaped = BOLD.matcher(escaped).replaceAll("<b>$1</b>");
				escaped = CODE.matcher(escaped).replaceAll("<code>$1</code>");
				html.append(escaped.replace("\n", "<br>"));
			}
		}
		return html.toString();
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

}
