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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.types.CustomElement;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.workspace.WorkspacePanel;
import net.mcreator.workspace.elements.ModElement;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Changes the same value (for example the hardness) of all the selected mod elements at once.
 */
public final class BatchEditDialog {

	private static final int OK = 0, SKIPPED = 1, INVALID = 2;

	public static void show(MCreator mcreator) {
		String title = L10N.t("batch.title");
		if (!(mcreator.getWorkspacePanel() instanceof WorkspacePanel panel))
			return;

		List<ModElement> elements = chooseElements(mcreator, panel);
		if (elements.isEmpty()) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("batch.nothing_selected"), title,
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// values that exist in all the selected elements
		Map<ModElement, JsonObject> jsons = new LinkedHashMap<>();
		Set<String> common = null;
		for (ModElement element : elements) {
			JsonObject json = JsonParser.parseString(
					mcreator.getModElementManager().generatableElementToJSON(element.getGeneratableElement()))
					.getAsJsonObject();
			jsons.put(element, json);

			Set<String> properties = new TreeSet<>();
			collect(json, "", properties, 0);
			if (common == null)
				common = properties;
			else
				common.retainAll(properties);
		}
		if (common == null || common.isEmpty()) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("batch.no_common"), title, JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		JsonObject first = jsons.values().iterator().next();
		JComboBox<String> property = new JComboBox<>(common.toArray(new String[0]));
		JLabel current = new JLabel();
		JTextField value = new JTextField(22);
		property.addActionListener(_ -> current.setText(
				L10N.t("batch.current_value", String.valueOf(read(first, String.valueOf(property.getSelectedItem()))))));
		property.setSelectedIndex(0);
		current.setText(L10N.t("batch.current_value", String.valueOf(read(first, common.iterator().next()))));

		JPanel form = new JPanel(new GridLayout(0, 1, 0, 6));
		form.add(new JLabel(L10N.t("batch.message", elements.size())));
		form.add(property);
		form.add(current);
		form.add(new JLabel(L10N.t("batch.new_value")));
		form.add(value);

		int answer = JOptionPane.showConfirmDialog(mcreator, form, title, JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE);
		if (answer != JOptionPane.OK_OPTION)
			return;

		String path = String.valueOf(property.getSelectedItem());
		String text = value.getText().strip();
		int done = 0, skipped = 0, failed = 0;

		mcreator.setCursor(new Cursor(Cursor.WAIT_CURSOR));
		try {
			for (ModElement element : elements) {
				JsonObject json = JsonParser.parseString(
						mcreator.getModElementManager().generatableElementToJSON(element.getGeneratableElement()))
						.getAsJsonObject();

				int result = write(json, path, text);
				if (result == SKIPPED) {
					skipped++;
					continue;
				} else if (result == INVALID) {
					failed++;
					continue;
				}

				GeneratableElement updated = mcreator.getModElementManager()
						.fromJSONtoGeneratableElementOrNull(json.toString(), element);
				if (updated == null) {
					failed++;
					continue;
				}

				mcreator.getModElementManager().storeModElementPicture(updated);
				mcreator.getModElementManager().storeModElement(updated);
				mcreator.getGenerator().generateElement(updated);
				done++;
			}
			mcreator.getWorkspace().markDirty();
			mcreator.reloadWorkspaceTabContents();
		} finally {
			mcreator.setCursor(Cursor.getDefaultCursor());
		}

		JOptionPane.showMessageDialog(mcreator, L10N.t("batch.done", done, skipped, failed), title,
				failed > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
	}

	/** Lists the elements with check boxes, so no multiple selection in the workspace is needed. */
	private static List<ModElement> chooseElements(MCreator mcreator, WorkspacePanel panel) {
		Set<ModElement> preselected = new HashSet<>();
		for (var selected : panel.list.getSelectedValuesList())
			if (selected instanceof ModElement element)
				preselected.add(element);

		List<ModElement> candidates = new ArrayList<>();
		for (ModElement element : mcreator.getWorkspace().getModElements()) {
			if (element.getGeneratableElement() != null && !(element.getGeneratableElement() instanceof CustomElement)
					&& !element.isCodeLocked())
				candidates.add(element);
		}
		candidates.sort(Comparator.comparing(ModElement::getName, String.CASE_INSENSITIVE_ORDER));
		if (candidates.isEmpty())
			return List.of();

		String all = L10N.t("batch.filter_all");
		Set<String> types = new TreeSet<>();
		Map<ModElement, JCheckBox> boxes = new LinkedHashMap<>();
		JPanel listPanel = new JPanel();
		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		for (ModElement element : candidates) {
			types.add(element.getType().getReadableName());
			JCheckBox box = new JCheckBox(element.getName() + "   (" + element.getType().getReadableName() + ")",
					preselected.contains(element));
			boxes.put(element, box);
			listPanel.add(box);
		}

		List<String> filterItems = new ArrayList<>();
		filterItems.add(all);
		filterItems.addAll(types);
		JComboBox<String> filter = new JComboBox<>(filterItems.toArray(new String[0]));
		filter.addActionListener(_ -> {
			String selected = String.valueOf(filter.getSelectedItem());
			boxes.forEach((element, box) -> box.setVisible(
					all.equals(selected) || element.getType().getReadableName().equals(selected)));
			listPanel.revalidate();
			listPanel.repaint();
		});

		JButton checkAll = new JButton(L10N.t("batch.check_all"));
		checkAll.addActionListener(_ -> boxes.values().forEach(box -> {
			if (box.isVisible())
				box.setSelected(true);
		}));
		JButton uncheckAll = new JButton(L10N.t("batch.uncheck_all"));
		uncheckAll.addActionListener(_ -> boxes.values().forEach(box -> box.setSelected(false)));

		JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		tools.add(filter);
		tools.add(checkAll);
		tools.add(uncheckAll);

		JScrollPane scroll = new JScrollPane(listPanel);
		scroll.setPreferredSize(new Dimension(460, 300));

		JPanel form = new JPanel(new BorderLayout(0, 8));
		form.add("North", new JLabel(L10N.t("batch.choose_elements")));
		form.add("Center", scroll);
		form.add("South", tools);

		int answer = JOptionPane.showConfirmDialog(mcreator, form, L10N.t("batch.title"),
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (answer != JOptionPane.OK_OPTION)
			return List.of();

		List<ModElement> chosen = new ArrayList<>();
		boxes.forEach((element, box) -> {
			if (box.isSelected())
				chosen.add(element);
		});
		return chosen;
	}

	/** Collects the simple values (text, number, true/false), also inside sub-objects up to 2 levels deep. */
	private static void collect(JsonObject object, String prefix, Set<String> out, int depth) {
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (entry.getValue().isJsonPrimitive())
				out.add(prefix + entry.getKey());
			else if (entry.getValue().isJsonObject() && depth < 2)
				collect(entry.getValue().getAsJsonObject(), prefix + entry.getKey() + ".", out, depth + 1);
		}
	}

	private static JsonElement read(JsonObject root, String path) {
		JsonElement current = root;
		for (String part : path.split("\\.")) {
			if (current == null || !current.isJsonObject())
				return null;
			current = current.getAsJsonObject().get(part);
		}
		return current;
	}

	private static int write(JsonObject root, String path, String text) {
		String[] parts = path.split("\\.");
		JsonObject current = root;
		for (int i = 0; i < parts.length - 1; i++) {
			JsonElement next = current.get(parts[i]);
			if (next == null || !next.isJsonObject())
				return SKIPPED;
			current = next.getAsJsonObject();
		}

		String last = parts[parts.length - 1];
		JsonElement existing = current.get(last);
		if (existing == null || !existing.isJsonPrimitive())
			return SKIPPED;

		JsonPrimitive primitive = existing.getAsJsonPrimitive();
		if (primitive.isBoolean()) {
			if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false"))
				return INVALID;
			current.addProperty(last, Boolean.parseBoolean(text));
		} else if (primitive.isNumber()) {
			String number = text.replace(',', '.');
			try {
				String original = primitive.getAsString().toLowerCase(Locale.ROOT);
				if (!original.contains(".") && !original.contains("e") && number.matches("-?\\d+"))
					current.addProperty(last, Long.parseLong(number));
				else
					current.addProperty(last, Double.parseDouble(number));
			} catch (NumberFormatException e) {
				return INVALID;
			}
		} else {
			current.addProperty(last, text);
		}
		return OK;
	}

}
