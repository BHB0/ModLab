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
import net.mcreator.ui.modgui.ProcedureGUI;
import net.mcreator.util.DesktopUtils;
import net.mcreator.workspace.elements.ModElement;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The assistant, docked on the right of the workspace window. It knows the workspace, the open editor and the blocks
 * of the current Minecraft version. It can advise which blocks to use, and add a proposed set of blocks to the open
 * procedure after checking that every block exists.
 */
public final class AssistantPanel extends JPanel {

	private final MCreator mcreator;
	private final List<AssistantClient.Message> history = new ArrayList<>();
	private final StringBuilder transcript = new StringBuilder();
	private final JEditorPane view = new JEditorPane("text/html", "");
	private final JTextArea input = new JTextArea(3, 20);
	private final JButton send = new JButton();
	private final JButton insert = new JButton();
	private boolean busy = false;
	private String pendingXml = null;

	public AssistantPanel(MCreator mcreator) {
		super(new BorderLayout());
		this.mcreator = mcreator;
		setMinimumSize(new Dimension(280, 200));

		view.setEditable(false);
		view.setBackground(Theme.current().getSecondAltBackgroundColor());

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

		insert.setText(L10N.t("assistant.insert"));
		insert.setVisible(false);
		insert.addActionListener(_ -> insertBlocks());

		JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
		top.add(newConversation);
		top.add(settings);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.add(send);

		JPanel south = new JPanel(new BorderLayout(0, 6));
		south.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		south.add("North", insert);
		south.add("Center", new JScrollPane(input));
		south.add("South", buttons);

		add("North", top);
		add("Center", new JScrollPane(view));
		add("South", south);

		reset();
	}

	/** Small icon of the dock button. */
	public static Icon dockIcon() {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Theme.current().getInterfaceAccentColor());
		g.fillOval(0, 0, 16, 16);
		g.setColor(Color.WHITE);
		Path2D star = new Path2D.Double();
		star.moveTo(8, 2.5);
		star.lineTo(9.6, 6.4);
		star.lineTo(13.5, 8);
		star.lineTo(9.6, 9.6);
		star.lineTo(8, 13.5);
		star.lineTo(6.4, 9.6);
		star.lineTo(2.5, 8);
		star.lineTo(6.4, 6.4);
		star.closePath();
		g.fill(star);
		g.dispose();
		return new ImageIcon(image);
	}

	public void focusInput() {
		input.requestFocusInWindow();
	}

	private void reset() {
		history.clear();
		transcript.setLength(0);
		pendingXml = null;
		insert.setVisible(false);
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

	public void submit(String text) {
		if (busy || text == null || text.isBlank())
			return;
		if (!AssistantClient.isConfigured()) {
			addMessage("error", L10N.t("assistant.not_configured"));
			return;
		}

		input.setText("");
		history.add(new AssistantClient.Message("user", text.strip()));
		addMessage("user", text.strip());
		insert.setVisible(false);
		pendingXml = null;

		busy = true;
		send.setEnabled(false);
		List<AssistantClient.Message> snapshot = new ArrayList<>(
				history.subList(Math.max(0, history.size() - 12), history.size()));
		String system = systemPrompt(text);

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
					checkProposedBlocks(result);
				}
			});
		}, "ModLabAssistant").start();
	}

	private ProcedureGUI currentProcedure() {
		var tab = mcreator.getTabs().getCurrentTab();
		if (tab != null && tab.getContent() instanceof ProcedureGUI gui)
			return gui;
		return null;
	}

	private String systemPrompt(String question) {
		StringBuilder sb = new StringBuilder();
		sb.append("You are the built-in assistant of ModLab, a modernized fork of MCreator, the visual tool used to make ")
				.append("Minecraft Java mods. Help the user make mods: explain MCreator concepts, suggest how to build ")
				.append("things step by step, and explain compilation errors in plain words. Answer in the language of ")
				.append("the user, be concise and practical. If you are not sure about a feature or block name, say so ")
				.append("instead of inventing it.\n\n")
				.append("When the user asks which blocks to use, recommend the exact blocks by name and category, in order. ")
				.append("When the user asks you to create or change a procedure, explain in 2 to 4 short lines, then give ")
				.append("the blocks as Blockly XML in ONE ```xml code block. Rules: use ONLY block types listed below (the ")
				.append("type attribute is the machine name), plus these standard blocks: controls_if, logic_compare, ")
				.append("logic_operation, logic_negate, logic_boolean, math_number, math_arithmetic, text. Do NOT include ")
				.append("the trigger block: start with the first action block and chain the next ones with <next>. Use ")
				.append("exactly the field and input names that appear in the examples. If a block you need is not listed, ")
				.append("say so instead of inventing it.\n\n");
		try {
			sb.append("Current workspace: ").append(mcreator.getWorkspaceSettings().getModName()).append("\n");
			sb.append("Generator: ").append(mcreator.getGeneratorConfiguration().getGeneratorName()).append("\n");

			ProcedureGUI procedure = currentProcedure();
			if (procedure != null)
				sb.append("The user is editing the procedure: ").append(procedure.getModElement().getName()).append("\n");

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
			sb.append("\n\n");
			sb.append(BlockCatalog.contextFor(mcreator, question));
		} catch (Exception ignored) {
		}
		return sb.toString();
	}

	private static final Pattern XML_BLOCK = Pattern.compile("```(?:xml)?\\s*(<xml.*?</xml>)\\s*```", Pattern.DOTALL);

	private void checkProposedBlocks(String answer) {
		Matcher matcher = XML_BLOCK.matcher(answer);
		if (!matcher.find())
			return;

		BlockCatalog.Validation validation = BlockCatalog.validate(mcreator, matcher.group(1));
		if (!validation.ok()) {
			addMessage("error", L10N.t("assistant.xml_invalid", String.join(", ", validation.unknown())));
			return;
		}

		pendingXml = validation.xml();
		insert.setVisible(true);
		revalidate();
	}

	private void insertBlocks() {
		ProcedureGUI procedure = currentProcedure();
		if (procedure == null || pendingXml == null) {
			JOptionPane.showMessageDialog(this, L10N.t("assistant.no_procedure"), L10N.t("assistant.title"),
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		procedure.getBlocklyPanel().addBlocksFromXML(pendingXml, true);
		addMessage("assistant", L10N.t("assistant.inserted"));
		insert.setVisible(false);
		pendingXml = null;
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

	/** Turns the text of the answer into simple HTML: code blocks, inline code, bold and line breaks. */
	private static String format(String text) {
		String[] blocks = text.split("```", -1);
		StringBuilder html = new StringBuilder();
		for (int i = 0; i < blocks.length; i++) {
			String block = blocks[i];
			if (i % 2 == 1) {
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
